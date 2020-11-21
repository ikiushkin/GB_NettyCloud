package cloudcommon.services.settings;

import cloudcommon.services.LogServiceCommon;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Settings {
    private Properties properties;
    private String settingsFilePath;

    public Settings(String filePath, AppOption[] options) {
        load(filePath, options);
    }

    public void load(String filePath, AppOption[] options) {
        if (properties != null || filePath == null || options == null) return;
        properties = new Properties();
        settingsFilePath = filePath;
        Path settingsFile = Paths.get(settingsFilePath);
        LogServiceCommon.APP.info("Loading configuration file - " + settingsFile.toAbsolutePath().toString());
        try {
            if (!Files.exists(settingsFile)) throw new FileNotFoundException();
            InputStream in = Files.newInputStream(settingsFile);
            properties.load(in);
            in.close();
            if (!checkConsistent(options)) save();
        } catch (FileNotFoundException e) {
            createDefaultProperties(options);
        } catch (IOException e) {
            String s = "Error during loading configuration file " + settingsFilePath;
            LogServiceCommon.APP.fatal(s);
            throw new RuntimeException(s);
        }
    }

    public String get(AppSettings settings) {
        if (properties == null) {
            String s = "Settings wasn't loaded.";
            LogServiceCommon.APP.error(s);
            throw new NullPointerException(s);
        }
        return properties.getProperty(settings.getOption().getName());
    }

    public int getInt(AppSettings settings) {
        AppOption option = settings.getOption();
        if (!option.isInteger()) {
            String s = option.toString() + " is not integer";
            LogServiceCommon.APP.fatal(s);
            throw new IllegalArgumentException(s);
        }
        return Integer.parseInt(get(settings));
    }

    private boolean checkConsistent(AppOption[] options) {
        boolean result = true;
        for (AppOption option : options) {
            String optionName = option.getName();
            String value = properties.getProperty(optionName);
            if (value == null || (option.isInteger() && !checkIntegerFormat(value))) {
                LogServiceCommon.APP.error("Option " + optionName + " is missed or broken. Default option restored");
                properties.put(optionName, option.getDefaultOption());
                result = false;
            }
        }
        return result;
    }

    private void createDefaultProperties(AppOption[] options) {
        properties.clear();
        for (AppOption option : options) {
            properties.put(option.getName(), option.getDefaultOption());
        }
        save();
    }

    public void set(AppOption option, String value) {
        if (option.isInteger() && !checkIntegerFormat(value)) {
            String s = option + " should be an integer";
            LogServiceCommon.APP.error(s);
            throw new IllegalArgumentException(s);
        }
        properties.put(option, value);
    }

    public void save() {
        try {
            Path settingsFile = Paths.get(settingsFilePath);
            OutputStream out = Files.newOutputStream(settingsFile);
            properties.store(out, null);
            out.close();
        } catch (IOException e) {
            String s = "Error during saving configuration file " + settingsFilePath;
            LogServiceCommon.APP.fatal(s);
            throw new RuntimeException(s);
        }
    }

    private boolean checkIntegerFormat(String value) {
        return value.matches("\\d+");
    }
}