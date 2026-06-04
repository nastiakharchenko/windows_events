package org.windows_events.numlock;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NumLockEvent {
    private String eventType;
    private String username;
    private String numLockState;
    private String windowTitle;
    private String processName;
    private int pid;
    private long timestamp;
    private String host;
    private String message;

    public NumLockEvent() {
    }

    public NumLockEvent(
            String eventType,
            String username,
            String numLockState,
            String windowTitle,
            String processName,
            int pid,
            long timestamp,
            String host,
            String message
    ) {
        this.eventType = eventType;
        this.username = username;
        this.numLockState = numLockState;
        this.windowTitle = windowTitle;
        this.processName = processName;
        this.pid = pid;
        this.timestamp = timestamp;
        this.host = host;
        this.message = message;
    }
}
