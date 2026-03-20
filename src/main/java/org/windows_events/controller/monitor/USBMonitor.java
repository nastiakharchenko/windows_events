package org.windows_events.controller.monitor;

import lombok.Setter;
import org.windows_events.constants.Constants;
import org.windows_events.file.ConfigurationFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DataPC;
import org.windows_events.service.DateFormatter;
import org.windows_events.time.NTPTimeService;
import oshi.SystemInfo;
import oshi.hardware.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.windows_events.constants.Constants.*;

@Setter
public class USBMonitor implements Runnable {

    private Set<String> previousDevices;
    private DurableSeqLogger durableLogger;

    public USBMonitor(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    @Override
    public void run() {
        NTPTimeService ntpTimeService = new NTPTimeService(ConfigurationFile.readTimeServerHost());
        if (ntpTimeService.getNtpHost() == null || ntpTimeService.getNtpHost().isEmpty()) {
            System.err.println(Class.class.getSimpleName() + ": " + ERROR_NTP_HOST);
            return;
        }
        if(previousDevices == null || previousDevices.isEmpty()) {
            previousDevices = new HashSet<>();
        }

        while (true) {
            try {
                Set<String> currentDevices = new HashSet<>();
                findUsbDevice(currentDevices);

                for (String id : currentDevices) {
                    if(!previousDevices.contains(id)) {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                                + Constants.USB_CONNECTED + "\t"
                                + DateFormatter.dateConvert(ntpTimeService.getNTPTime()) + "\t"
                                + id);
                    }
                }

                for (String id : previousDevices) {
                    if (!currentDevices.contains(id)) {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                                + Constants.USB_DISCONNECTED + "\t"
                                + DateFormatter.dateConvert(ntpTimeService.getNTPTime()) + "\t"
                                + id);
                    }
                }

                previousDevices = currentDevices;

                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    public void findUsbDevice(Set<String> currentDevices){
        try {
            // Каждый цикл создаём новый SystemInfo и HAL, чтобы OSHI делал свежий запрос
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();

            List<UsbDevice> usbDevices = hal.getUsbDevices(true); // рекурсивно все устройства

            for (UsbDevice d : usbDevices) {
                collectDevices(d, currentDevices);
            }
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void collectDevices(UsbDevice device, Set<String> devices) {
        String name = device.getName();
        String vendor = device.getVendor();
        String id = device.getUniqueDeviceId();

        devices.add(name + " | " + vendor + " | " + id);

        for (UsbDevice child : device.getConnectedDevices()) {
            collectDevices(child, devices);
        }
    }
}

//    @Override
//    public void run() {
//        System.out.println("USB Monitor started");
//
//        Set<String> previousDrives = new HashSet<>();
//
//        while (true) {
//            try {
//                Set<String> currentDrives = new HashSet<>();
//
//                File[] roots = File.listRoots();
//                FileSystemView fsv = FileSystemView.getFileSystemView();
//
//                for (File root : roots) {
//                    String type = fsv.getSystemTypeDescription(root);
//                    if (type != null && (type.equals("USB-накопитель") || type.equals("CD-дисковод"))) {
//                        String id = root.getAbsolutePath(); // буква диска
//                        currentDrives.add(id);
//
//                        if (!previousDrives.contains(id)) {
//                            String msg = "USB connected: " + id;
//                            System.out.println(msg);
//                            DatabaseLogger.log(msg);
//                        }
//                    }
//                }
//
//                // Проверка отключений
//                for (String id : previousDrives) {
//                    if (!currentDrives.contains(id)) {
//                        String msg = "USB disconnected: " + id;
//                        System.out.println(msg);
//                        DatabaseLogger.log(msg);
//                    }
//                }
//
//                previousDrives = currentDrives;
//
//                // Проверка каждую секунду
//                Thread.sleep(1000);
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

//    @Override
//    public void run() {
//        // Сохраняем предыдущий набор USB устройств
//        NTPTimeService ntpTimeService = new NTPTimeService(WriteIniFile.readTimeServerHost());
//        if (ntpTimeService.getNtpHost() == null || ntpTimeService.getNtpHost().isEmpty()) {
//            return;
//        }
//
//        Set<String> previousDevices = new HashSet<>();
//
//        while (true) {
//            try {
//                // Каждый цикл создаём новый SystemInfo и HAL, чтобы OSHI делал свежий запрос
////                SystemInfo si = new SystemInfo();
////                HardwareAbstractionLayer hal = si.getHardware();
////
////                OperatingSystem system = si.getOperatingSystem();
////                List<OSFileStore> stores = system.getFileSystem().getFileStores();
////
////                List<UsbDevice> usbDevices = hal.getUsbDevices(true); // рекурсивно все устройства
//                Set<String> currentDevices = new HashSet<>();
//                findUsbDevice(currentDevices);
////
////                for (UsbDevice d : usbDevices) {
////                    collectDevices(d, currentDevices);
////                }
//                    // Корректная кодировка
////                    String name = d.getName() != null ? d.getName() : "Unknown";
////                    String vendor = d.getVendor() != null ? d.getVendor() : "Unknown";
////                    String serialNumber = d.getSerialNumber();
////                    String productId = d.getProductId();
////                    String uniqueDeviceId = d.getUniqueDeviceId();
////
////
////                    String id = name + " / " + vendor + " / " + serialNumber + " / " + productId + " / " + uniqueDeviceId;
////                    currentDevices.add(id);
////
////                    // Новое устройство
////                    if (!previousDevices.contains(id)) {
////                        String msg = "USB connected: " + id;
////                        System.out.println(msg);
////                        //DatabaseLogger.log(msg);
////                    }
//
//
////                for (OSFileStore fs : stores) {
////                    String name = fs.getName() != null ? fs.getName() : "Unknown";
////                    String description = fs.getDescription() != null ? fs.getDescription() : "Unknown";
////                    String type = fs.getType();
////                    String label = fs.getLabel();
////                    String mount = fs.getMount();
////                    String options = fs.getOptions();
////                    String logicalVolume = fs.getLogicalVolume();
////                    long freeInodes = fs.getFreeInodes();
////                    long freeSpace = fs.getFreeSpace();
////                    long totalInodes = fs.getTotalInodes();
////                    long totalSpace = fs.getTotalSpace();
////                    long usableSpace = fs.getUsableSpace();
////                    String uuid = fs.getUUID();
////                    String volume = fs.getVolume();
////
////                    String id = name + " / " + description + " / " + type + " / " + label + " / " + mount + " / "
////                            + options + " / " + logicalVolume + " / " + freeInodes + " / " + freeSpace
////                            + " / " + totalInodes + " / " + totalSpace + " / " + usableSpace + " / "
////                            + uuid + " / " + volume;
////
////                    //Removable drive CD-ROM
////                    if(description.equals("Removable drive") || description.equals("CD-ROM")) {
////                        currentDevices.add(id);
////                        // Новое устройство
////                        if (!previousDevices.contains(id)) {
////                            String msg = "USB connected: " + id;
////                            System.out.println(msg);
////                           // DatabaseLogger.log(msg);
////                        }
////                    }
////                }
//
////                //TODO: добавить перебор коллекции. Проверка новое ли устройство
////
//                for (String id : currentDevices) {
//                    if(!previousDevices.contains(id)) {
//                        //TODO: логировать событие
//                        System.out.println("USB connected: " + id);
//                    }
//                }
//
//                // Проверка отключений
//                for (String id : previousDevices) {
//                    if (!currentDevices.contains(id)) {
//                        //TODO: логировать событие
//                        String msg = "USB disconnected: " + id;
//                        System.out.println(msg);
//                        //DatabaseLogger.log(msg);
//                    }
//                }
//
//                previousDevices = currentDevices;
////                for (String id : previousDevices) {
////                    System.out.println(id);
////                }
////                System.out.println("\n=======================================\n");
//
//                // Проверяем каждую секунду
//                Thread.sleep(1000);
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }


//    @Override
//    public void run() {
//        System.out.println("USB Monitor started via OSHI");
//
//        SystemInfo si = new SystemInfo();
//        HardwareAbstractionLayer hal = si.getHardware();
//
//        Set<String> previous = new HashSet<>();
//
//        while (true) {
//            try {
//                Set<String> current = new HashSet<>();
//                List<UsbDevice> usbDevices = hal.getUsbDevices(true);
//
//                for (UsbDevice d : usbDevices) {
//                    String id = d.getName() + " / " + d.getVendor();
//                    current.add(id);
//                    if (!previous.contains(id)) {
//                        String msg = "USB connected: " + id;
//                        System.out.println(msg);
//                        DatabaseLogger.log(msg);
//                    }
//                }
//
//                for (String id : previous) {
//                    if (!current.contains(id)) {
//                        String msg = "USB disconnected: " + id;
//                        System.out.println(msg);
//                        DatabaseLogger.log(msg);
//                    }
//                }
//
//                previous = current;
//
//                Thread.sleep(1000);
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }


//    @Override
//    public void run() {
//        System.out.println("USB Monitor started");
//
//        User32 user32 = User32.INSTANCE;
//
//        WinDef.HWND hwnd = user32.CreateWindowEx(0, "STATIC", "USBMonitorWindow",
//                0, 0, 0, 0, 0,
//                null, null, null, null);
//
//        DBT.DEV_BROADCAST_DEVICEINTERFACE notification = new DBT.DEV_BROADCAST_DEVICEINTERFACE();
//        notification.dbcc_size = notification.size();
//        notification.dbcc_devicetype = DBT_DEVTYP_DEVICEINTERFACE;
//
//        WinUser.HDEVNOTIFY hDevNotify = User32.INSTANCE.RegisterDeviceNotification(hwnd,
//                notification, DEVICE_NOTIFY_WINDOW_HANDLE);
//
//        WinUser.MSG msg = new WinUser.MSG();
//        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
//            if (msg.message == WinUser.WM_DEVICECHANGE) {
//                int eventType = msg.wParam.intValue();
//                if (eventType == DBT_DEVICEARRIVAL) {
//                    System.out.println("USB connected");
//                    DatabaseLogger.log("USB connected");
//                } else if (eventType == DBT_DEVICEREMOVECOMPLETE) {
//                    System.out.println("USB disconnected");
//                    DatabaseLogger.log("USB disconnected");
//                }
//            }
//            User32.INSTANCE.TranslateMessage(msg);
//            User32.INSTANCE.DispatchMessage(msg);
//        }
//
//        User32.INSTANCE.UnregisterDeviceNotification(hDevNotify);
//    }
//}
