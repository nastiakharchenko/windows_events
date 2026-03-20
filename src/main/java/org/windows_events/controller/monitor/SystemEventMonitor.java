package org.windows_events.controller.monitor;

import com.sun.jna.platform.win32.*;
import java.util.HashMap;

import static org.windows_events.constants.Constants.*;

public class SystemEventMonitor { //} implements Runnable {
    public HashMap<Long, String> lastTimeShutdown() {
        HashMap<Long, String> result = new HashMap<>();
        try {
//            long lastStartupTime = 0;
            long lastRecordNumber = 0;
            long lastShutdownTime = 0;
            int lastShutdownEventID = 0;
            String shutdownType = "";

            Advapi32Util.EventLogIterator iter = new Advapi32Util.EventLogIterator("System");

            while (iter.hasNext()) {
                Advapi32Util.EventLogRecord record = iter.next();
                long recordNumber = record.getRecord().RecordNumber.longValue();
                if (recordNumber > lastRecordNumber)
                    lastRecordNumber = recordNumber;

                int eventID = record.getRecord().EventID.intValue() & 0xFFFF;
                WinDef.DWORD timeGenerated = record.getRecord().TimeGenerated;
                long eventTime = Integer.toUnsignedLong(timeGenerated.intValue()) * 1000;

                switch (eventID) {
//                    case 6005:
//                        if (eventTime > lastStartupTime) {
//                            lastStartupTime = eventTime;
//                        }
//                        break;
                    case 1074:
                    case 6008:
                        if (eventTime > lastShutdownTime) {
                            lastShutdownTime = eventTime;
                            lastShutdownEventID = eventID;
                        }
                        break;
                }

                if (lastShutdownTime > 0) {
                    switch (lastShutdownEventID) {
                        case 1074: shutdownType = CODE_1074; break;
                        case 6008: shutdownType = CODE_6008; break;
                    }
                }
            }
            result.put(lastShutdownTime, shutdownType);
//            result.put(lastStartupTime, CODE_6005);

        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

//    private volatile boolean running = true;
//
//    @Override
//    public void run() {
//        System.out.println("System Event Monitor started");
//
//        try {
//            long lastRecordNumber = 0;
//            long lastStartupTime = 0;    // для 6005
//            long lastShutdownTime = 0;   // для 6006,6008,1074
//            int lastShutdownEventID = 0; // чтобы знать тип shutdown
//
//            // --- 1. Определяем последние события при старте ---
//            Advapi32Util.EventLogIterator iter = new Advapi32Util.EventLogIterator("System");
//
//            while (iter.hasNext()) {
//                Advapi32Util.EventLogRecord record = iter.next();
//                long recordNumber = record.getRecord().RecordNumber.longValue();
//                if (recordNumber > lastRecordNumber) lastRecordNumber = recordNumber;
//
//                int eventID = record.getRecord().EventID.intValue() & 0xFFFF;
//
//                // Конвертируем DWORD -> long миллисекунды -> Date
//                WinDef.DWORD timeGenerated = record.getRecord().TimeGenerated;
//                long eventTime = Integer.toUnsignedLong(timeGenerated.intValue()) * 1000;
//
//                switch (eventID) {
//                    case 6005: // startup
//                        if (eventTime > lastStartupTime) lastStartupTime = eventTime;
//                        break;
//                    case 6006: // shutdown
//                    case 6008: // unexpected shutdown
//                    case 1074: // shutdown by user/process
//                        if (eventTime > lastShutdownTime) {
//                            lastShutdownTime = eventTime;
//                            lastShutdownEventID = eventID;
//                        }
//                        break;
//                }
//            }
//
//            // --- 2. Логируем последнее включение ПК ---
//            if (lastStartupTime > 0) {
//                Log.information("Last system startup detected: " + new Date(lastStartupTime));
//            }
//
//            // --- 3. Логируем последнее выключение ПК ---
//            if (lastShutdownTime > 0) {
//                String shutdownType;
//                switch (lastShutdownEventID) {
//                    case 6006: shutdownType = "System shutdown"; break;
//                    case 6008: shutdownType = "Unexpected shutdown"; break;
//                    case 1074: shutdownType = "Shutdown by user/process"; break;
//                    default: shutdownType = "Shutdown"; break;
//                }
//                Log.information("Last system shutdown detected: " + shutdownType + " " +
//                        new Date(lastShutdownTime));
//            }
//
//            // --- 4. Дальше мониторим новые события ---
//            while (running) {
//                Advapi32Util.EventLogIterator monitorIter = new Advapi32Util.EventLogIterator("System");
//
//                while (monitorIter.hasNext()) {
//                    Advapi32Util.EventLogRecord record = monitorIter.next();
//                    long recordNumber = record.getRecord().RecordNumber.longValue();
//                    if (recordNumber <= lastRecordNumber) continue;
//                    lastRecordNumber = recordNumber;
//
//                    int eventID = record.getRecord().EventID.intValue() & 0xFFFF;
//                    WinDef.DWORD timeGenerated = record.getRecord().TimeGenerated;
//                    long eventTime = Integer.toUnsignedLong(timeGenerated.intValue()) * 1000;
//                    Date eventDate = new Date(eventTime);
//
//                    processEvent(eventID, eventDate);
//                }
//
//                Thread.sleep(5000); // проверка каждые 5 секунд
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void processEvent(int eventID, Date eventDate) {
//        String time = eventDate.toString();
//
//        switch (eventID) {
//            case 6005:
//                Log.information("System startup detected " + time);
//                break;
//            case 6006:
//                Log.information("System shutdown detected " + time);
//                break;
//            case 6008:
//                Log.information("Unexpected shutdown detected " + time);
//                break;
//            case 1074:
//                Log.information("Shutdown initiated by user/process " + time);
//                break;
//        }
//    }
}
