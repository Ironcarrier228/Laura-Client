package im.laura.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigStorage {
    public final Logger logger = Logger.getLogger(ConfigStorage.class.getName());

    public final File CONFIG_DIR = new File(Minecraft.getInstance().gameDir, "\\laura\\configs");
    public final File AUTOCFG_DIR = new File(CONFIG_DIR, "auto.cfg");

    public final JsonParser jsonParser = new JsonParser();

    public void init() throws IOException {
        setupFolder();
    }

    public void setupFolder() {
        // 1. Создаём папку, если её нет
        if (!CONFIG_DIR.exists()) {
            if (CONFIG_DIR.mkdirs()) {
                logger.log(Level.INFO, "Config directory created");
            } else {
                logger.log(Level.SEVERE, "Failed to create config directory");
                return;
            }
        }

        // 2. Если конфиг существует — загружаем
        if (AUTOCFG_DIR.exists()) {
            loadConfiguration("system");
            logger.log(Level.INFO, "Loaded system configuration");
        }
        // 3. Иначе создаём конфиг
        else {
            logger.log(Level.INFO, "Creating system configuration...");
            try {
                if (AUTOCFG_DIR.createNewFile()) {
                    logger.log(Level.INFO, "System configuration created");
                } else {
                    logger.log(Level.SEVERE, "Failed to create system configuration file");
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error while creating system configuration file", e);
            }
        }
    }


    public boolean isEmpty() {
        return getConfigs().isEmpty();
    }

    public List<Config> getConfigs() {
        List<Config> configs = new ArrayList<>();
        File[] configFiles = CONFIG_DIR.listFiles();

        if (configFiles != null) {
            for (File configFile : configFiles) {
                if (configFile.isFile() && configFile.getName().endsWith(".cfg")) {
                    String configName = configFile.getName().replace(".cfg", "");
                    Config config = findConfig(configName);
                    if (config != null) {
                        configs.add(config);
                    }
                }
            }
        }

        return configs;
    }


    public void loadConfiguration(String configuration) {
        Config config = findConfig(configuration);
        if (config == null) return;

        try (FileReader reader = new FileReader(config.getFile())) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element == null || !element.isJsonObject()) {
                logger.warning("Config is empty or invalid: " + configuration);
                return;
            }

            JsonObject object = element.getAsJsonObject();
            config.loadConfig(object);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load config: " + configuration, e);
        }
    }


    public void saveConfiguration(String configuration) {
        Config config = new Config(configuration);
        String contentPrettyPrint = new GsonBuilder().setPrettyPrinting().create().toJson(config.saveConfig());
        try {
            FileWriter writer = new FileWriter(config.getFile());
            writer.write(contentPrettyPrint);
            writer.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "File not found!", e);
        } catch (NullPointerException e) {
            logger.log(Level.WARNING, "Fatal Error in Config!", e);
        }
    }

    public Config findConfig(String configName) {
        if (configName == null) return null;
        if (new File(CONFIG_DIR, configName + ".cfg").exists())
            return new Config(configName);
        return null;
    }
}
