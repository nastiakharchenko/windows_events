package org.windows_events.service.monitor.services;

import org.windows_events.logger.DurableSeqLogger;

import static org.windows_events.constants.Constants.W32_TIME_SERVICE;

public class W32TimeMonitorService {

    public W32TimeMonitorService() {
    }

    public void check(DurableSeqLogger durableLogger, String hostNtp) throws Exception {
        CheckStartServices checkStartServices = new CheckStartServices(durableLogger, hostNtp);

        if(!checkStartServices.isServiceRunning(W32_TIME_SERVICE)){
            checkStartServices.startService(W32_TIME_SERVICE);
        }
        if(!checkStartServices.isServiceAutoStart(W32_TIME_SERVICE)){
            checkStartServices.setServiceAutoStart(W32_TIME_SERVICE);
        }
    }
}
