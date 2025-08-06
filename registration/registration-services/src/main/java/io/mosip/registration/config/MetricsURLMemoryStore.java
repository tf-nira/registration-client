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
        LOGGER.info("set MetricsURLMemoryStore {}", s);
        try(FileOutputStream fos = new FileOutputStream(getFileName(s));
            ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(url);
        } catch (IOException e) {
            LOGGER.error("Failed to set resumable url in MetricsURLMemoryStore", e);
        }
    }

    @Override
    public URL get(String s) {
        File file = getFileName(s);
        if (!file.exists()) {
            LOGGER.warn("Resumable file not found: {}. Starting new upload.", file.getAbsolutePath());
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (URL) ois.readObject();
        } catch (Exception e) {
            LOGGER.error("Failed to read resumable URL from file: {}. Starting fresh upload.", file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    public void remove(String s) {
        if (getFileName(s).delete()) {
            LOGGER.info("Deleted metrics");
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
