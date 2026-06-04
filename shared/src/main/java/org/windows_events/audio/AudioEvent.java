package org.windows_events.audio;

import lombok.Getter;

@Getter
public final class AudioEvent {

    private String processName;
    private int pid;
    private String action;
    private boolean volumeRestoredToMax;
    private float currentVolume;
    private long timestamp;

    public AudioEvent() {
    }

    public AudioEvent(
            String processName,
            int pid,
            String action,
            boolean volumeRestoredToMax,
            float currentVolume,
            long timestamp
    ) {
        this.processName = processName;
        this.pid = pid;
        this.action = action;
        this.volumeRestoredToMax = volumeRestoredToMax;
        this.currentVolume = currentVolume;
        this.timestamp = timestamp;
    }
}
