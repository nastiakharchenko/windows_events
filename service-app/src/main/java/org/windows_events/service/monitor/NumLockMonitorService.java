package org.windows_events.service.monitor;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

public class NumLockMonitorService {

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_VALUE_NAME = "NumLockGuardianAgent";

    private static final int VK_NUMLOCK = 0x90;
    private static final int KEYEVENTF_EXTENDEDKEY = 0x0001;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        short GetKeyState(int nVirtKey);

        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java -jar numlock-guardian.jar [service|agent]");
            System.exit(1);
        }

        switch (args[0].toLowerCase()) {
            case "service" -> runServiceLoop();
            case "agent" -> runAgentLoop();
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                System.exit(2);
            }
        }
    }

    /**
     * Режим службы:
     * 1. Проверяет, что агент зарегистрирован в HKLM Run.
     * 2. Периодически убеждается, что запись не удалили.
     *
     * Служба должна работать от LocalSystem или администратора,
     * иначе в HKLM запись может не создаться.
     */
    private static void runServiceLoop() throws Exception {
        log("Service mode started");

        while (true) {
            try {
                ensureAgentAutorunRegistered();
            } catch (Throwable t) {
                log("Failed to ensure autorun: " + t.getMessage());
                t.printStackTrace(System.err);
            }

            Thread.sleep(30_000);
        }
    }

    /**
     * Режим агента:
     * работает в интерактивной пользовательской сессии,
     * проверяет состояние NumLock и включает его, если выключен.
     */
    private static void runAgentLoop() throws Exception {
        log("Agent mode started");

        while (true) {
            try {
                ensureNumLockOn();
            } catch (Throwable t) {
                log("Failed to control NumLock: " + t.getMessage());
                t.printStackTrace(System.err);
            }

            Thread.sleep(1000);
        }
    }

    private static void ensureAgentAutorunRegistered() throws URISyntaxException {
        Path self = getCurrentJarOrClassesPath().toAbsolutePath();
        String javaw = new File(System.getProperty("java.home"), "bin\\javaw.exe").getAbsolutePath();

        String cmd = "\"" + javaw + "\" -jar \"" + self + "\" agent";

        String current = null;
        if (Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, RUN_KEY, RUN_VALUE_NAME)) {
            current = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, RUN_KEY, RUN_VALUE_NAME);
        }

        if (!Objects.equals(current, cmd)) {
            Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_LOCAL_MACHINE,
                    RUN_KEY,
                    RUN_VALUE_NAME,
                    cmd
            );
            log("HKLM Run updated: " + cmd);
        }
    }

    private static Path getCurrentJarOrClassesPath() throws URISyntaxException {
        return Path.of(
                NumLockMonitorService.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        );
    }

    private static void ensureNumLockOn() throws InterruptedException {
        if (!isNumLockOn()) {
            toggleNumLock();
            Thread.sleep(100);

            if (!isNumLockOn()) {
                log("NumLock is still OFF after toggle attempt");
            } else {
                log("NumLock turned ON");
            }
        }
    }

    private static boolean isNumLockOn() {
        return (User32Ext.INSTANCE.GetKeyState(VK_NUMLOCK) & 0x0001) != 0;
    }

    private static void toggleNumLock() {
        User32Ext.INSTANCE.keybd_event((byte) VK_NUMLOCK, (byte) 0x45, KEYEVENTF_EXTENDEDKEY, 0);
        User32Ext.INSTANCE.keybd_event((byte) VK_NUMLOCK, (byte) 0x45, KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP, 0);
    }

    private static void log(String msg) {
        System.out.println(LocalDateTime.now() + " | " + msg);
    }
}
