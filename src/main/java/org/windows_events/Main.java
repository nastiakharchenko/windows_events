package org.windows_events;

import org.windows_events.constants.Constants;
import org.windows_events.controller.CheckServicesNeedScheduler;
import org.windows_events.controller.FirstRunController;
import org.windows_events.scheduler.TimeMonitorScheduler;
import org.windows_events.file.ConfigurationFile;
import org.windows_events.file.TimeShutdownFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.scheduler.USBMonitorScheduler;

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


        //---------------------------------------

        FirstRunController controller = new FirstRunController(durableLogger);
        controller.loggingStartupAndShutdown(hostNtp);

        Set<String> currentUsbDevice = controller.firstRunService();
        USBMonitorScheduler monitor = new USBMonitorScheduler(durableLogger);
        monitor.setPreviousDevices(currentUsbDevice);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(monitor);

        TimeMonitorScheduler timeMonitorScheduler = new TimeMonitorScheduler(durableLogger);
        timeMonitorScheduler.run(hostNtp);

        CheckServicesNeedScheduler checkServicesNeedScheduler = new CheckServicesNeedScheduler(durableLogger, hostNtp);
        checkServicesNeedScheduler.run();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TimeShutdownFile timeShutdownFile = new TimeShutdownFile();
            try {
                timeShutdownFile.writeDateToFile(System.currentTimeMillis());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }
}