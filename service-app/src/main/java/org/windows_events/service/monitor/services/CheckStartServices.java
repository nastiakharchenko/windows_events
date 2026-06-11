package org.windows_events.service.monitor.services;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Date;
import java.util.SplittableRandom;

import static org.windows_events.constants.Constants.*;

public class CheckStartServices {

    private DurableSeqLogger durableLogger;
    private NTPTimeService ntpTimeService;

    public CheckStartServices(DurableSeqLogger durableLogger, String hostNtp) {
        this.durableLogger = durableLogger;
        ntpTimeService = new NTPTimeService(hostNtp);
    }

    /**
     * Проверка: служба запущена
     */
    public boolean isServiceRunning(String serviceName) {
        try {
            Process process = Runtime.getRuntime().exec("sc query " + serviceName);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("RUNNING")) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Service check error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Запустить службу
     */
    public void startService(String serviceName) throws Exception {
        if(executeCommand("sc start " + serviceName)){
            Date dateNow = ntpTimeService.getNTPTime();
            boolean access = true;
            if(dateNow == null){
                dateNow = Date.from(Instant.now());
                access = false;
            }
            StringBuilder str = new StringBuilder(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                    + String.format(RUN_SERVICE, serviceName)
                    + DateFormatter.dateConvert(dateNow));
            if(!access){
                str.append(TIME_PC);
            }
            durableLogger.log(str.toString());

//            durableLogger.log(IDENTIFIER_PROGRAM +
//                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                    + String.format(RUN_SERVICE, serviceName)
//                    + DateFormatter.dateConvert(dateNow));
        }
    }

    /**
     * Проверка: автозапуск службы
     */
    public boolean isServiceAutoStart(String serviceName) {
        try {
            Process process = Runtime.getRuntime().exec("sc qc " + serviceName);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("AUTO_START")) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("AutoStart check error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Включить автозапуск службы
     */
    public void setServiceAutoStart(String serviceName) throws Exception {
        if(executeCommand("sc config " + serviceName + " start= auto")){
            Date dateNow = ntpTimeService.getNTPTime();
            boolean access = true;
            if(dateNow == null){
                dateNow = Date.from(Instant.now());
                access = false;
            }
            StringBuilder str = new StringBuilder(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                    + String.format(AUTOSTART_SERVICE, serviceName) + DateFormatter.dateConvert(dateNow));
            if(!access){
                str.append(TIME_PC);
            }
            durableLogger.log(str.toString());


//            durableLogger.log(IDENTIFIER_PROGRAM +
//                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
//                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
//                    + String.format(AUTOSTART_SERVICE, serviceName) + DateFormatter.dateConvert(dateNow));
        }
    }

    private boolean executeCommand(String command){
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("Command error: " + e.getMessage());
        }
        return false;
    }
}
