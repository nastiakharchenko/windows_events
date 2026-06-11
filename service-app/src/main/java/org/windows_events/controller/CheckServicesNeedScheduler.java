package org.windows_events.controller;

import lombok.Getter;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
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
    @Getter
    private Map<String, Boolean> mapSeq;

    public CheckServicesNeedScheduler(DurableSeqLogger durableLogger, String hostNtp) {
        this.durableLogger = durableLogger;
        this.hostNtp = hostNtp;
        mapSeq = ConfigurationFile.readModeListenServices();
    }

    public void run() {
        scheduler = Executors.newScheduledThreadPool(1);
//        Map<String, Boolean> mapSeq = ConfigurationFile.readModeListenServices();

        Runnable hourlyTask = () -> {
            ExecutorService threadPool = Executors.newFixedThreadPool(1);
            try {
                //check(mapSeq);
                check();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            threadPool.shutdown();
        };
        scheduler.scheduleWithFixedDelay(hourlyTask, 0, DELAY, TimeUnit.MINUTES);
    }

    private void check() throws Exception {//Map<String, Boolean> mapSeq) throws Exception {
        NTPTimeService ntp = new NTPTimeService(hostNtp);
        boolean access = true;

        Date date = ntp.getNTPTime();
        if(date == null){
            date = Date.from(Instant.now());
            access = false;
        }

        if(mapSeq.get(KEYBOARD_HOOK_SERVICE)){
            KeyboardHookMonitorService keyboardHookMonitorService = new KeyboardHookMonitorService();
            keyboardHookMonitorService.check(durableLogger, hostNtp);
        }
        if(mapSeq.get(CHECK_AUTO_UPDATE_TIME)){
            WinTimeConversionSettingsMonitorService service = new WinTimeConversionSettingsMonitorService();
            if( ! service.isAutoTimeAdjustmentEnabled()){
                service.enableAutoTimeAdjustment(false, "time.windows.com,0x9");
                if (service.statusRequest){
//                    durableLogger.log(IDENTIFIER_PROGRAM +
//                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                            + SET_AUTOUPDATE_TIME
//                            + DateFormatter.dateConvert(date));

                    StringBuilder str = new StringBuilder(IDENTIFIER_PROGRAM +
                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                            + SET_AUTOUPDATE_TIME
                            + DateFormatter.dateConvert(date));

                    if(!access){
                        str.append(TIME_PC);
                    }
                    durableLogger.log(str.toString());
                }
            }
        }
        if(mapSeq.get(CHECK_DAYLIGHT_SAVE_TIME)){
            WinTimeConversionSettingsMonitorService service = new WinTimeConversionSettingsMonitorService();
            if( ! service.isDaylightSavingEnabled()){
                service.enableDaylightSaving();
                if (service.statusRequest){
//                    durableLogger.log(IDENTIFIER_PROGRAM +
//                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                            + SET_DAYLIGHT_SAVE_TIME
//                            + DateFormatter.dateConvert(ntp.getNTPTime()));

                    StringBuilder str = new StringBuilder(IDENTIFIER_PROGRAM +
                            String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                    , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                            + SET_DAYLIGHT_SAVE_TIME
                            + DateFormatter.dateConvert(date));
                    if(!access){
                        str.append(TIME_PC);
                    }
                    durableLogger.log(str.toString());
                }
            }
        }
        if(mapSeq.get(ALERT_100_MBPS)){
            NetworkConnectionSpeedMonitorService networkConnectionSpeedMonitorService = new NetworkConnectionSpeedMonitorService(durableLogger);
            networkConnectionSpeedMonitorService.findAdaptersWithLowSpeed(hostNtp);
        }
    }
}
