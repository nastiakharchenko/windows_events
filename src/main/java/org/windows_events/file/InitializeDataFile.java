package org.windows_events.file;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class InitializeDataFile {

    private static final String PATH_TO_FILE;

    static {
        String baseDir = System.getProperty("user.dir");
        File dataFolder = new File(baseDir, "data");
        if (!dataFolder.exists()) {
            boolean created = dataFolder.mkdirs();
        }

        PATH_TO_FILE = dataFolder.getAbsolutePath() + "\\db_usb.txt";
    }

    public InitializeDataFile() {}

    public boolean isFile(){
        File file = new File(PATH_TO_FILE);
        return file.exists();
    }

    public boolean writeToFile(Set<String> dataUsbDevices){
        Path path = Paths.get(PATH_TO_FILE);
        try {
            String dataAsString = "[" + String.join("; ", dataUsbDevices) + "]";
            Files.writeString(path, dataAsString);
        } catch (IOException e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    public Set<String> readFromFile(){
        Path path = Paths.get(PATH_TO_FILE);
        try {
            String content = Files.readString(path).trim();

            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }

            return Arrays.stream(content.split(";\\s*"))
                    .map(String::trim)        // Убираем лишние пробелы
                    .filter(s -> !s.isEmpty()) // Игнорируем пустые элементы
                    .collect(Collectors.toSet());

        } catch (IOException e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
            return Collections.emptySet();
        }
    }
}
