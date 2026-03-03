package com.benjch.www.photosorter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfigStore {

    private static final String KEY_KEEP_DIR = "keepDir";

    private final Path configFile;

    public AppConfigStore() {
        this(Path.of(System.getProperty("user.home"), ".myimagefilter", "config.properties"));
    }

    AppConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public synchronized String readKeepDir() {
        Properties properties = load();
        return properties.getProperty(KEY_KEEP_DIR, "");
    }

    public synchronized String updateKeepDir(String keepDir) {
        Properties properties = load();
        properties.setProperty(KEY_KEEP_DIR, keepDir == null ? "" : keepDir);
        save(properties);
        return properties.getProperty(KEY_KEEP_DIR, "");
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(configFile)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            properties.load(inputStream);
        } catch (IOException ignored) {
            // fallback empty config
        }
        return properties;
    }

    private void save(Properties properties) {
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(configFile)) {
                properties.store(outputStream, "myImageFilter configuration");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save config", e);
        }
    }
}
