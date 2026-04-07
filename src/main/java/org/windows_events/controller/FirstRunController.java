package org.windows_events.controller;

import org.windows_events.service.monitor.SystemEventMonitorService;
import org.windows_events.service.monitor.USBMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.file.InitializeDataFile;
import org.windows_events.file.TimeShutdownFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.DateFormatter;
import org.windows_events.time.NTPTimeService;

import java.util.*;

import static org.windows_events.constants.Constants.*;

public class FirstRunController {
    private final DurableSeqLogger durableLogger;

    public FirstRunController(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    public Set<String> firstRunService(){
        InitializeDataFile initializeDataFile = new InitializeDataFile();
        if(!initializeDataFile.isFile()){
            Set<String> currentDevices = new HashSet<>();
            USBMonitorService usbMonitorService = new USBMonitorService();
            usbMonitorService.findUsbDevice(currentDevices);
            initializeDataFile.writeToFile(currentDevices);
            return currentDevices;
        } else{
            return initializeDataFile.readFromFile();
        }
    }

    public void loggingStartupAndShutdown(String hostNtp) throws Exception {
//        UserMonitorService userMonitorService = new UserMonitorService();
        String activeUser = UserMonitorService.getActiveUser();

        NTPTimeService ntpTimeService = new NTPTimeService(hostNtp);
        Date dateNow = ntpTimeService.getNTPTime();

        SystemEventMonitorService systemEventMonitorService = new SystemEventMonitorService();
        HashMap<Long, String> datePC = systemEventMonitorService.lastTimeShutdown();
        Iterator<HashMap.Entry<Long, String>> iterator = datePC.entrySet().iterator();
        long dateShutdownPCL = iterator.next().getKey();
        String textShutdown = datePC.get(dateShutdownPCL);

        TimeShutdownFile timeShutdownFile = new TimeShutdownFile();
        long dateFile = timeShutdownFile.readDateFromFile();

        if(dateFile == 0L || dateFile <= dateShutdownPCL){
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                    + STOP_SERVICE + DateFormatter.dateConvertString(dateShutdownPCL) + textShutdown);
        } else {
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                    + STOP_SERVICE + DateFormatter.dateConvertString(dateFile));
        }

        durableLogger.log(IDENTIFIER_PROGRAM +
                String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                + START_SERVICE + DateFormatter.dateConvert(dateNow)
        );
    }
}
