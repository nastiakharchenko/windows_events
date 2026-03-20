package org.windows_events.controller;

import org.windows_events.controller.monitor.SystemEventMonitor;
import org.windows_events.controller.monitor.USBMonitor;
import org.windows_events.file.InitializeDataFile;
import org.windows_events.file.TimeShutdownFile;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DataPC;
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
            USBMonitor usbMonitor = new USBMonitor(durableLogger);
            usbMonitor.findUsbDevice(currentDevices);
            initializeDataFile.writeToFile(currentDevices);
            return currentDevices;
        } else{
            return initializeDataFile.readFromFile();
        }
    }

    public void loggingStartupAndShutdown(String hostNtp) throws Exception {
        NTPTimeService ntpTimeService = new NTPTimeService(hostNtp);
        Date dateNow = ntpTimeService.getNTPTime();

        SystemEventMonitor systemEventMonitor = new SystemEventMonitor();
        HashMap<Long, String> datePC = systemEventMonitor.lastTimeShutdown();
        Iterator<HashMap.Entry<Long, String>> iterator = datePC.entrySet().iterator();
//        long dateStartupPCL = iterator.next().getKey();
        long dateShutdownPCL = iterator.next().getKey();
        String textShutdown = datePC.get(dateShutdownPCL);

        TimeShutdownFile timeShutdownFile = new TimeShutdownFile();
        long dateFile = timeShutdownFile.readDateFromFile();

        if(dateFile == 0L || dateFile <= dateShutdownPCL){
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                    + STOP_SERVICE + DateFormatter.dateConvertString(dateShutdownPCL) + textShutdown);
        } else {
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                    + STOP_SERVICE + DateFormatter.dateConvertString(dateFile));
        }

        durableLogger.log(IDENTIFIER_PROGRAM +
                String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                + START_SERVICE + DateFormatter.dateConvert(dateNow)
        );

//        if(dateStartupPCL != 0 && dateNow.getTime() <= dateStartupPCL){
//            durableLogger.log(START_SERVICE + DateFormatter.dateConvertString(dateStartupPCL));
//        } else{
//            durableLogger.log(START_SERVICE + DateFormatter.dateConvert(dateNow));
//        }
    }
}
