package org.windows_events;

import org.windows_events.constants.Constants;
import org.windows_events.controller.FirstRunController;
import org.windows_events.controller.monitor.TimeMonitor;
import org.windows_events.file.ConfigurationFile;
import org.windows_events.file.TimeShutdownFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.controller.monitor.USBMonitor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws Exception {
        Map<String, String> mapSeq = ConfigurationFile.readConfigurationSeq();
        String hostNtp = ConfigurationFile.readTimeServerHost();

        DurableSeqLogger durableLogger =
                new DurableSeqLogger(
                        mapSeq.get(Constants.URL),
                        mapSeq.get(Constants.KEY),
                        "seq-buffer.log"
                );

        FirstRunController controller = new FirstRunController(durableLogger);
        controller.loggingStartupAndShutdown(hostNtp);

        Set<String> currentUsbDevice = controller.firstRunService();
        USBMonitor monitor = new USBMonitor(durableLogger);
        monitor.setPreviousDevices(currentUsbDevice);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TimeShutdownFile timeShutdownFile = new TimeShutdownFile();
            try {
                timeShutdownFile.writeDateToFile(System.currentTimeMillis());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(monitor);

        TimeMonitor timeMonitor = new TimeMonitor(durableLogger);
        timeMonitor.start(hostNtp);
    }
}