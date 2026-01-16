package org.shareandimprove.cjj;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configuration loader class.
 * Reads settings from a JSON file.
 */
public class Config {
    private static volatile Config instance;
    private static long lastLoadedTime = 0;

    private String jwtSecret;
    private Set<String> dateAttrNames;
    private Set<String> numAttrNames;
    private String selectColumns;
    private String aiHost;
    private String aiKey;
    private String aiModel;

    /**
     * Loads configuration from the specified JSON file path.
     * @param filePath Path to the config file.
     * @throws IOException If the file cannot be read.
     */
    public static void load(String filePath) throws IOException {
        File file = new File(filePath);
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            instance = new Gson().fromJson(reader, Config.class);
            lastLoadedTime = file.lastModified();
        }
    }

    /**
     * Starts a background thread to monitor the config file for changes.
     * @param filePath Path to the config file.
     */
    public static void startHotReload(String filePath) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Config-HotReload");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                File file = new File(filePath);
                if (file.exists() && file.lastModified() > lastLoadedTime) {
                    Log.println("Detected config change. Reloading...");
                    load(filePath);
                    Log.println("Config reloaded successfully.");
                }
            } catch (Exception e) {
                Log.println("Failed to reload config: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static String getJwtSecret() {
        if (instance == null || instance.jwtSecret == null) {
            throw new RuntimeException("Config not loaded or jwtSecret is missing");
        }
        return instance.jwtSecret;
    }

    public static Set<String> getDateAttrNames() {
        return instance != null && instance.dateAttrNames != null ? instance.dateAttrNames : Collections.emptySet();
    }

    public static Set<String> getNumAttrNames() {
        return instance != null && instance.numAttrNames != null ? instance.numAttrNames : Collections.emptySet();
    }

    public static String getSelectColumns() {
        return instance != null && instance.selectColumns != null ? instance.selectColumns : "*";
    }

    public static String getAiHost() {
        return instance != null ? instance.aiHost : null;
    }

    public static String getAiKey() {
        return instance != null ? instance.aiKey : null;
    }

    public static String getAiModel() {
        return instance != null ? instance.aiModel : null;
    }
}