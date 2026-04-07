package org.windows_events.scheduler;

import lombok.Setter;
import org.windows_events.constants.Constants;
import org.windows_events.file.ConfigurationFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.DateFormatter;
import org.windows_events.service.monitor.USBMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.time.NTPTimeService;

import java.util.HashSet;
import java.util.Set;

import static org.windows_events.constants.Constants.*;

@Setter
public class USBMonitorScheduler implements Runnable {

    private Set<String> previousDevices;
    private DurableSeqLogger durableLogger;

    public USBMonitorScheduler(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    @Override
    public void run() {
        USBMonitorService usbMonitorService = new USBMonitorService();
        NTPTimeService ntpTimeService = new NTPTimeService(ConfigurationFile.readTimeServerHost());
        if (ntpTimeService.getNtpHost() == null || ntpTimeService.getNtpHost().isEmpty()) {
            System.err.println(Class.class.getSimpleName() + ": " + ERROR_NTP_HOST);
            return;
        }
        if (previousDevices == null || previousDevices.isEmpty()) {
            previousDevices = new HashSet<>();
        }

        while (true) {
            try {
                Set<String> currentDevices = new HashSet<>();

                usbMonitorService.findUsbDevice(currentDevices);

                for (String id : currentDevices) {
                    if (!previousDevices.contains(id)) {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                        , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                                + Constants.USB_CONNECTED + "\t"
                                + DateFormatter.dateConvert(ntpTimeService.getNTPTime()) + "\t"
                                + id);
                    }
                }

                for (String id : previousDevices) {
                    if (!currentDevices.contains(id)) {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                        , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
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
}
