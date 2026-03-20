package org.windows_events.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TimeShutdownFile {
    private static final String PATH_TO_FILE;

    static {
        String baseDir = System.getProperty("user.dir");
        File dataFolder = new File(baseDir, "data");
        if (!dataFolder.exists()) {
            boolean created = dataFolder.mkdirs();
        }

        PATH_TO_FILE = dataFolder.getAbsolutePath() + "\\time_shutdown.txt";
    }

    public TimeShutdownFile() {}

    public boolean isFile(){
        File file = new File(PATH_TO_FILE);
        return file.exists();
    }

    public void createFileIfNotExists() throws IOException {
        File file = new File(PATH_TO_FILE);
        file.createNewFile();
    }

    public long readDateFromFile() throws IOException {
        createFileIfNotExists();
        try {
            String content = new String(Files.readAllBytes(Paths.get(PATH_TO_FILE))).trim();
            if (content.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(content);
        } catch (NumberFormatException e) {
            throw new IOException(Class.class.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void writeDateToFile(long date) throws IOException {
        createFileIfNotExists();
        Path path = Paths.get(PATH_TO_FILE);
        try {
            Files.writeString(path, Long.toString(date));
        } catch (IOException e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
    }
}
