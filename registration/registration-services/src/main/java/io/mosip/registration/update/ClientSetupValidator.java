package io.mosip.registration.update;

import io.mosip.registration.exception.RegBaseCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ClientSetupValidator {

    private static final Logger logger = LoggerFactory.getLogger(ClientSetupValidator.class);

    private static final String PROPERTIES_FILE = "props/mosip-application.properties";
    private static final String manifestFile = "MANIFEST.MF";
    private static final String libFolder = "lib";
    private static final String SLASH = "/";

    private static String serverRegClientURL = null;
    private static String serverSDKManifestUrl = null;
    private static String serverSDKZipUrl = null;
    private static String sdkZipExtractionPath = null;
    private static String localSDKManifestPath = null;
    private static String latestVersion = null;
    private static Manifest localManifest = null;
    private static Manifest serverManifest = null;
    private static Manifest localSDKManifest = null;
    private static Manifest serverSDKManifest = null;

    private static String environment = null;
    private static boolean validation_failed = false;

    private static boolean patch_downloaded = false;
    private static boolean unknown_jars_found = false;
    private static boolean bioSDK_updated = false;
    private static Stack<String> messages = new Stack<>();


    public ClientSetupValidator() throws RegBaseCheckedException {
        try (InputStream keyStream = ClientSetupValidator.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            Properties properties = new Properties();
            properties.load(keyStream);
            logger.info("Loading {} completed", PROPERTIES_FILE);

            serverRegClientURL = properties.getProperty("mosip.reg.client.url");
            serverSDKManifestUrl = properties.getProperty("mosip.bio.sdk.url");
            serverSDKZipUrl = properties.getProperty("mosip.bio.sdk.zip.url");
            sdkZipExtractionPath = properties.getProperty("mosip.bio.sdk.zip.extraction.path");
            localSDKManifestPath = properties.getProperty("mosip.bio.sdk.manifest.path");
            latestVersion = properties.getProperty("mosip.reg.version");
            environment = properties.getProperty("environment");
            setLocalManifest();
            setLocalSDKManifest();

            Objects.requireNonNull(serverRegClientURL, "'mosip.reg.client.url' IS NOT SET");
            Objects.requireNonNull(latestVersion, "'mosip.reg.version' IS NOT SET");

            if("LOCAL".equals(environment)) {
                messages.push("IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS ITS LOCAL ENVIRONMENT");
                return;
            }

            Objects.requireNonNull(localManifest, manifestFile + " - Not found");
            //SoftwareUpdateUtil.deleteUnknownJars(localManifest);

        } catch (RegBaseCheckedException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to load {}", PROPERTIES_FILE, e);
            throw new RegBaseCheckedException("REG-BUILD-001", "Failed to load properties");
        } catch (Throwable t) {
            throw new RegBaseCheckedException("REG-BUILD-002", t.getMessage());
        }
    }


    public void validateBuildSetup() throws RegBaseCheckedException {
        final int MAX_RETRIES = 2;

        try {
            if ("LOCAL".equals(environment)) {
                logger.warn("NOTE :: IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS IT'S A LOCAL ENVIRONMENT");
                return;
            }

            // Load local manifest first (assuming setLocalManifest() is called somewhere before or here)
            setLocalManifest();  // Ensure localManifest is loaded before pre-check

            // --------- STEP 1: Pre-check all jars against local manifest ----------------
            Map<String, Attributes> localAttributes = localManifest.getEntries();
            for (Map.Entry<String, Attributes> entry : localAttributes.entrySet()) {
                String jarName = entry.getKey();
                File jarFile = new File(libFolder + File.separator + jarName);

                boolean needsDownload = false;
                if (!jarFile.exists()) {
                    logger.info("{} does not exist during pre-check, will download", jarName);
                    needsDownload = true;
                } else {
                    // Validate checksum
                    boolean checksumOk = SoftwareUpdateUtil.validateJarChecksum(jarFile, entry.getValue());
                    // Validate it's a proper JAR
                    boolean isJarValid = false;
                    try (JarFile jar = new JarFile(jarFile)) {
                        jar.entries();
                        isJarValid = true;
                    } catch (IOException ex) {
                        logger.error("Corrupted JAR detected during pre-check: {}", jarName, ex);
                    }
                    if (!checksumOk || !isJarValid) {
                        logger.info("{} is corrupted or checksum invalid during pre-check, will download", jarName);
                        needsDownload = true;
                    }
                }

                if (needsDownload) {
                    boolean success = false;
                    String jarUrl = serverRegClientURL + latestVersion + "/" + libFolder + "/" + jarName;

                    for (int attempt = 1; attempt <= MAX_RETRIES && !success; attempt++) {
                       // logger.info("Pre-check download attempt {}/{} for {}", attempt, MAX_RETRIES, jarName);
                        SoftwareUpdateUtil.download(jarUrl, jarName);
                        File downloadedJar = new File(libFolder + File.separator + jarName);

                        boolean checksumOk = SoftwareUpdateUtil.validateJarChecksum(downloadedJar, entry.getValue());
                        boolean isJarValid = false;
                        try (JarFile jar = new JarFile(downloadedJar)) {
                            jar.entries();
                            isJarValid = true;
                        } catch (IOException ex) {
                           // logger.error("Downloaded JAR {} is invalid during pre-check on attempt {}", jarName, attempt, ex);
                        }

                        if (checksumOk && isJarValid) {
                            success = true;
                            patch_downloaded = true;
                            logger.info("Successfully downloaded and validated {} during pre-check", jarName);
                        } else {
                            downloadedJar.delete();
                            logger.warn("Downloaded file {} invalid during pre-check, retrying...", jarName);
                        }
                    }

                    if (!success) {
                        logger.error("Failed to download valid JAR {} during pre-check after {} attempts", jarName, MAX_RETRIES);
                        validation_failed = true;
                    }
                }
            }

            // --------- STEP 2: Proceed with server manifest setup and version checks ----------------
            setServerManifest();

            String serverVersion = serverManifest == null ? null :
                    serverManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
            String localVersion = localManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);

            if (localVersion.equals(serverVersion)) {
                serverManifest.write(new FileOutputStream(manifestFile));
                setLocalManifest();
            }

            latestVersion = localManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
            logger.info("Checksum validation started with manifest version: {}", latestVersion);

            SoftwareUpdateUtil.clearTempDirectory();

            if (SoftwareUpdateUtil.deleteUnknownJars(localManifest)) {
                logger.info("Found unknown jars in the classpath!");
                unknown_jars_found = true;
                validation_failed = true;
            }

            // Repeat the same download logic here for new patches if manifest version is different
            // (As in previous code snippet)

            // ... (You can reuse the previous loop here or call a helper method)

        } catch (Throwable e) {
            logger.error("Failed to validate build setup", e);
            validation_failed = true;
        }

        logger.info("Checksum validation completed. validation_failed: {}, patch_downloaded: {}", validation_failed, patch_downloaded);
    }




    public void validateBioSDK() {
    	if("LOCAL".equals(environment)) {
            logger.warn("NOTE :: IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS ITS LOCAL ENVIRONMENT");
            return;
        }
    	
    	setServerSDKManifest();
    	
    	String serverVersion = serverSDKManifest == null ? null : serverSDKManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
        String localVersion = localSDKManifest == null ? null : localSDKManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
        
        if(serverVersion != null && localVersion != null && !localVersion.equals(serverVersion)) {
        	bioSDK_updated = true;
        	downloadLatestSDKZip();
        }
    }
    
    private void downloadLatestSDKZip() {
    	String url = serverSDKZipUrl;
    	String zipFilePath = "Bio_SDK.zip";

        try (InputStream in = SoftwareUpdateUtil.download(url);
             FileOutputStream out = new FileOutputStream(zipFilePath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            renameExistingDirectory(sdkZipExtractionPath);
            unzip(zipFilePath, sdkZipExtractionPath);
        } catch (IOException | RegBaseCheckedException e) {
            logger.error("Failed to download or extract the zip file", e);
        }
    }
    
    private void renameExistingDirectory(String destDir) {
    	File dir = new File(destDir);
        if (dir.exists()) {
            String timestamp = new SimpleDateFormat("ddMMyyyyHHmmss").format(new Date());
            File newDir = new File(destDir + "_" + timestamp);
            dir.renameTo(newDir);
        }
    }
    
    private void unzip(String zipFilePath, String destDir) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) dir.mkdirs();
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            String rootDir = null;
            while (entry != null) {
            	String entryName = entry.getName();
                if (rootDir == null) {
                    rootDir = entryName.split("/")[0] + "/";
                }
                String filePath = destDir + File.separator + entryName.replaceFirst(rootDir, "");
                if (!entry.isDirectory()) {
                    extractFile(zipIn, filePath);
                } else {
                    File dirEntry = new File(filePath);
                    dirEntry.mkdirs();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }
    
//    private void downloadLatestSDKInstaller() {
//    	String url = "https://raw.githubusercontent.com/Manishch22/VIdExpireBatchJob/main/Installer.msi";
//        try(InputStream in = SoftwareUpdateUtil.download(url);
//                FileOutputStream out = new FileOutputStream("Installer.msi")) {
//        	byte[] buffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = in.read(buffer)) != -1) {
//                out.write(buffer, 0, bytesRead);
//            }
//            
//            ProcessBuilder processBuilder = new ProcessBuilder("msiexec", "/i", "Installer.msi");
//            //ProcessBuilder processBuilder = new ProcessBuilder("msiexec", "/i", filePath, "TARGETDIR=" + installDir, "/qn");
//            processBuilder.start();
//        } catch (IOException | RegBaseCheckedException e) {
//            logger.error("Failed to load server manifest file", e);
//        }
//    }

    public boolean isValidationFailed() {
        return validation_failed;
    }

    public boolean isPatch_downloaded() {
        return patch_downloaded;
    }

    public boolean isUnknown_jars_found() {
        return unknown_jars_found;
    }
    
    public boolean isBioSDK_updated() {
        return bioSDK_updated;
    }

    private void setLocalManifest() throws RegBaseCheckedException {
        try {
            File localManifestFile = new File(manifestFile);
            if (localManifestFile.exists()) {
                localManifest = new Manifest(new FileInputStream(localManifestFile));
            }
        } catch (IOException e) {
            logger.error("Failed to load local manifest file", e);
            throw new RegBaseCheckedException("REG-BUILD-003", "Local Manifest not found");
        }
    }
    
    private void setLocalSDKManifest() throws RegBaseCheckedException {
		try {
			File localManifestFile = new File(localSDKManifestPath + SLASH + manifestFile);
			if (localManifestFile.exists()) {
				localSDKManifest = new Manifest(new FileInputStream(localManifestFile));
			}
		} catch (IOException e) {
			logger.error("Failed to load local SDK manifest file", e);
			throw new RegBaseCheckedException("REG-BUILD-003", "Local SDK Manifest not found");
		}
	}

    private void setServerManifest() {
        String url = serverRegClientURL + latestVersion + SLASH + manifestFile;
        try(InputStream in = SoftwareUpdateUtil.download(url)) {
            serverManifest = new Manifest(in);
        } catch (IOException | RegBaseCheckedException e) {
            logger.error("Failed to load server manifest file", e);
        }
    }
    
    private void setServerSDKManifest() {
		String url = serverSDKManifestUrl + SLASH + manifestFile;
		try(InputStream in = SoftwareUpdateUtil.download(url)) {
			serverSDKManifest = new Manifest(in);
		} catch (IOException | RegBaseCheckedException e) {
			logger.error("Failed to load server manifest file for SDK", e);
		}
	}
}
