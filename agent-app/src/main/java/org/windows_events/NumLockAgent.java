package org.windows_events;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class NumLockAgent {

    private static final int VK_NUMLOCK = 0x90;
    private static final int KEYEVENTF_EXTENDEDKEY = 0x0001;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final long CHECK_INTERVAL_MS = 300L;
    private static Pointer mutexHandle;

    private NumLockAgent() {
    }

    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);

        short GetKeyState(int nVirtKey);

        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);

        Pointer GetForegroundWindow();

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference lpdwProcessId);

        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);
    }

    public interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        Pointer CreateMutexA(Pointer lpMutexAttributes, boolean bInitialOwner, String lpName);

        int GetLastError();
    }

    public static void runAgent(String host, int port) {
        if (!acquireSingleInstance()) {
            return;
        }

        boolean previousState = isNumLockOn();

        sendEvent(host, port, new NumLockEvent(
                "AGENT_STARTED",
                System.getProperty("user.name"),
                previousState ? "ON" : "OFF",
                getActiveWindowTitle(),
                getActiveProcessName(),
                getActivePid(),
                System.currentTimeMillis(),
                getLocalHostName(),
                "NumLock agent started"
        ));

        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean currentState = isNumLockOn();

                if (currentState != previousState) {
                    sendEvent(host, port, buildEvent(
                            "NUMLOCK_STATE_CHANGED",
                            currentState,
                            "NumLock state changed while workstation is active"
                    ));
                    previousState = currentState;
                }

                if (!currentState) {
                    sendEvent(host, port, buildEvent(
                            "NUMLOCK_OFF_DETECTED",
                            false,
                            "NumLock is OFF, forcing ON"
                    ));

                    forceNumLockOn();
                    Thread.sleep(150L);

                    boolean afterToggle = isNumLockOn();
                    sendEvent(host, port, buildEvent(
                            "NUMLOCK_FORCED_ON",
                            afterToggle,
                            afterToggle
                                    ? "NumLock was successfully forced to ON"
                                    : "Attempt to force NumLock ON failed"
                    ));
                    previousState = afterToggle;
                }

                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                sendEvent(host, port, new NumLockEvent(
                        "AGENT_ERROR",
                        System.getProperty("user.name"),
                        isNumLockOn() ? "ON" : "OFF",
                        getActiveWindowTitle(),
                        getActiveProcessName(),
                        getActivePid(),
                        System.currentTimeMillis(),
                        getLocalHostName(),
                        e.getMessage() == null ? "Unknown agent error" : e.getMessage()
                ));

                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static boolean acquireSingleInstance() {
        mutexHandle = Kernel32Ext.INSTANCE.CreateMutexA(null, false, "Global\\WindowsEventsNumLockAgent");
        int lastError = Kernel32Ext.INSTANCE.GetLastError();
        return lastError != 183;
    }

    private static NumLockEvent buildEvent(String eventType, boolean state, String message) {
        return new NumLockEvent(
                eventType,
                System.getProperty("user.name"),
                state ? "ON" : "OFF",
                getActiveWindowTitle(),
                getActiveProcessName(),
                getActivePid(),
                System.currentTimeMillis(),
                getLocalHostName(),
                message
        );
    }

    private static boolean isNumLockOn() {
        return (User32Ext.INSTANCE.GetKeyState(VK_NUMLOCK) & 0x0001) != 0;
    }

    private static void forceNumLockOn() {
        if (!isNumLockOn()) {
            toggleNumLock();
        }
    }

    private static void toggleNumLock() {
        User32Ext.INSTANCE.keybd_event((byte) VK_NUMLOCK, (byte) 0x45, KEYEVENTF_EXTENDEDKEY, 0);
        User32Ext.INSTANCE.keybd_event((byte) VK_NUMLOCK, (byte) 0x45, KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP, 0);
    }

    private static void sendEvent(String host, int port, NumLockEvent event) {
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println(NumLockJson.toJson(event));
        } catch (Exception ignored) {
        }
    }

    private static String getActiveWindowTitle() {
        try {
            Pointer hwnd = User32Ext.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return "";
            }

            char[] buffer = new char[1024];
            User32Ext.INSTANCE.GetWindowTextW(hwnd, buffer, buffer.length);
            String value = Native.toString(buffer);
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private static int getActivePid() {
        try {
            Pointer hwnd = User32Ext.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return -1;
            }

            IntByReference pidRef = new IntByReference();
            User32Ext.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            return pidRef.getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String getActiveProcessName() {
        try {
            int pid = getActivePid();
            if (pid <= 0) {
                return "";
            }

            return ProcessHandle.of(pid)
                    .flatMap(handle -> handle.info().command())
                    .map(path -> {
                        int idx = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
                        return idx >= 0 ? path.substring(idx + 1) : path;
                    })
                    .orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }
}
