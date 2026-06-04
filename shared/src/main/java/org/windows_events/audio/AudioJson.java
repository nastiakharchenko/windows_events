package org.windows_events.audio;

public final class AudioJson {

    private AudioJson() {
    }

    public static String toJson(AudioEvent event) {
        return "{"
                + "\"processName\":\"" + escape(event.getProcessName()) + "\","
                + "\"pid\":" + event.getPid() + ","
                + "\"action\":\"" + escape(event.getAction()) + "\","
                + "\"volumeRestoredToMax\":" + event.isVolumeRestoredToMax() + ","
                + "\"currentVolume\":" + event.getCurrentVolume() + ","
                + "\"timestamp\":" + event.getTimestamp()
                + "}";
    }

    public static AudioEvent fromJson(String json) {
        String processName = readString(json, "processName");
        int pid = readInt(json, "pid");
        String action = readString(json, "action");
        boolean volumeRestoredToMax = readBoolean(json, "volumeRestoredToMax");
        float currentVolume = readFloat(json, "currentVolume");
        long timestamp = readLong(json, "timestamp");

        return new AudioEvent(processName, pid, action, volumeRestoredToMax, currentVolume, timestamp);
    }

    private static String readString(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);

        if (start < 0) {
            return "";
        }

        start += key.length();
        StringBuilder result = new StringBuilder();

        boolean escaping = false;

        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);

            if (escaping) {
                result.append(ch);
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                escaping = true;
                continue;
            }

            if (ch == '"') {
                break;
            }

            result.append(ch);
        }

        return result.toString();
    }

    private static int readInt(String json, String field) {
        return (int) readLong(json, field);
    }

    private static long readLong(String json, String field) {
        String raw = readRawValue(json, field);

        if (raw.isBlank()) {
            return 0L;
        }

        return Long.parseLong(raw);
    }

    private static boolean readBoolean(String json, String field) {
        return Boolean.parseBoolean(readRawValue(json, field));
    }

    private static float readFloat(String json, String field) {
        String raw = readRawValue(json, field);

        if (raw.isBlank()) {
            return 0.0f;
        }

        return Float.parseFloat(raw);
    }

    private static String readRawValue(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);

        if (start < 0) {
            return "";
        }

        start += key.length();

        int end = start;
        while (end < json.length()) {
            char ch = json.charAt(end);

            if (ch == ',' || ch == '}') {
                break;
            }

            end++;
        }

        return json.substring(start, end).trim();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
