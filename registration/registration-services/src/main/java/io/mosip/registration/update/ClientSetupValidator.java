package io.mosip.registration.update;

import io.mosip.registration.exception.RegBaseCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private static String downloadBioSDKURL = null;
    private static String mosipHostname = null;
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
    public static final String MOSIP_HOSTNAME_PLACEHOLDER = "${mosip.hostname}";

    public String prepareURLByHostName(String url) {
        String mosipHostNameVal = mosipHostname;
        return (url != null) ? url.replace(MOSIP_HOSTNAME_PLACEHOLDER, mosipHostNameVal)
                : url;
    }

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
            downloadBioSDKURL = properties.getProperty("mosip.download.bio.sdk.url");
            mosipHostname = properties.getProperty("mosip.hostname");
            String upgradeServerURL = properties.getProperty("mosip.client.upgrade.server.url");
            if (serverRegClientURL != null && serverRegClientURL.contains("%s")) {
                serverRegClientURL = String.format(serverRegClientURL, upgradeServerURL);
            }

            Objects.requireNonNull(serverRegClientURL, "'mosip.reg.client.url' IS NOT SET");
            Objects.requireNonNull(latestVersion, "'mosip.reg.version' IS NOT SET");

            if("LOCAL".equals(environment)) {
                messages.push("IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS ITS LOCAL ENVIRONMENT");
                return;
            }

            // If MANIFEST.MF doesn't exist locally, download it from server first
            File localManifestFile = new File(manifestFile);
            if (!localManifestFile.exists()) {
                logger.info("MANIFEST.MF not found locally, downloading from server...");
                String url = serverRegClientURL + latestVersion + SLASH + manifestFile;
                try (InputStream in = SoftwareUpdateUtil.download(url);
                     FileOutputStream out = new FileOutputStream(manifestFile)) {
                    in.transferTo(out);
                    logger.info("Successfully downloaded MANIFEST.MF from server");
                } catch (Exception e) {
                    logger.error("Failed to download MANIFEST.MF from server", e);
                    throw new RegBaseCheckedException("REG-BUILD-003", "Could not find or download MANIFEST.MF");
                }
            }

            setLocalManifest();
            setLocalSDKManifest();

            Objects.requireNonNull(localManifest, manifestFile + " - Not found");

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
        try {

            if("LOCAL".equals(environment)) {
                logger.warn("NOTE :: IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS ITS LOCAL ENVIRONMENT");
                return;
            }

            setServerManifest();

            //When machine is offline / not reachable to server, serverManifest might be null
            String serverVersion = serverManifest == null ? null : serverManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
            String localVersion = localManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);

            //only if the version is same then rewrite local manifest with server manifest.
            //if the version is different, then upgrade should handle it, and only checksum validation will be
            //done based on the local manifest file.
            if(localVersion.equals(serverVersion)) {
                serverManifest.write(new FileOutputStream(manifestFile));
                //reset the local manifest, as it's overwritten
                setLocalManifest();
            }

            latestVersion = localManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
            logger.info("Checksum validation started with manifest version : {}", latestVersion);

            SoftwareUpdateUtil.clearTempDirectory();

            if(SoftwareUpdateUtil.deleteUnknownJars(localManifest)) {
                logger.info("Found unknown jars in the classpath !");
                unknown_jars_found = true;
                validation_failed = true;
            }

            Map<String, Attributes> localAttributes = localManifest.getEntries();
            for (Map.Entry<String, Attributes> entry : localAttributes.entrySet()) {
                File file = new File(libFolder + File.separator + entry.getKey());
                String url = serverRegClientURL + latestVersion + SLASH + libFolder + SLASH + entry.getKey();
                if(!file.exists()) {
                    logger.info("{} file doesn't exists, downloading it", entry.getKey());
                    SoftwareUpdateUtil.download(url, entry.getKey());
                    logger.info("Successfully downloaded the file : {}", entry.getKey());
                    patch_downloaded = true;
                    continue;
                }

                if(!SoftwareUpdateUtil.validateJarChecksum(file, entry.getValue())) {
                    logger.info("{} file checksum validation failed, downloading it", entry.getKey());
                    SoftwareUpdateUtil.download(url, entry.getKey());
                    logger.info("Successfully downloaded the latest file : {}", entry.getKey());
                    patch_downloaded = true;
                }
            }
        } catch (Throwable e) {
            logger.error("Failed to validate build setup", e);
            validation_failed = true;
        }
        logger.info("Checksum validation completed validation_failed : {}, patch_downloaded : {}", validation_failed,
                patch_downloaded);
    }




    public void validateBioSDK() {
        if ("LOCAL".equals(environment)) {
            logger.warn("NOTE :: IGNORING LOCAL REGISTRATION CLIENT SETUP VALIDATION AS ITS LOCAL ENVIRONMENT");
            return;
        }

        File localSDKManifestFile = new File(localSDKManifestPath + SLASH + manifestFile);
        logger.info("Checking local SDK manifest at: {}", localSDKManifestFile.getAbsolutePath());

        // No local manifest at all → fresh install
        if (!localSDKManifestFile.exists()) {
            logger.info("No local SDK manifest found, downloading fresh SDK...");
            downloadLatestSDKZip();
            return;
        }

        // Local manifest exists → fetch server manifest and compare
        setServerSDKManifest();

        if (serverSDKManifest == null) {
            logger.warn("Server SDK manifest unreachable, skipping SDK version check");
            return;
        }

        String localSDKVersion = null;
        if (localSDKManifest != null) {
            localSDKVersion = localSDKManifest.getMainAttributes()
                    .getValue(Attributes.Name.MANIFEST_VERSION);
        }

        String serverSDKVersion = serverSDKManifest.getMainAttributes()
                .getValue(Attributes.Name.MANIFEST_VERSION);

        logger.info("Local SDK manifest version : [{}]", localSDKVersion);
        logger.info("Server SDK manifest version: [{}]", serverSDKVersion);

        if (serverSDKVersion == null) {
            logger.warn("Server SDK manifest has no version, skipping update");
            return;
        }

        if (!serverSDKVersion.equals(localSDKVersion)) {
            logger.info("SDK version mismatch detected! local=[{}] server=[{}]",
                    localSDKVersion, serverSDKVersion);
            logger.info("Backing up existing SDK and downloading latest from server...");
            downloadLatestSDKZip();
        } else {
            logger.info("BioSDK is up to date [{}], skipping download", localSDKVersion);
        }
    }

    private void downloadLatestSDKZip() {
        String url = prepareURLByHostName(downloadBioSDKURL);
        String zipFilePath = "Bio_SDK.zip";
        bioSDK_updated = false;

        try {
            logger.info("Downloading Bio SDK zip from: {}", url);
            SoftwareUpdateUtil.downloadZipfile(url, new File(zipFilePath));
            logger.info("Bio_SDK.zip downloaded successfully");

            // Backup existing SDK directory with timestamp before overwriting
            backupExistingDirectory(sdkZipExtractionPath);

            // Extract fresh SDK
            unzip(zipFilePath, sdkZipExtractionPath);
            logger.info("Bio SDK extracted to: {}", sdkZipExtractionPath);

            // Cleanup downloaded zip
            if (new File(zipFilePath).delete()) {
                logger.info("Cleaned up Bio_SDK.zip");
            }

            // Reload local SDK manifest from freshly extracted files
            setLocalSDKManifest();

            bioSDK_updated = true;
            logger.info("Bio-SDK successfully updated. New version: {}",
                    localSDKManifest != null ?
                            localSDKManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) : "unknown");

        } catch (Exception e) {
            logger.error("SDK update aborted due to error: {}", e.getMessage(), e);
            bioSDK_updated = false;
        }
    }

    private void backupExistingDirectory(String destDir) {
    	logger.info("Renaming Existing directory : {}", destDir);
    	File dir = new File(destDir);
        if (dir.exists()) {
            String timestamp = new SimpleDateFormat("ddMMyyyyHHmmss").format(new Date());
            Path source = dir.toPath();
            Path backup = Paths.get(destDir + "_backup_" + timestamp);
            
            try {
                Files.walk(source).forEach(path -> {
                    try {
                        Path targetPath = backup.resolve(source.relativize(path));
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(targetPath);
                        } else {
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                logger.info("Backup created at {}", backup);
            } catch (Exception e) {
                logger.error("Backup failed", e);
            }
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
                logger.info("Loaded local MANIFEST.MF from: {}", localManifestFile.getAbsolutePath());
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
        // mosip.bio.sdk.url = https://github.com/.../releases/download/1.2.0
        String url = serverSDKManifestUrl + SLASH + manifestFile;
        logger.info("Fetching server SDK manifest from: {}", url);
        try (InputStream in = SoftwareUpdateUtil.download(url)) {
            serverSDKManifest = new Manifest(in);
            logger.info("Server SDK manifest loaded successfully, version: [{}]",
                    serverSDKManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
        } catch (IOException | RegBaseCheckedException e) {
            logger.warn("Could not fetch server SDK manifest from {}: {}", url, e.getMessage());
            serverSDKManifest = null;
        }
    }
}
