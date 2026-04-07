package org.windows_events.service.monitor;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.UsbDevice;

import java.util.List;
import java.util.Set;

public class USBMonitorService {

    public USBMonitorService() {}

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
