package org.windows_events.controller.monitor;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DataPC;
import org.windows_events.service.DateFormatter;
import org.windows_events.time.NTPTimeService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.windows_events.constants.Constants.*;

public class TimeMonitor {
    private DurableSeqLogger durableLogger;
    private ScheduledExecutorService schedulerChangeTime;

    public TimeMonitor(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    public void start(String hostNTP) {
        schedulerChangeTime = Executors.newScheduledThreadPool(1);
        NTPTimeService ntpTimeService = new NTPTimeService(hostNTP);

        Runnable hourlyTask = () -> {
            ExecutorService threadPool = Executors.newFixedThreadPool(1);

            long ntpTime = 0;
            try {
                ntpTime = ntpTimeService.getNTPTime().getTime();
            } catch (Exception e) {
                System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            long systemTime = System.currentTimeMillis();

            long diff = Math.abs(systemTime - ntpTime);
            if (diff > 30000) { // если разница больше 30 секунд
//                String dateStr = String.format("%tF", ntpTime);
//                String timeStr = String.format("%tT", ntpTime);
                try {
//                    // Установка даты
//                    ProcessBuilder datePb = new ProcessBuilder("cmd", "/c", "date", dateStr);
//                    Process dateProcess = datePb.start();
//                    int dateExit = dateProcess.waitFor();
//
//                    // Установка времени
//                    ProcessBuilder timePb = new ProcessBuilder("cmd", "/c", "time", timeStr);
//                    Process timeProcess = timePb.start();
//                    int timeExit = timeProcess.waitFor();

//                    if (dateExit == 0 && timeExit == 0) {
//                        durableLogger.log(String.format(
//                                CHANGE_TIME_PC,
//                                DateFormatter.dateConvertString(systemTime),
//                                DateFormatter.dateConvertString(ntpTime)
//                        ));
//                    } else {
//                        durableLogger.log(ERROR_EXIT_CODE + "дата=" + dateExit + ", час=" + timeExit);
//                    }

                    String command = String.format(
                            "powershell -Command \"Set-Date -Date '%s'\"",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                    .format(new Date(ntpTime))
                    );

                    Process process = Runtime.getRuntime().exec(command);
                    int exitCode = process.waitFor();

                    if (exitCode == 0) {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                                + String.format(CHANGE_TIME_PC, DateFormatter.dateConvertString(systemTime)
                                , DateFormatter.dateConvertString(ntpTime)));
                    } else {
                        durableLogger.log(IDENTIFIER_PROGRAM +
                                String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                                + String.format(ERROR_EXIT_CODE + exitCode));
                    }


//                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "time", timeStr);
//                    Process process = pb.start();
//                    int exitCode = process.waitFor();
//                    if (exitCode == 0) {
//                        durableLogger.log(String.format(CHANGE_TIME_PC, DateFormatter.dateConvertString(systemTime)
//                                , DateFormatter.dateConvertString(ntpTime)));
//                    } else {
//                        durableLogger.log(String.format(ERROR_EXIT_CODE, exitCode));
//                    }
                } catch (IOException | InterruptedException e) {
                    durableLogger.log(IDENTIFIER_PROGRAM +
                            String.format(IDENTIFIER_PC, DataPC.getHostName(), DataPC.getIpAddress())
                            + "Error: " + e.getMessage());
                }
            }

            threadPool.shutdown();
        };

        schedulerChangeTime.scheduleWithFixedDelay(hourlyTask, 0, 30, TimeUnit.MINUTES);
    }

    public void stop() {
        if (schedulerChangeTime != null && !schedulerChangeTime.isShutdown()) {
            schedulerChangeTime.shutdownNow();
            try {
                if (!schedulerChangeTime.awaitTermination(5, TimeUnit.SECONDS)) {
                    schedulerChangeTime.shutdownNow();
                }
            } catch (InterruptedException e) {
                schedulerChangeTime.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
