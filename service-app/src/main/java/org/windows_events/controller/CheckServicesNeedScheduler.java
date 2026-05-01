package org.windows_events.controller;

import org.windows_events.constants.Constants;
import org.windows_events.file.ConfigurationFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.monitor.NetworkConnectionSpeedMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.service.monitor.WinTimeConversionSettingsMonitorService;
import org.windows_events.service.monitor.services.KeyboardHookMonitorService;
import org.windows_events.time.NTPTimeService;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.windows_events.constants.Constants.*;

public class CheckServicesNeedScheduler {
    private ScheduledExecutorService scheduler;
    private DurableSeqLogger durableLogger;
    private String hostNtp;

    public CheckServicesNeedScheduler(DurableSeqLogger durableLogger, String hostNtp) {
        this.durableLogger = durableLogger;
        this.hostNtp = hostNtp;
    }

    public void run() {
        scheduler = Executors.newScheduledThreadPool(1);
        Map<String, Boolean> mapSeq = ConfigurationFile.readModeListenServices();

        Runnable hourlyTask = () -> {
            ExecutorService threadPool = Executors.newFixedThreadPool(1);
            try {
                check(mapSeq);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            threadPool.shutdown();
        };
        scheduler.scheduleWithFixedDelay(hourlyTask, 0, DELAY, TimeUnit.MINUTES);
    }

    private void check(Map<String, Boolean> mapSeq) throws Exception {
        if(mapSeq.get(Constants.KEYBOARD_HOOK_SERVICE)){
            KeyboardHookMonitorService keyboardHookMonitorService = new KeyboardHookMonitorService();
            keyboardHookMonitorService.check(durableLogger, hostNtp);
        }
        if(mapSeq.get(Constants.CHECK_AUTO_UPDATE_TIME)){
            WinTimeConversionSettingsMonitorService service = new WinTimeConversionSettingsMonitorService();
            if( ! service.isAutoTimeAdjustmentEnabled()){
                service.enableAutoTimeAdjustment(false, "time.windows.com,0x9");
                if (service.statusRequest){
                    NTPTimeService ntp = new NTPTimeService(hostNtp);
                    durableLogger.log(IDENTIFIER_PROGRAM +
                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                            + SET_AUTOUPDATE_TIME
                            + DateFormatter.dateConvert(ntp.getNTPTime()));
                }
            }
        }
        if(mapSeq.get(Constants.CHECK_DAYLIGHT_SAVE_TIME)){
            WinTimeConversionSettingsMonitorService service = new WinTimeConversionSettingsMonitorService();
            if( ! service.isDaylightSavingEnabled()){
                service.enableDaylightSaving();
                if (service.statusRequest){
                    NTPTimeService ntp = new NTPTimeService(hostNtp);
                    durableLogger.log(IDENTIFIER_PROGRAM +
                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                            + SET_DAYLIGHT_SAVE_TIME
                            + DateFormatter.dateConvert(ntp.getNTPTime()));
                }
            }
        }
        if(mapSeq.get(Constants.ALERT_100_MBPS)){
            NetworkConnectionSpeedMonitorService networkConnectionSpeedMonitorService = new NetworkConnectionSpeedMonitorService(durableLogger);
            networkConnectionSpeedMonitorService.findAdaptersWithLowSpeed(hostNtp);
        }
//        if(mapSeq.get(Constants.CHECK_NUMLOCK)){
//
//        }
    }
}
