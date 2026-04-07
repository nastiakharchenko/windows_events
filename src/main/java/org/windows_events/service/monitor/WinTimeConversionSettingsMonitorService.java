package org.windows_events.service.monitor;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

import static org.windows_events.constants.Constants.*;

public class WinTimeConversionSettingsMonitorService {

    private DurableSeqLogger durableLogger;
    private NTPTimeService ntpTimeService;

    public WinTimeConversionSettingsMonitorService(DurableSeqLogger durableLogger, String hostNtp) {
        this.durableLogger = durableLogger;
        ntpTimeService = new NTPTimeService(hostNtp);
    }

    /**
     * Проверка: включён ли переход на летнее время
     */
    public boolean isDaylightSavingEnabled() {
        String value = readRegistry(
                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation",
                "DynamicDaylightTimeDisabled"
        );

        // 0 = включено, 1 = отключено
        return "0x0".equalsIgnoreCase(value) || "0".equals(value);
    }

    /**
     * Проверка: включён ли авто-перевод времени
     */
    public boolean isAutoTimeAdjustmentEnabled() {
        String value = readRegistry(
                "HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters",
                "Type"
        );

        if (value == null) return false;

        value = value.trim().toUpperCase();

        return value.contains("NTP");
    }

    /**
     * Включить авто-перевод времени
     */
    public void enableAutoTimeAdjustment() throws Exception {
        if(executeCommand("reg add HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters " +
                "/v Type /t REG_SZ /d NTP /f")){
            Date dateNow = ntpTimeService.getNTPTime();
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                    + SET_AUTOUPDATE_TIME + DateFormatter.dateConvert(dateNow));
        }
    }

    /**
     * Включить летнее время
     */
    public void enableDaylightSaving() throws Exception {
        if(executeCommand("reg add HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation " +
                "/v DynamicDaylightTimeDisabled /t REG_DWORD /d 0 /f")){
            Date dateNow = ntpTimeService.getNTPTime();
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                    + SET_DAYLIGHT_SAVE_TIME + DateFormatter.dateConvert(dateNow));
        }
    }

    private String readRegistry(String path, String key) {
        try {
            Process process = Runtime.getRuntime().exec(
                    "reg query \"" + path + "\" /v " + key
            );

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(key)) {
                    String[] parts = line.trim().split("\\s+");
                    return parts[parts.length - 1];
                }
            }
        } catch (Exception e) {
            System.err.println("Registry read error: " + e.getMessage());
        }
        return null;
    }

    private boolean executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("Command error: " + e.getMessage());
        }
        return false;
    }
}
