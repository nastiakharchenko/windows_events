package org.windows_events.service.monitor;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.*;

public class NumLockMonitorService {

    // --- User32 для GetKeyState ---
    public interface MyUser32 extends StdCallLibrary {
        MyUser32 INSTANCE = Native.load("user32", MyUser32.class);
        short GetKeyState(int nVirtKey);
    }

    // --- kernel32 ---
    public interface MyKernel32 extends StdCallLibrary {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        int WTSGetActiveConsoleSessionId();
        boolean CloseHandle(WinNT.HANDLE h);
    }

    // --- wtsapi32 ---
    public interface MyWtsapi32 extends StdCallLibrary {
        MyWtsapi32 INSTANCE = Native.load("wtsapi32", MyWtsapi32.class);
        boolean WTSQueryUserToken(int sessionId, WinNT.HANDLEByReference phToken);
    }

    public static void main(String[] args) throws Exception {

        if (args.length > 0 && args[0].equals("helper")) {
            runHelperPipe(args[1]); // args[1] = pipe handle
            return;
        }

        // --- Step 1: активная сессия ---
        int sessionId = MyKernel32.INSTANCE.WTSGetActiveConsoleSessionId();
        if (sessionId == 0xFFFFFFFF) {
            System.err.println("No active session.");
            return;
        }

        // --- Step 2: токен пользователя ---
        WinNT.HANDLEByReference userToken = new WinNT.HANDLEByReference();
        boolean ok = MyWtsapi32.INSTANCE.WTSQueryUserToken(sessionId, userToken);
        if (!ok) {
            System.err.println("WTSQueryUserToken failed. Error=" + Kernel32.INSTANCE.GetLastError());
            return;
        }

        // --- Step 3: создаём pipe ---
        WinNT.HANDLEByReference hRead = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference hWrite = new WinNT.HANDLEByReference();
        if (!Kernel32.INSTANCE.CreatePipe(hRead, hWrite, null, 0)) {
            System.err.println("CreatePipe failed");
            return;
        }

        // --- Step 4: запускаем helper процесс ---
        String javaExe = System.getProperty("java.home") + "\\bin\\java.exe";
        String classpath = System.getProperty("java.class.path");
        String className = NumLockMonitorService.class.getName();
        String pipeHandleStr = Long.toString(Pointer.nativeValue(hWrite.getValue().getPointer()));
        String command = String.format("\"%s\" -cp \"%s\" %s helper %s", javaExe, classpath, className, pipeHandleStr);

        WinBase.STARTUPINFO si = new WinBase.STARTUPINFO();
        si.dwFlags = WinBase.STARTF_USESTDHANDLES;
        si.hStdOutput = hWrite.getValue();
        si.hStdError = hWrite.getValue();

        WinBase.PROCESS_INFORMATION pi = new WinBase.PROCESS_INFORMATION();

        boolean created = Advapi32.INSTANCE.CreateProcessAsUser(
                userToken.getValue(),
                null,
                command,
                null, null,
                true,
                0,
                null,
                null,
                si,
                pi
        );

        if (!created) {
            System.err.println("CreateProcessAsUser failed: " + Kernel32.INSTANCE.GetLastError());
            return;
        }

        Kernel32.INSTANCE.CloseHandle(hWrite.getValue());

        // --- Step 5: читаем результат из pipe ---
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getFd(hRead.getValue()))))) {
            String line = reader.readLine();
            System.out.println("From helper: " + line);
        }

        Kernel32.INSTANCE.CloseHandle(hRead.getValue());
        Kernel32.INSTANCE.CloseHandle(pi.hProcess);
        Kernel32.INSTANCE.CloseHandle(pi.hThread);
    }

    // --- Helper метод ---
    private static void runHelperPipe(String pipeHandleStr) {
        long handleVal = Long.parseLong(pipeHandleStr);
        WinNT.HANDLE hPipe = new WinNT.HANDLE(Pointer.createConstant(handleVal));

        short numState = MyUser32.INSTANCE.GetKeyState(0x90); // VK_NUMLOCK
        boolean numLock = (numState & 1) != 0;

        short capsState = MyUser32.INSTANCE.GetKeyState(0x14); // VK_CAPITAL
        boolean capsLock = (capsState & 1) != 0;

        String result = "NumLock=" + numLock + " CapsLock=" + capsLock;

        try (OutputStream os = new FileOutputStream(getFd(hPipe))) {
            os.write((result + "\n").getBytes());
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Kernel32.INSTANCE.CloseHandle(hPipe);
    }

    // --- HANDLE → FileDescriptor ---
    private static FileDescriptor getFd(WinNT.HANDLE handle) throws Exception {
        FileDescriptor fd = new FileDescriptor();
        java.lang.reflect.Field f = FileDescriptor.class.getDeclaredField("handle");
        f.setAccessible(true);
        f.set(fd, Pointer.nativeValue(handle.getPointer()));
        return fd;
    }

//    // --- kernel32 fix ---
//    public interface MyKernel32 extends StdCallLibrary {
//        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
//        int WTSGetActiveConsoleSessionId();
//    }
//
//    // --- wtsapi32 fix ---
//    public interface MyWtsapi32 extends StdCallLibrary {
//        MyWtsapi32 INSTANCE = Native.load("wtsapi32", MyWtsapi32.class);
//        boolean WTSQueryUserToken(int sessionId, WinNT.HANDLEByReference phToken);
//    }
//
//    public interface MyUser32 extends StdCallLibrary {
//        MyUser32 INSTANCE = Native.load("user32", MyUser32.class);
//
//        // VK_NUMLOCK = 0x90, VK_CAPITAL = 0x14
//        short GetKeyState(int nVirtKey);
//    }
//
//    public static void main(String[] args) throws Exception {
//        if (args.length > 0 && args[0].equals("helper")) {
//            runHelper();
//            return;
//        }
//
//        int sessionId = MyKernel32.INSTANCE.WTSGetActiveConsoleSessionId();
//        if (sessionId == 0xFFFFFFFF) {
//            System.err.println("No active session");
//            return;
//        }
//
//        WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();
//        if (!MyWtsapi32.INSTANCE.WTSQueryUserToken(sessionId, token)) {
//            System.err.println("WTSQueryUserToken failed: " + Kernel32.INSTANCE.GetLastError());
//            return;
//        }
//
//        // Запускаем helper (этот же класс)
//        String javaExe = System.getProperty("java.home") + "\\bin\\java.exe";
//        String classpath = System.getProperty("java.class.path");
//        String className = NumLockMonitorService.class.getName();
//        String command = String.format("\"%s\" -cp \"%s\" %s helper", javaExe, classpath, className);
//
//        WinBase.STARTUPINFO si = new WinBase.STARTUPINFO();
//        WinBase.PROCESS_INFORMATION pi = new WinBase.PROCESS_INFORMATION();
//
//        boolean result = Advapi32.INSTANCE.CreateProcessAsUser(
//                token.getValue(),
//                null,
//                command,
//                null, null,
//                false,
//                0,
//                null, null,
//                si, pi
//        );
//
//        if (!result) {
//            System.err.println("CreateProcessAsUser failed: " + Kernel32.INSTANCE.GetLastError());
//        }
//    }
//
//    private static void runHelper() {
//        boolean num = (MyUser32.INSTANCE.GetKeyState(0x90) & 1) != 0;
//        boolean caps = (MyUser32.INSTANCE.GetKeyState(0x14) & 1) != 0;
//
//        char[] buffer = new char[256];
//        IntByReference len = new IntByReference(buffer.length);
//        Advapi32.INSTANCE.GetUserNameW(buffer, len);
//        String user = new String(buffer, 0, len.getValue() - 1);
//
//        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
//        boolean locked = (hwnd == null);
//
//        System.out.println("User=" + user + " NumLock=" + num + " CapsLock=" + caps + " Locked=" + locked);
//    }
}
