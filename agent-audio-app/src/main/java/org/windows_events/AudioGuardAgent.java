package org.windows_events;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class AudioGuardAgent {

    private static final Guid.CLSID CLSID_MM_DEVICE_ENUMERATOR =
            new Guid.CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");

    private static final Guid.IID IID_IMM_DEVICE_ENUMERATOR =
            new Guid.IID("A95664D2-9614-4F35-A746-DE8DB63617E6");

    private static final Guid.IID IID_IAUDIO_SESSION_MANAGER_2 =
            new Guid.IID("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");

    private static final Guid.IID IID_IAUDIO_SESSION_CONTROL_2 =
            new Guid.IID("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D");

    private static final Guid.IID IID_ISIMPLE_AUDIO_VOLUME =
            new Guid.IID("87CE5498-68D6-44E5-9215-6DA47EF883D8");

    private static final Guid.IID IID_IAUDIO_ENDPOINT_VOLUME =
            new Guid.IID("5CDF2C82-841E-4546-9722-0CF74078229A");

    private static final int E_RENDER = 0;
    private static final int E_CONSOLE = 0;
    private static final int CLSCTX_ALL = 0x17;

    // Не долбим CoreAudio слишком часто
    private static final int CHECK_INTERVAL_MS = 750;

    private static final float MIN_ALLOWED_VOLUME = 0.01f;

    // Никогда не восстанавливаем endpoint в 100%
    private static final float MAX_ALLOWED_ENDPOINT_RESTORE = 0.90f;

    // Если не знаем громкость — используем 50%
    private static final float DEFAULT_ENDPOINT_RESTORED_VOLUME = 0.50f;

    // Приложения по-прежнему можно восстанавливать в 100%
    private static final float APP_RESTORED_VOLUME = 1.0f;

    private static volatile float lastKnownEndpointVolume =
            DEFAULT_ENDPOINT_RESTORED_VOLUME;

    // Защита от transient states Win11
    private static volatile long lastEndpointRestoreTime = 0;

    // После restore некоторое время не обновляем lastKnownEndpointVolume
    private static final long ENDPOINT_RESTORE_COOLDOWN_MS = 2000;

    private static final String EVENT_SERVER_HOST = "127.0.0.1";
    private static final int EVENT_SERVER_PORT = 47632;

    public static void runAudioAgent() {

        WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(
                Pointer.NULL,
                Ole32.COINIT_MULTITHREADED
        );

        if (COMUtils.FAILED(hr)
                && hr.intValue() != WinError.RPC_E_CHANGED_MODE) {

            COMUtils.checkRC(hr);
        }

        try {

            while (true) {

                try {
                    checkAndFixAudio();
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                TimeUnit.MILLISECONDS.sleep(CHECK_INTERVAL_MS);
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } finally {

            Ole32.INSTANCE.CoUninitialize();
        }
    }

    private static void checkAndFixAudio() {

        IMMDeviceEnumerator enumerator = null;
        IMMDevice device = null;

        try {

            enumerator = createDeviceEnumerator();

            device = enumerator.getDefaultAudioEndpoint(
                    E_RENDER,
                    E_CONSOLE
            );

            checkAndFixEndpoint(device);

            checkAndFixApplicationSessions(device);

        } finally {

            if (device != null) {
                device.release();
            }

            if (enumerator != null) {
                enumerator.release();
            }
        }
    }

    private static void checkAndFixEndpoint(IMMDevice device) {

        IAudioEndpointVolume endpointVolume = null;

        try {

            endpointVolume = device.activateEndpointVolume();

            boolean muted = endpointVolume.getMute();

            float currentVolume =
                    endpointVolume.getMasterVolumeLevelScalar();

            long now = System.currentTimeMillis();

            boolean recentlyRestored =
                    now - lastEndpointRestoreTime
                            < ENDPOINT_RESTORE_COOLDOWN_MS;

            // Нормализуем
            currentVolume = normalizeVolume(currentVolume);

            // Запоминаем только:
            // - не mute
            // - громкость > минимума
            // - не сразу после restore
            // - не подозрительные 100%
            if (!muted
                    && currentVolume > MIN_ALLOWED_VOLUME
                    && !recentlyRestored
                    && currentVolume < MAX_ALLOWED_ENDPOINT_RESTORE) {

                lastKnownEndpointVolume = currentVolume;

                return;
            }

            // Восстанавливаем только если реально mute
            // или почти нулевая громкость
            if (muted || currentVolume <= MIN_ALLOWED_VOLUME) {

                float restoredVolume =
                        normalizeRestoredEndpointVolume(
                                lastKnownEndpointVolume
                        );

                // Сначала громкость
                // потом unmute
                // На Win11 так стабильнее
                endpointVolume.setMasterVolumeLevelScalar(
                        restoredVolume
                );

                endpointVolume.setMute(false);

                lastEndpointRestoreTime =
                        System.currentTimeMillis();

                sendAudioEvent(
                        "SYSTEM_ENDPOINT",
                        0,
                        muted
                                ? "ENDPOINT_MUTE_RESTORED"
                                : "ENDPOINT_VOLUME_ZERO_RESTORED",
                        false,
                        currentVolume
                );
            }

        } finally {

            if (endpointVolume != null) {
                endpointVolume.release();
            }
        }
    }

    private static float normalizeVolume(float value) {

        if (Float.isNaN(value)) {
            return DEFAULT_ENDPOINT_RESTORED_VOLUME;
        }

        if (value < 0f) {
            return 0f;
        }

        if (value > 1f) {
            return 1f;
        }

        return value;
    }

    private static float normalizeRestoredEndpointVolume(float value) {

        value = normalizeVolume(value);

        if (value <= MIN_ALLOWED_VOLUME) {
            return DEFAULT_ENDPOINT_RESTORED_VOLUME;
        }

        // Никогда не даём восстановиться в 100%
        if (value >= MAX_ALLOWED_ENDPOINT_RESTORE) {
            return MAX_ALLOWED_ENDPOINT_RESTORE;
        }

        return value;
    }

    private static void checkAndFixApplicationSessions(
            IMMDevice device
    ) {

        IAudioSessionManager2 sessionManager = null;
        IAudioSessionEnumerator sessionEnumerator = null;

        try {

            sessionManager =
                    device.activateAudioSessionManager2();

            sessionEnumerator =
                    sessionManager.getSessionEnumerator();

            int count = sessionEnumerator.getCount();

            for (int i = 0; i < count; i++) {

                IAudioSessionControl control = null;
                IAudioSessionControl2 control2 = null;
                ISimpleAudioVolume volume = null;

                try {

                    control = sessionEnumerator.getSession(i);

                    control2 =
                            control.queryAudioSessionControl2();

                    int pid = control2.getProcessId();

                    String processName =
                            getProcessName(pid);

                    if (processName == null
                            || processName.isBlank()) {

                        continue;
                    }

                    volume =
                            control.querySimpleAudioVolume();

                    boolean muted = volume.getMute();

                    float currentVolume =
                            volume.getMasterVolume();

                    currentVolume =
                            normalizeVolume(currentVolume);

                    if (muted
                            || currentVolume <= MIN_ALLOWED_VOLUME) {

                        volume.setMasterVolume(
                                APP_RESTORED_VOLUME
                        );

                        volume.setMute(false);

                        sendAudioEvent(
                                processName,
                                pid,
                                muted
                                        ? "APP_MUTE_RESTORED"
                                        : "APP_VOLUME_ZERO_RESTORED",
                                true,
                                currentVolume
                        );
                    }

                } finally {

                    if (volume != null) {
                        volume.release();
                    }

                    if (control2 != null) {
                        control2.release();
                    }

                    if (control != null) {
                        control.release();
                    }
                }
            }

        } finally {

            if (sessionEnumerator != null) {
                sessionEnumerator.release();
            }

            if (sessionManager != null) {
                sessionManager.release();
            }
        }
    }

    private static void sendAudioEvent(
            String processName,
            int pid,
            String action,
            boolean volumeRestoredToMax,
            float currentVolume
    ) {

        String json = "{"
                + "\"processName\":\""
                + escapeJson(processName)
                + "\","
                + "\"pid\":" + pid + ","
                + "\"action\":\""
                + escapeJson(action)
                + "\","
                + "\"volumeRestoredToMax\":"
                + volumeRestoredToMax
                + ","
                + "\"currentVolume\":"
                + currentVolume
                + ","
                + "\"timestamp\":"
                + System.currentTimeMillis()
                + "}";

        try (
                Socket socket = new Socket(
                        EVENT_SERVER_HOST,
                        EVENT_SERVER_PORT
                );

                OutputStreamWriter writer =
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8
                        );

                BufferedWriter bufferedWriter =
                        new BufferedWriter(writer)
        ) {

            bufferedWriter.write(json);
            bufferedWriter.newLine();
            bufferedWriter.flush();

        } catch (Exception ignored) {
        }
    }

    private static String getProcessName(int pid) {

        try {

            if (pid <= 0) {
                return null;
            }

            Optional<ProcessHandle> handle =
                    ProcessHandle.of(pid);

            if (handle.isEmpty()) {
                return null;
            }

            Optional<String> command =
                    handle.get().info().command();

            if (command.isEmpty()) {
                return null;
            }

            return Path.of(command.get())
                    .getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT);

        } catch (Throwable ignored) {

            return null;
        }
    }

    private static String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static IMMDeviceEnumerator createDeviceEnumerator() {

        PointerByReference ppv =
                new PointerByReference();

        WinNT.HRESULT hr =
                Ole32.INSTANCE.CoCreateInstance(
                        CLSID_MM_DEVICE_ENUMERATOR,
                        null,
                        CLSCTX_ALL,
                        IID_IMM_DEVICE_ENUMERATOR,
                        ppv
                );

        COMUtils.checkRC(hr);

        return new IMMDeviceEnumerator(
                ppv.getValue()
        );
    }

    static class ComObject extends PointerType {

        ComObject(Pointer pointer) {
            setPointer(pointer);
        }

        int release() {

            return (Integer) invokeNativeObject(
                    2,
                    new Object[]{getPointer()},
                    Integer.class
            );
        }

        Pointer queryInterface(Guid.IID iid) {

            PointerByReference ppv =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            0,
                            new Object[]{
                                    getPointer(),
                                    new Guid.REFIID(iid),
                                    ppv
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return ppv.getValue();
        }

        protected Object invokeNativeObject(
                int vtableId,
                Object[] args,
                Class<?> returnType
        ) {

            Pointer vtable =
                    getPointer().getPointer(0);

            Pointer functionPointer =
                    vtable.getPointer(
                            (long) vtableId
                                    * Native.POINTER_SIZE
                    );

            Function function =
                    Function.getFunction(functionPointer);

            return function.invoke(returnType, args);
        }
    }

    static class IMMDeviceEnumerator extends ComObject {

        IMMDeviceEnumerator(Pointer pointer) {
            super(pointer);
        }

        IMMDevice getDefaultAudioEndpoint(
                int dataFlow,
                int role
        ) {

            PointerByReference ppDevice =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            4,
                            new Object[]{
                                    getPointer(),
                                    dataFlow,
                                    role,
                                    ppDevice
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return new IMMDevice(
                    ppDevice.getValue()
            );
        }
    }

    static class IMMDevice extends ComObject {

        IMMDevice(Pointer pointer) {
            super(pointer);
        }

        IAudioSessionManager2
        activateAudioSessionManager2() {

            PointerByReference ppInterface =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            3,
                            new Object[]{
                                    getPointer(),
                                    new Guid.REFIID(
                                            IID_IAUDIO_SESSION_MANAGER_2
                                    ),
                                    CLSCTX_ALL,
                                    Pointer.NULL,
                                    ppInterface
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return new IAudioSessionManager2(
                    ppInterface.getValue()
            );
        }

        IAudioEndpointVolume activateEndpointVolume() {

            PointerByReference ppInterface =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            3,
                            new Object[]{
                                    getPointer(),
                                    new Guid.REFIID(
                                            IID_IAUDIO_ENDPOINT_VOLUME
                                    ),
                                    CLSCTX_ALL,
                                    Pointer.NULL,
                                    ppInterface
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return new IAudioEndpointVolume(
                    ppInterface.getValue()
            );
        }
    }

    static class IAudioEndpointVolume extends ComObject {

        IAudioEndpointVolume(Pointer pointer) {
            super(pointer);
        }

        void setMasterVolumeLevelScalar(float level) {

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            7,
                            new Object[]{
                                    getPointer(),
                                    level,
                                    Pointer.NULL
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);
        }

        float getMasterVolumeLevelScalar() {

            FloatByReference level =
                    new FloatByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            9,
                            new Object[]{
                                    getPointer(),
                                    level
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return level.getValue();
        }

        void setMute(boolean mute) {

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            14,
                            new Object[]{
                                    getPointer(),
                                    mute ? 1 : 0,
                                    Pointer.NULL
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);
        }

        boolean getMute() {

            IntByReference muted =
                    new IntByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            15,
                            new Object[]{
                                    getPointer(),
                                    muted
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return muted.getValue() != 0;
        }
    }

    static class IAudioSessionManager2 extends ComObject {

        IAudioSessionManager2(Pointer pointer) {
            super(pointer);
        }

        IAudioSessionEnumerator getSessionEnumerator() {

            PointerByReference ppEnum =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            5,
                            new Object[]{
                                    getPointer(),
                                    ppEnum
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return new IAudioSessionEnumerator(
                    ppEnum.getValue()
            );
        }
    }

    static class IAudioSessionEnumerator extends ComObject {

        IAudioSessionEnumerator(Pointer pointer) {
            super(pointer);
        }

        int getCount() {

            IntByReference count =
                    new IntByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            3,
                            new Object[]{
                                    getPointer(),
                                    count
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return count.getValue();
        }

        IAudioSessionControl getSession(int index) {

            PointerByReference ppSession =
                    new PointerByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            4,
                            new Object[]{
                                    getPointer(),
                                    index,
                                    ppSession
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return new IAudioSessionControl(
                    ppSession.getValue()
            );
        }
    }

    static class IAudioSessionControl extends ComObject {

        IAudioSessionControl(Pointer pointer) {
            super(pointer);
        }

        IAudioSessionControl2
        queryAudioSessionControl2() {

            Pointer pointer =
                    queryInterface(
                            IID_IAUDIO_SESSION_CONTROL_2
                    );

            return new IAudioSessionControl2(pointer);
        }

        ISimpleAudioVolume
        querySimpleAudioVolume() {

            Pointer pointer =
                    queryInterface(
                            IID_ISIMPLE_AUDIO_VOLUME
                    );

            return new ISimpleAudioVolume(pointer);
        }
    }

    static class IAudioSessionControl2 extends ComObject {

        IAudioSessionControl2(Pointer pointer) {
            super(pointer);
        }

        int getProcessId() {

            IntByReference pid =
                    new IntByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            14,
                            new Object[]{
                                    getPointer(),
                                    pid
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return pid.getValue();
        }
    }

    static class ISimpleAudioVolume extends ComObject {

        ISimpleAudioVolume(Pointer pointer) {
            super(pointer);
        }

        void setMasterVolume(float value) {

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            3,
                            new Object[]{
                                    getPointer(),
                                    value,
                                    Pointer.NULL
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);
        }

        float getMasterVolume() {

            FloatByReference level =
                    new FloatByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            4,
                            new Object[]{
                                    getPointer(),
                                    level
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return level.getValue();
        }

        void setMute(boolean mute) {

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            5,
                            new Object[]{
                                    getPointer(),
                                    mute ? 1 : 0,
                                    Pointer.NULL
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);
        }

        boolean getMute() {

            IntByReference muted =
                    new IntByReference();

            WinNT.HRESULT hr =
                    (WinNT.HRESULT) invokeNativeObject(
                            6,
                            new Object[]{
                                    getPointer(),
                                    muted
                            },
                            WinNT.HRESULT.class
                    );

            COMUtils.checkRC(hr);

            return muted.getValue() != 0;
        }
    }
}


//=========================================================================================================================


//package org.windows_events;
//
//import com.sun.jna.Function;
//import com.sun.jna.Native;
//import com.sun.jna.Pointer;
//import com.sun.jna.PointerType;
//import com.sun.jna.platform.win32.*;
//import com.sun.jna.platform.win32.COM.COMUtils;
//import com.sun.jna.ptr.FloatByReference;
//import com.sun.jna.ptr.IntByReference;
//import com.sun.jna.ptr.PointerByReference;
//
//import java.io.BufferedWriter;
//import java.io.OutputStreamWriter;
//import java.net.Socket;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Path;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
//public class AudioGuardAgent {
//    private static final Guid.CLSID CLSID_MM_DEVICE_ENUMERATOR =
//            new Guid.CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");
//
//    private static final Guid.IID IID_IMM_DEVICE_ENUMERATOR =
//            new Guid.IID("A95664D2-9614-4F35-A746-DE8DB63617E6");
//
//    private static final Guid.IID IID_IAUDIO_SESSION_MANAGER_2 =
//            new Guid.IID("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
//
//    private static final Guid.IID IID_IAUDIO_SESSION_CONTROL_2 =
//            new Guid.IID("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D");
//
//    private static final Guid.IID IID_ISIMPLE_AUDIO_VOLUME =
//            new Guid.IID("87CE5498-68D6-44E5-9215-6DA47EF883D8");
//
//    private static final Guid.IID IID_IAUDIO_ENDPOINT_VOLUME =
//            new Guid.IID("5CDF2C82-841E-4546-9722-0CF74078229A");
//
//    private static final int E_RENDER = 0;
//    private static final int E_CONSOLE = 0;
//    private static final int CLSCTX_ALL = 0x17;
//
//    private static final int CHECK_INTERVAL_MS = 100;
//
//    private static final float MIN_ALLOWED_VOLUME = 0.01f;
//
//    // Для приложений по-прежнему восстанавливаем в 100%
//    private static final float APP_RESTORED_VOLUME = 1.0f;
//
//    // Для динамиков восстанавливаем последнее нормальное значение
//    private static final float DEFAULT_ENDPOINT_RESTORED_VOLUME = 0.5f;
//    private static float lastKnownEndpointVolume = DEFAULT_ENDPOINT_RESTORED_VOLUME;
//
//    private static final String EVENT_SERVER_HOST = "127.0.0.1";
//    private static final int EVENT_SERVER_PORT = 47632;
//
//    public static void runAudioAgent() {
//        WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(
//                Pointer.NULL,
//                Ole32.COINIT_MULTITHREADED
//        );
//
//        if (COMUtils.FAILED(hr) && hr.intValue() != WinError.RPC_E_CHANGED_MODE) {
//            COMUtils.checkRC(hr);
//        }
//
////        System.out.println("AudioGuardAgent started.");
//
//        try {
//            while (true) {
//                try {
//                    checkAndFixAudio();
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                }
//
//                TimeUnit.MILLISECONDS.sleep(CHECK_INTERVAL_MS);
//            }
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//
//        } finally {
//            Ole32.INSTANCE.CoUninitialize();
//        }
//    }
//
//    private static void checkAndFixAudio() {
//        IMMDeviceEnumerator enumerator = null;
//        IMMDevice device = null;
//
//        try {
//            enumerator = createDeviceEnumerator();
//            device = enumerator.getDefaultAudioEndpoint(E_RENDER, E_CONSOLE);
//
//            checkAndFixEndpoint(device);
//            checkAndFixApplicationSessions(device);
//
//        } finally {
//            if (device != null) {
//                device.release();
//            }
//
//            if (enumerator != null) {
//                enumerator.release();
//            }
//        }
//    }
//
//    private static void checkAndFixEndpoint(IMMDevice device) {
//        IAudioEndpointVolume endpointVolume = null;
//
//        try {
//            endpointVolume = device.activateEndpointVolume();
//
//            boolean muted = endpointVolume.getMute();
//            float currentVolume = endpointVolume.getMasterVolumeLevelScalar();
//
//            // Если динамики не замьючены и громкость выше минимума —
//            // запоминаем это как последнее нормальное значение.
//            if (!muted && currentVolume > MIN_ALLOWED_VOLUME) {
//                lastKnownEndpointVolume = currentVolume;
//                return;
//            }
//
//            // Если пользователь нажал mute или опустил динамики почти в 0 —
//            // возвращаем не 100%, а последнее нормальное значение.
//            if (muted || currentVolume <= MIN_ALLOWED_VOLUME) {
//                String action = muted
//                        ? "ENDPOINT_MUTE_RESTORED"
//                        : "ENDPOINT_VOLUME_ZERO_RESTORED";
//
//                float restoredVolume = normalizeEndpointVolume(lastKnownEndpointVolume);
//
//                System.out.println(
//                        "Endpoint audio disabled. muted="
//                                + muted
//                                + ", currentVolume="
//                                + currentVolume
//                                + ", restoredVolume="
//                                + restoredVolume
//                );
//
//                endpointVolume.setMute(false);
//                endpointVolume.setMasterVolumeLevelScalar(restoredVolume);
//
//                sendAudioEvent(
//                        "SYSTEM_ENDPOINT",
//                        0,
//                        action,
//                        false,
//                        currentVolume
//                );
//
////                System.out.println("Endpoint audio restored to last volume: " + restoredVolume);
//            }
//
//        } finally {
//            if (endpointVolume != null) {
//                endpointVolume.release();
//            }
//        }
//    }
//
//    private static float normalizeEndpointVolume(float value) {
//        if (value <= MIN_ALLOWED_VOLUME) {
//            return DEFAULT_ENDPOINT_RESTORED_VOLUME;
//        }
//
//        if (value > 1.0f) {
//            return 1.0f;
//        }
//
//        return value;
//    }
//
//    private static void checkAndFixApplicationSessions(IMMDevice device) {
//        IAudioSessionManager2 sessionManager = null;
//        IAudioSessionEnumerator sessionEnumerator = null;
//
//        try {
//            sessionManager = device.activateAudioSessionManager2();
//            sessionEnumerator = sessionManager.getSessionEnumerator();
//
//            int count = sessionEnumerator.getCount();
//
//            for (int i = 0; i < count; i++) {
//                IAudioSessionControl control = null;
//                IAudioSessionControl2 control2 = null;
//                ISimpleAudioVolume volume = null;
//
//                try {
//                    control = sessionEnumerator.getSession(i);
//                    control2 = control.queryAudioSessionControl2();
//
//                    int pid = control2.getProcessId();
//                    String processName = getProcessName(pid);
//
//                    if (processName == null || processName.isBlank()) {
//                        continue;
//                    }
//
//                    volume = control.querySimpleAudioVolume();
//
//                    boolean muted = volume.getMute();
//                    float currentVolume = volume.getMasterVolume();
//
//                    if (muted || currentVolume <= MIN_ALLOWED_VOLUME) {
//                        String action = muted
//                                ? "APP_MUTE_RESTORED"
//                                : "APP_VOLUME_ZERO_RESTORED";
//
//                        System.out.println(
//                                "Application audio disabled: "
//                                        + processName
//                                        + ", pid="
//                                        + pid
//                                        + ", muted="
//                                        + muted
//                                        + ", volume="
//                                        + currentVolume
//                        );
//
//                        volume.setMute(false);
//                        volume.setMasterVolume(APP_RESTORED_VOLUME);
//
//                        sendAudioEvent(
//                                processName,
//                                pid,
//                                action,
//                                true,
//                                currentVolume
//                        );
//                    }
//
//                } finally {
//                    if (volume != null) {
//                        volume.release();
//                    }
//
//                    if (control2 != null) {
//                        control2.release();
//                    }
//
//                    if (control != null) {
//                        control.release();
//                    }
//                }
//            }
//
//        } finally {
//            if (sessionEnumerator != null) {
//                sessionEnumerator.release();
//            }
//
//            if (sessionManager != null) {
//                sessionManager.release();
//            }
//        }
//    }
//
//    private static void sendAudioEvent(
//            String processName,
//            int pid,
//            String action,
//            boolean volumeRestoredToMax,
//            float currentVolume
//    ) {
//        String json = "{"
//                + "\"processName\":\"" + escapeJson(processName) + "\","
//                + "\"pid\":" + pid + ","
//                + "\"action\":\"" + escapeJson(action) + "\","
//                + "\"volumeRestoredToMax\":" + volumeRestoredToMax + ","
//                + "\"currentVolume\":" + currentVolume + ","
//                + "\"timestamp\":" + System.currentTimeMillis()
//                + "}";
//
//        try (Socket socket = new Socket(EVENT_SERVER_HOST, EVENT_SERVER_PORT);
//             OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
//             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
//
//            bufferedWriter.write(json);
//            bufferedWriter.newLine();
//            bufferedWriter.flush();
//
//        } catch (Exception ignored) {
//        }
//    }
//
//    private static String getProcessName(int pid) {
//        try {
//            if (pid <= 0) {
//                return null;
//            }
//
//            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
//
//            if (handle.isEmpty()) {
//                return null;
//            }
//
//            Optional<String> command = handle.get().info().command();
//
//            if (command.isEmpty()) {
//                return null;
//            }
//
//            return Path.of(command.get()).getFileName().toString().toLowerCase(Locale.ROOT);
//
//        } catch (Throwable ignored) {
//            return null;
//        }
//    }
//
//    private static String escapeJson(String value) {
//        if (value == null) {
//            return "";
//        }
//
//        return value
//                .replace("\\", "\\\\")
//                .replace("\"", "\\\"");
//    }
//
//    private static IMMDeviceEnumerator createDeviceEnumerator() {
//        PointerByReference ppv = new PointerByReference();
//
//        WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
//                CLSID_MM_DEVICE_ENUMERATOR,
//                null,
//                CLSCTX_ALL,
//                IID_IMM_DEVICE_ENUMERATOR,
//                ppv
//        );
//
//        COMUtils.checkRC(hr);
//
//        return new IMMDeviceEnumerator(ppv.getValue());
//    }
//
//    static class ComObject extends PointerType {
//
//        ComObject(Pointer pointer) {
//            setPointer(pointer);
//        }
//
//        int release() {
//            return (Integer) invokeNativeObject(
//                    2,
//                    new Object[]{getPointer()},
//                    Integer.class
//            );
//        }
//
//        Pointer queryInterface(Guid.IID iid) {
//            PointerByReference ppv = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    0,
//                    new Object[]{
//                            getPointer(),
//                            new Guid.REFIID(iid),
//                            ppv
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return ppv.getValue();
//        }
//
//        protected Object invokeNativeObject(int vtableId, Object[] args, Class<?> returnType) {
//            Pointer vtable = getPointer().getPointer(0);
//            Pointer functionPointer = vtable.getPointer((long) vtableId * Native.POINTER_SIZE);
//            Function function = Function.getFunction(functionPointer);
//            return function.invoke(returnType, args);
//        }
//    }
//
//    static class IMMDeviceEnumerator extends ComObject {
//
//        IMMDeviceEnumerator(Pointer pointer) {
//            super(pointer);
//        }
//
//        IMMDevice getDefaultAudioEndpoint(int dataFlow, int role) {
//            PointerByReference ppDevice = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    4,
//                    new Object[]{
//                            getPointer(),
//                            dataFlow,
//                            role,
//                            ppDevice
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return new IMMDevice(ppDevice.getValue());
//        }
//    }
//
//    static class IMMDevice extends ComObject {
//
//        IMMDevice(Pointer pointer) {
//            super(pointer);
//        }
//
//        IAudioSessionManager2 activateAudioSessionManager2() {
//            PointerByReference ppInterface = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    3,
//                    new Object[]{
//                            getPointer(),
//                            new Guid.REFIID(IID_IAUDIO_SESSION_MANAGER_2),
//                            CLSCTX_ALL,
//                            Pointer.NULL,
//                            ppInterface
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return new IAudioSessionManager2(ppInterface.getValue());
//        }
//
//        IAudioEndpointVolume activateEndpointVolume() {
//            PointerByReference ppInterface = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    3,
//                    new Object[]{
//                            getPointer(),
//                            new Guid.REFIID(IID_IAUDIO_ENDPOINT_VOLUME),
//                            CLSCTX_ALL,
//                            Pointer.NULL,
//                            ppInterface
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return new IAudioEndpointVolume(ppInterface.getValue());
//        }
//    }
//
//    static class IAudioEndpointVolume extends ComObject {
//
//        IAudioEndpointVolume(Pointer pointer) {
//            super(pointer);
//        }
//
//        void setMasterVolumeLevelScalar(float level) {
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    7,
//                    new Object[]{
//                            getPointer(),
//                            level,
//                            Pointer.NULL
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//        }
//
//        float getMasterVolumeLevelScalar() {
//            FloatByReference level = new FloatByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    9,
//                    new Object[]{
//                            getPointer(),
//                            level
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return level.getValue();
//        }
//
//        void setMute(boolean mute) {
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    14,
//                    new Object[]{
//                            getPointer(),
//                            mute ? 1 : 0,
//                            Pointer.NULL
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//        }
//
//        boolean getMute() {
//            IntByReference muted = new IntByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    15,
//                    new Object[]{
//                            getPointer(),
//                            muted
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return muted.getValue() != 0;
//        }
//    }
//
//    static class IAudioSessionManager2 extends ComObject {
//
//        IAudioSessionManager2(Pointer pointer) {
//            super(pointer);
//        }
//
//        IAudioSessionEnumerator getSessionEnumerator() {
//            PointerByReference ppEnum = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    5,
//                    new Object[]{
//                            getPointer(),
//                            ppEnum
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return new IAudioSessionEnumerator(ppEnum.getValue());
//        }
//    }
//
//    static class IAudioSessionEnumerator extends ComObject {
//
//        IAudioSessionEnumerator(Pointer pointer) {
//            super(pointer);
//        }
//
//        int getCount() {
//            IntByReference count = new IntByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    3,
//                    new Object[]{
//                            getPointer(),
//                            count
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return count.getValue();
//        }
//
//        IAudioSessionControl getSession(int index) {
//            PointerByReference ppSession = new PointerByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    4,
//                    new Object[]{
//                            getPointer(),
//                            index,
//                            ppSession
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return new IAudioSessionControl(ppSession.getValue());
//        }
//    }
//
//    static class IAudioSessionControl extends ComObject {
//
//        IAudioSessionControl(Pointer pointer) {
//            super(pointer);
//        }
//
//        IAudioSessionControl2 queryAudioSessionControl2() {
//            Pointer pointer = queryInterface(IID_IAUDIO_SESSION_CONTROL_2);
//            return new IAudioSessionControl2(pointer);
//        }
//
//        ISimpleAudioVolume querySimpleAudioVolume() {
//            Pointer pointer = queryInterface(IID_ISIMPLE_AUDIO_VOLUME);
//            return new ISimpleAudioVolume(pointer);
//        }
//    }
//
//    static class IAudioSessionControl2 extends ComObject {
//
//        IAudioSessionControl2(Pointer pointer) {
//            super(pointer);
//        }
//
//        int getProcessId() {
//            IntByReference pid = new IntByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    14,
//                    new Object[]{
//                            getPointer(),
//                            pid
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return pid.getValue();
//        }
//    }
//
//    static class ISimpleAudioVolume extends ComObject {
//
//        ISimpleAudioVolume(Pointer pointer) {
//            super(pointer);
//        }
//
//        void setMasterVolume(float value) {
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    3,
//                    new Object[]{
//                            getPointer(),
//                            value,
//                            Pointer.NULL
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//        }
//
//        float getMasterVolume() {
//            FloatByReference level = new FloatByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    4,
//                    new Object[]{
//                            getPointer(),
//                            level
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return level.getValue();
//        }
//
//        void setMute(boolean mute) {
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    5,
//                    new Object[]{
//                            getPointer(),
//                            mute ? 1 : 0,
//                            Pointer.NULL
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//        }
//
//        boolean getMute() {
//            IntByReference muted = new IntByReference();
//
//            WinNT.HRESULT hr = (WinNT.HRESULT) invokeNativeObject(
//                    6,
//                    new Object[]{
//                            getPointer(),
//                            muted
//                    },
//                    WinNT.HRESULT.class
//            );
//
//            COMUtils.checkRC(hr);
//            return muted.getValue() != 0;
//        }
//    }
//}
