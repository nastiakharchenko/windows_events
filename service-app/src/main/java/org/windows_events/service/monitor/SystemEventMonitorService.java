package org.windows_events.service.monitor;

import com.sun.jna.platform.win32.*;
import java.util.HashMap;

import static org.windows_events.constants.Constants.*;

public class SystemEventMonitorService {

    public SystemEventMonitorService() {}

    public HashMap<Long, String> lastTimeShutdown() {
        HashMap<Long, String> result = new HashMap<>();
        try {
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

        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }
}
