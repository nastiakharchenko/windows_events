package org.windows_events.service.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class UserMonitorService {

    //public UserMonitorService() {}

    public static String getActiveUser() {
        try {
            Process process = Runtime.getRuntime().exec("query user");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // пропускаем заголовки
                if (line.startsWith("USERNAME") || line.isEmpty()) continue;

                // активная сессия помечается '>'
                if (line.startsWith(">") || line.contains("Active")) {
                    line = line.replace(">", "").trim();
                    String[] parts = line.split("\\s+");
                    return parts[0];
                }
            }
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return "UNKNOWN";
    }
}
