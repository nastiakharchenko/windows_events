package org.windows_events.service.monitor;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class UserMonitorService {
    private static final int WTSUserName = 5;
    private static final int WTSDomainName = 7;
    private static final int INVALID_SESSION_ID = 0xFFFFFFFF;

    public UserMonitorService() {}

    public interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);

        int WTSGetActiveConsoleSessionId();
    }

    public interface Wtsapi32Ex extends StdCallLibrary {
        Wtsapi32Ex INSTANCE = Native.load("Wtsapi32", Wtsapi32Ex.class);

        boolean WTSQuerySessionInformationW(
                Pointer hServer,
                int sessionId,
                int wtsInfoClass,
                PointerByReference ppBuffer,
                IntByReference pBytesReturned
        );

        void WTSFreeMemory(Pointer pointer);
    }

    public static String getActiveUser() {
        try {
            int sessionId = Kernel32Ex.INSTANCE.WTSGetActiveConsoleSessionId();
            if (sessionId == INVALID_SESSION_ID) {
                return "UNKNOWN";
            }

            String user = querySessionString(sessionId, WTSUserName);
            String domain = querySessionString(sessionId, WTSDomainName);

            if (isBlank(user)) {
                return "UNKNOWN";
            }
            return user;

//            if (isBlank(domain)) {
//                return user;
//            }
//
//            return domain + "\\" + user;
        } catch (Exception e) {
            System.err.println("ActiveUserResolver error: " + e.getMessage());
            return "UNKNOWN";
        }
    }

    private static String querySessionString(int sessionId, int infoClass) {
        PointerByReference bufferRef = new PointerByReference();
        IntByReference bytesReturned = new IntByReference();

        boolean ok = Wtsapi32Ex.INSTANCE.WTSQuerySessionInformationW(
                null,
                sessionId,
                infoClass,
                bufferRef,
                bytesReturned
        );

        if (!ok) {
            return "";
        }

        Pointer buffer = bufferRef.getValue();
        if (buffer == null) {
            return "";
        }

        try {
            int bytes = bytesReturned.getValue();
            if (bytes <= 2) {
                return "";
            }

            return buffer.getWideString(0);
        } finally {
            Wtsapi32Ex.INSTANCE.WTSFreeMemory(buffer);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
