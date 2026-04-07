package org.windows_events.service.monitor;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.windows_events.constants.Constants.*;
import static org.windows_events.constants.Constants.IDENTIFIER_PC;

public class TimeMonitorService {

    public TimeMonitorService() {}

    public void setTimePC(long systemTime, long ntpTime, DurableSeqLogger durableLogger){
        //UserMonitorService userMonitorService = new UserMonitorService();
        String activeUser = UserMonitorService.getActiveUser();
        try{
            String command = String.format(
                    "powershell -Command \"Set-Date -Date '%s'\"",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new Date(ntpTime))
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                durableLogger.log(IDENTIFIER_PROGRAM +
                        String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                        + String.format(CHANGE_TIME_PC, DateFormatter.dateConvertString(systemTime)
                        , DateFormatter.dateConvertString(ntpTime)));
            } else {
                durableLogger.log(IDENTIFIER_PROGRAM +
                        String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                        + String.format(ERROR_EXIT_CODE + exitCode));
            }

        } catch (IOException | InterruptedException e) {
            durableLogger.log(IDENTIFIER_PROGRAM +
                    String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName(), DataPCMonitorService.getIpAddress(), activeUser)
                    + "Error: " + e.getMessage());
        }
    }
}
