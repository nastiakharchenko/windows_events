package org.windows_events.scheduler;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.monitor.TimeMonitorService;
import org.windows_events.time.NTPTimeService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.windows_events.constants.Constants.DELAY;

public class TimeMonitorScheduler {
    private DurableSeqLogger durableLogger;
    private ScheduledExecutorService schedulerChangeTime;

    public TimeMonitorScheduler(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    public void run(String hostNTP) {
        schedulerChangeTime = Executors.newScheduledThreadPool(1);
        NTPTimeService ntpTimeService = new NTPTimeService(hostNTP);
        TimeMonitorService timeMonitorService = new TimeMonitorService();

        Runnable hourlyTask = () -> {
            ExecutorService threadPool = Executors.newFixedThreadPool(1);

            long ntpTime = 0;
            try {
                //TODO: что делать, если сервер недоступен?
                ntpTime = ntpTimeService.getNTPTime().getTime();
            } catch (Exception e) {
                System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
                return;
                //throw new RuntimeException(e);
            }
            long systemTime = System.currentTimeMillis();

            long diff = Math.abs(systemTime - ntpTime);
            if (diff > 30000) { // если разница больше 30 секунд
                timeMonitorService.setTimePC(systemTime, ntpTime, durableLogger);
            }

            threadPool.shutdown();
        };

        schedulerChangeTime.scheduleWithFixedDelay(hourlyTask, 0, DELAY, TimeUnit.MINUTES);
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
