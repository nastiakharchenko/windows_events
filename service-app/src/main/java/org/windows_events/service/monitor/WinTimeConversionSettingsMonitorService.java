package org.windows_events.service.monitor;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import lombok.Getter;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;

public class WinTimeConversionSettingsMonitorService {
    private static final String W32TIME = "w32time";
    private static final String SE_TIME_ZONE_NAME = "SeTimeZonePrivilege";
    @Getter
    public boolean statusRequest = false;

    public WinTimeConversionSettingsMonitorService() {
    }

    public interface Kernel32Ext extends Library {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        int GetDynamicTimeZoneInformation(DYNAMIC_TIME_ZONE_INFORMATION pTimeZoneInformation);

        boolean SetDynamicTimeZoneInformation(DYNAMIC_TIME_ZONE_INFORMATION pTimeZoneInformation);

        int GetLastError();

        HANDLE GetCurrentProcess();
    }

    @Structure.FieldOrder({
            "wYear", "wMonth", "wDayOfWeek", "wDay",
            "wHour", "wMinute", "wSecond", "wMilliseconds"
    })
    public static class SYSTEMTIME extends Structure {
        public short wYear;
        public short wMonth;
        public short wDayOfWeek;
        public short wDay;
        public short wHour;
        public short wMinute;
        public short wSecond;
        public short wMilliseconds;
    }

    @Structure.FieldOrder({
            "Bias",
            "StandardName",
            "StandardDate",
            "StandardBias",
            "DaylightName",
            "DaylightDate",
            "DaylightBias",
            "TimeZoneKeyName",
            "DynamicDaylightTimeDisabled"
    })
    public static class DYNAMIC_TIME_ZONE_INFORMATION extends Structure {
        public int Bias;
        public char[] StandardName = new char[32];
        public SYSTEMTIME StandardDate = new SYSTEMTIME();
        public int StandardBias;
        public char[] DaylightName = new char[32];
        public SYSTEMTIME DaylightDate = new SYSTEMTIME();
        public int DaylightBias;
        public char[] TimeZoneKeyName = new char[128];

        // WinAPI BOOLEAN = 1 byte
        public byte DynamicDaylightTimeDisabled;

        public boolean isDynamicDstDisabled() {
            return DynamicDaylightTimeDisabled != 0;
        }

        public void setDynamicDstDisabled(boolean disabled) {
            DynamicDaylightTimeDisabled = (byte) (disabled ? 1 : 0);
        }

        public String getTimeZoneKeyName() {
            return readNullTerminated(TimeZoneKeyName);
        }

        private static String readNullTerminated(char[] value) {
            int len = 0;
            while (len < value.length && value[len] != '\0') {
                len++;
            }
            return new String(value, 0, len);
        }
    }

    public boolean isDaylightSavingEnabled() {
        statusRequest = false;

        DYNAMIC_TIME_ZONE_INFORMATION info = getCurrentDynamicTimeZoneInformation();
        return !info.isDynamicDstDisabled();
    }


    public String getCurrentTimeZoneKeyName() {
        return getCurrentDynamicTimeZoneInformation().getTimeZoneKeyName();
    }

    public void enableDaylightSaving() throws Exception {
        String timeZoneKey = getCurrentTimeZoneKeyName();

        withTimeZonePrivilege(() -> {
            DYNAMIC_TIME_ZONE_INFORMATION info = getCurrentDynamicTimeZoneInformation();
            info.setDynamicDstDisabled(false);
            info.write();

            boolean ok = Kernel32Ext.INSTANCE.SetDynamicTimeZoneInformation(info);
            if (!ok) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }
        });

        // Пере-применяем ту же таймзону, чтобы Windows восстановил её правила полностью
        if (timeZoneKey != null && !timeZoneKey.isBlank()) {
            execOrThrow("tzutil", "/s", timeZoneKey);
        }

        forceTimeUpdate();
    }

    public boolean isAutoTimeAdjustmentEnabled() {
        statusRequest = false;

        if (!isServiceRunning(W32TIME)) return false;
        if (!isServiceAutoStart(W32TIME)) return false;

        String value = readRegistry(
                "HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters",
                "Type"
        );

        if (value == null) return false;

        value = value.trim().toUpperCase(Locale.ROOT);
        return value.contains("NTP") || value.contains("NT5DS");
    }

    /**
     * standalone = NTP
     * domain joined = NT5DS / domhier
     */
    public void enableAutoTimeAdjustment(boolean domainJoined, String manualPeerList) throws Exception {
        ensureW32TimeReady();

        if (domainJoined) {
            execOrThrow("w32tm", "/config", "/syncfromflags:domhier", "/update");
            execOrThrow("reg", "add",
                    "HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters",
                    "/v", "Type",
                    "/t", "REG_SZ",
                    "/d", "NT5DS",
                    "/f");
        } else {
            String peers = (manualPeerList == null || manualPeerList.isBlank())
                    ? "time.windows.com,0x9"
                    : manualPeerList;

            execOrThrow("w32tm", "/config",
                    "/manualpeerlist:" + peers,
                    "/syncfromflags:manual",
                    "/update");

            execOrThrow("reg", "add",
                    "HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters",
                    "/v", "Type",
                    "/t", "REG_SZ",
                    "/d", "NTP",
                    "/f");
        }

        restartService(W32TIME);
        forceTimeUpdate();
    }

    public boolean isServiceRunning(String serviceName) {
        try {
            CommandResult result = exec("sc", "query", serviceName);
            String out = (result.stdout + "\n" + result.stderr).toUpperCase(Locale.ROOT);
            return result.exitCode == 0 && out.contains("RUNNING");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isServiceAutoStart(String serviceName) {
        try {
            CommandResult result = exec("sc", "qc", serviceName);
            String out = (result.stdout + "\n" + result.stderr).toUpperCase(Locale.ROOT);
            return result.exitCode == 0 && out.contains("AUTO_START");
        } catch (Exception e) {
            return false;
        }
    }

    public void setServiceAutoStart(String serviceName) throws Exception {
        if (!isServiceAutoStart(serviceName)) {
            execOrThrow("sc", "config", serviceName, "start=", "auto");
        }
    }

    public void startServiceIfNeeded(String serviceName) throws Exception {
        if (!isServiceRunning(serviceName)) {
            execOrThrow("sc", "start", serviceName);
            waitForServiceState(serviceName, "RUNNING", Duration.ofSeconds(20));
        }
    }

    public void stopServiceIfRunning(String serviceName) throws Exception {
        if (isServiceRunning(serviceName)) {
            execOrThrow("sc", "stop", serviceName);
            waitForServiceState(serviceName, "STOPPED", Duration.ofSeconds(20));
        }
    }

    public void restartService(String serviceName) throws Exception {
        stopServiceIfRunning(serviceName);
        startServiceIfNeeded(serviceName);
    }

    private DYNAMIC_TIME_ZONE_INFORMATION getCurrentDynamicTimeZoneInformation() {
        DYNAMIC_TIME_ZONE_INFORMATION info = new DYNAMIC_TIME_ZONE_INFORMATION();
        int result = Kernel32Ext.INSTANCE.GetDynamicTimeZoneInformation(info);

        if (result == 0xFFFFFFFF) {
            throw new Win32Exception(Kernel32Ext.INSTANCE.GetLastError());
        }

        info.read();
        return info;
    }

    private void forceTimeUpdate() throws Exception {
        ensureW32TimeReady();

        try {
            execOrThrow("w32tm", "/resync");
        } catch (Exception first) {
            execOrThrow("w32tm", "/resync", "/rediscover");
        }
    }

    private void ensureW32TimeReady() throws Exception {
        ensureServiceInstalled();
        setServiceAutoStart(W32TIME);
        startServiceIfNeeded(W32TIME);
    }

    private void ensureServiceInstalled() throws Exception {
        CommandResult result = exec("sc", "query", W32TIME);
        if (result.exitCode != 0) {
            throw new IllegalStateException("Service not found: " + W32TIME + "\n" + result.stderr);
        }
    }

    private String readRegistry(String path, String key) {
        try {
            CommandResult result = exec("reg", "query", path, "/v", key);
            if (result.exitCode != 0) {
                return null;
            }

            String[] lines = result.stdout.split("\\R");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(key)) continue;

                String[] parts = trimmed.split("\\s{2,}");
                if (parts.length >= 3) {
                    return parts[2].trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void waitForServiceState(String serviceName, String expectedState, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String expected = expectedState.toUpperCase(Locale.ROOT);

        while (System.currentTimeMillis() < deadline) {
            CommandResult result = exec("sc", "query", serviceName);
            String out = (result.stdout + "\n" + result.stderr).toUpperCase(Locale.ROOT);
            if (result.exitCode == 0 && out.contains(expected)) {
                return;
            }
            Thread.sleep(700);
        }

        throw new IllegalStateException("Timeout waiting for service " + serviceName + " -> " + expectedState);
    }

    private void execOrThrow(String... command) throws Exception {
        CommandResult result = exec(command);
        if (result.exitCode != 0) {
            throw new IllegalStateException(
                    "Command failed: " + String.join(" ", command)
                            + "\nSTDOUT:\n" + result.stdout
                            + "\nSTDERR:\n" + result.stderr
            );
        }
    }

    private CommandResult exec(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout;
        String stderr;

        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
             BufferedReader err = new BufferedReader(
                     new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()))) {

            stdout = readAll(out);
            stderr = readAll(err);
        }

        int exitCode = process.waitFor();

        statusRequest = exitCode == 0;

        return new CommandResult(exitCode, stdout, stderr);
    }

    private String readAll(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void withTimeZonePrivilege(ThrowingRunnable action) {
        HANDLEByReference tokenRef = new HANDLEByReference();

        boolean opened = Advapi32.INSTANCE.OpenProcessToken(
                Kernel32Ext.INSTANCE.GetCurrentProcess(),
                WinNT.TOKEN_ADJUST_PRIVILEGES | WinNT.TOKEN_QUERY,
                tokenRef
        );

        if (!opened) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }

        HANDLE token = tokenRef.getValue();

        try {
            WinNT.LUID luid = new WinNT.LUID();
            boolean lookupOk = Advapi32.INSTANCE.LookupPrivilegeValue(null, SE_TIME_ZONE_NAME, luid);
            if (!lookupOk) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }

            WinNT.TOKEN_PRIVILEGES enablePrivileges = new WinNT.TOKEN_PRIVILEGES(1);
            enablePrivileges.Privileges[0] =
                    new WinNT.LUID_AND_ATTRIBUTES(luid, new DWORD(WinNT.SE_PRIVILEGE_ENABLED));
            enablePrivileges.write();

            boolean adjusted = Advapi32.INSTANCE.AdjustTokenPrivileges(
                    token, false, enablePrivileges, 0, null, null
            );
            if (!adjusted) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }

            int privilegeErr = Kernel32.INSTANCE.GetLastError();
            if (privilegeErr != 0) {
                throw new Win32Exception(privilegeErr);
            }

            action.run();

            WinNT.TOKEN_PRIVILEGES disablePrivileges = new WinNT.TOKEN_PRIVILEGES(1);
            disablePrivileges.Privileges[0] = new WinNT.LUID_AND_ATTRIBUTES(luid, new DWORD(0));
            disablePrivileges.write();

            Advapi32.INSTANCE.AdjustTokenPrivileges(
                    token, false, disablePrivileges, 0, null, null
            );

        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        } finally {
            Kernel32.INSTANCE.CloseHandle(token);
        }
    }

//    /**
//     * Проверка: включён ли переход на летнее время
//     */
//    public boolean isDaylightSavingEnabled() {
//        String value = readRegistry(
//                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation",
//                "DynamicDaylightTimeDisabled"
//        );
//
//        // 0 = включено, 1 = отключено
//        return "0x0".equalsIgnoreCase(value) || "0".equals(value);
//    }
//
//    /**
//     * Проверка: включён ли авто-перевод времени
//     */
//    public boolean isAutoTimeAdjustmentEnabled() {
//        String value = readRegistry(
//                "HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters",
//                "Type"
//        );
//
//        if (value == null) return false;
//
//        value = value.trim().toUpperCase();
//
//        return value.contains("NTP");
//    }
//
//    /**
//     * Включить авто-перевод времени
//     */
//    public void enableAutoTimeAdjustment() throws Exception {
//        if(executeCommand("reg add HKLM\\SYSTEM\\CurrentControlSet\\Services\\W32Time\\Parameters " +
//                "/v Type /t REG_SZ /d NTP /f")){
//            Date dateNow = ntpTimeService.getNTPTime();
//            durableLogger.log(IDENTIFIER_PROGRAM +
//                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                    + SET_AUTOUPDATE_TIME + DateFormatter.dateConvert(dateNow));
//        }
//    }
//
//    /**
//     * Включить летнее время
//     */
//    public void enableDaylightSaving() throws Exception {
//        if(executeCommand("reg add HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation " +
//                "/v DynamicDaylightTimeDisabled /t REG_DWORD /d 0 /f")){
//            Date dateNow = ntpTimeService.getNTPTime();
//            durableLogger.log(IDENTIFIER_PROGRAM +
//                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                    + SET_DAYLIGHT_SAVE_TIME + DateFormatter.dateConvert(dateNow));
//        }
//    }
//
//    private String readRegistry(String path, String key) {
//        try {
//            Process process = Runtime.getRuntime().exec(
//                    "reg query \"" + path + "\" /v " + key
//            );
//
//            BufferedReader reader = new BufferedReader(
//                    new InputStreamReader(process.getInputStream())
//            );
//
//            String line;
//            while ((line = reader.readLine()) != null) {
//                if (line.contains(key)) {
//                    String[] parts = line.trim().split("\\s+");
//                    return parts[parts.length - 1];
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Registry read error: " + e.getMessage());
//        }
//        return null;
//    }
//
//    private boolean executeCommand(String command) {
//        try {
//            Process process = Runtime.getRuntime().exec(command);
//            int exitCode = process.waitFor();
//            if (exitCode == 0) {
//                return true;
//            }
//        } catch (Exception e) {
//            System.err.println("Command error: " + e.getMessage());
//        }
//        return false;
//    }
}
