package io.mosip.registration.config;

import io.mosip.kernel.core.logger.spi.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MetricsURLMemoryStore implements io.tus.java.client.TusURLStore {

    private static final Logger LOGGER = AppConfig.getLogger(MetricsURLMemoryStore.class);
    private Path path = Paths.get(System.getProperty("user.dir"));

    @Override
    public void set(String s, URL url) {
        File file = getFileName(s);
        LOGGER.info("Saving resumable URL for {} into file {}", s, file.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(file);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(url);
        } catch (IOException e) {
            LOGGER.error("Failed to set resumable url in MetricsURLMemoryStore", e);
        }
    }

    @Override
    public URL get(String s) {
        try(FileInputStream fis = new FileInputStream(getFileName(s));
            ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (URL) ois.readObject();
        } catch (Exception e) {
            LOGGER.error("Failed to get resumable URL from MetricsURLMemoryStore for {}", s, e);
            return null;
        }
    }

    @Override
    public void remove(String s) {
        File file = getFileName(s);
        if (file.delete()) {
            LOGGER.info("Deleted resumable upload file: {}", file.getAbsolutePath());
        } else {
            LOGGER.warn("Failed to delete resumable upload file: {}", file.getAbsolutePath());
        }
    }

    private File getFileName(String s) {
        Path keyPath = Paths.get(s);
        Path targetPath = Paths.get(System.getProperty("user.dir"), ".metrics");
        targetPath.toFile().mkdirs();
        return Paths.get(targetPath.toString(),
                String.format("%s.ser", keyPath.toFile().getName())).toFile();
    }
}
