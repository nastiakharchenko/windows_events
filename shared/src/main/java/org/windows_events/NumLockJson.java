package org.windows_events;

import java.util.HashMap;
import java.util.Map;

public final class NumLockJson {

    private NumLockJson() {
    }

    public static String toJson(NumLockEvent event) {
        return "{"
                + "\"eventType\":\"" + escape(event.getEventType()) + "\","
                + "\"username\":\"" + escape(event.getUsername()) + "\","
                + "\"numLockState\":\"" + escape(event.getNumLockState()) + "\","
                + "\"windowTitle\":\"" + escape(event.getWindowTitle()) + "\","
                + "\"processName\":\"" + escape(event.getProcessName()) + "\","
                + "\"pid\":" + event.getPid() + ","
                + "\"timestamp\":" + event.getTimestamp() + ","
                + "\"host\":\"" + escape(event.getHost()) + "\","
                + "\"message\":\"" + escape(event.getMessage()) + "\""
                + "}";
    }

    public static NumLockEvent fromJson(String json) {
        Map<String, String> map = parseFlatJson(json);

        NumLockEvent event = new NumLockEvent();
        event.setEventType(map.getOrDefault("eventType", ""));
        event.setUsername(map.getOrDefault("username", ""));
        event.setNumLockState(map.getOrDefault("numLockState", ""));
        event.setWindowTitle(map.getOrDefault("windowTitle", ""));
        event.setProcessName(map.getOrDefault("processName", ""));
        event.setHost(map.getOrDefault("host", ""));
        event.setMessage(map.getOrDefault("message", ""));

        try {
            event.setPid(Integer.parseInt(map.getOrDefault("pid", "-1")));
        } catch (NumberFormatException e) {
            event.setPid(-1);
        }

        try {
            event.setTimestamp(Long.parseLong(map.getOrDefault("timestamp", "0")));
        } catch (NumberFormatException e) {
            event.setTimestamp(0L);
        }

        return event;
    }

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null) {
            return result;
        }

        String trimmed = json.trim();
        if (trimmed.startsWith("{")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("}")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        String[] pairs = splitJsonPairs(trimmed);
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx <= 0) {
                continue;
            }

            String rawKey = pair.substring(0, idx).trim();
            String rawValue = pair.substring(idx + 1).trim();

            String key = unquote(rawKey);
            String value = unquote(rawValue);

            result.put(key, value);
        }

        return result;
    }

    private static String[] splitJsonPairs(String body) {
        if (body.isBlank()) {
            return new String[0];
        }

        StringBuilder current = new StringBuilder();
        java.util.List<String> parts = new java.util.ArrayList<>();

        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                current.append(c);
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    private static String unquote(String value) {
        String v = value == null ? "" : value.trim();

        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }

        return unescape(v);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
