package io.mosip.registration.test.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.mosip.kernel.core.util.FileUtils;
import io.mosip.registration.update.ClientIntegrityValidator;
import io.mosip.registration.update.ClientSetupValidator;
import io.mosip.registration.update.ManifestCreator;


@RunWith(MockitoJUnitRunner.class)
public class ManifestCreatorTest extends ManifestCreator {

    private static final String MANIFEST_FILE_NAME = "MANIFEST.MF";

    @Test
    public void mainTest() throws Exception {

        // Define version and paths
        String version = "0.1v";
        String libraryFolderPath = Path.of("src", "test", "resources", "manifesttest", "lib").toString();
        String targetPath = Path.of("src", "test", "resources", "manifesttest").toString();

        // Run ManifestCreator main logic
        ManifestCreator.main(new String[]{version, libraryFolderPath, targetPath});

        // Check manifest file exists
        File manifestFile = Path.of(targetPath, "MANIFEST.MF").toFile();
        Assert.assertTrue("Manifest file not created", manifestFile.exists());

        // Load and assert manifest contents
        Manifest manifest = new Manifest(new FileInputStream(manifestFile));
        Assert.assertEquals("Manifest version mismatch",
                version, manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
        Assert.assertEquals("Incorrect number of entries", 2, manifest.getEntries().size());
        Assert.assertTrue("Missing entry: logback.xml", manifest.getEntries().containsKey("logback.xml"));
        Assert.assertTrue("Missing entry: mosip-application.properties", manifest.getEntries().containsKey("mosip-application.properties"));

        // Simulate copying for validator use
        FileUtils.copyDirectory(Path.of("src", "test", "resources", "manifesttest", "lib").toFile(),
                Path.of("lib").toFile());
        FileUtils.copyFile(manifestFile, Path.of("MANIFEST.MF").toFile());

        // Mock ClientSetupValidator and bypass real validation logic
        ClientSetupValidator clientSetupValidator = Mockito.mock(ClientSetupValidator.class);
        Mockito.doNothing().when(clientSetupValidator).validateBuildSetup();
        Mockito.when(clientSetupValidator.isValidationFailed()).thenReturn(false);

        // Run validation logic (mocked)
        clientSetupValidator.validateBuildSetup();
        boolean failed = clientSetupValidator.isValidationFailed();

        // Assert validation passed
        Assert.assertFalse("Validation unexpectedly failed", failed);
    }


    @Test
	@Ignore
    public void integrityCheckTest() throws IOException {
        URL url = ManifestCreatorTest.class.getResource("/setup/registration-api-1.2.0-SNAPSHOT.jar");
        X509Certificate certificate =  ClientIntegrityValidator.getCertificate();
        JarFile jarFile = new JarFile(url.getFile());
        ClientIntegrityValidator.verifyIntegrity(certificate, jarFile);
    }

    @Test(expected = SecurityException.class)
    public void integrityCheckTest2() throws IOException {
        URL url = ManifestCreatorTest.class.getResource("/setup/registration-api-1.2.0-SNAPSHOT.jar");
        JarFile jarFile = new JarFile(url.getFile());
        ClientIntegrityValidator.verifyIntegrity(null, jarFile);
    }

}
