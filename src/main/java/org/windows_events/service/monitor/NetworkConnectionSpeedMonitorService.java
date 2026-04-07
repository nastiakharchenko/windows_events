package org.windows_events.service.monitor;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.windows_events.constants.Constants.*;

public class NetworkConnectionSpeedMonitorService {
    private long THRESHOLD_MBPS = 100;
    private DurableSeqLogger durableLogger;

    public NetworkConnectionSpeedMonitorService(DurableSeqLogger durableLogger) {
        this.durableLogger = durableLogger;
    }

    /**
     * Проверяет все адаптеры из Get-NetAdapter | Select Name, LinkSpeed
     * и возвращает список адаптеров, у которых скорость <= 100 Мбит/с.
     */
    public void findAdaptersWithLowSpeed(String hostNTP) throws Exception {
//        List<AdapterSpeedInfo> result = new ArrayList<>();
        NTPTimeService ntpTimeService = new NTPTimeService(hostNTP);

        for (AdapterSpeedInfo adapter : getAdapters()) {
            if (adapter.getSpeedMbps().isPresent() && adapter.getSpeedMbps().get() <= THRESHOLD_MBPS) {
//                result.add(adapter);
                durableLogger.log(IDENTIFIER_PROGRAM +
                        String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                                , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser())
                        + String.format(LOW_NETWORK_SPEED, adapter.name, adapter.rawLinkSpeed)
                        + DateFormatter.dateConvert(ntpTimeService.getNTPTime()));
            }
        }

//        return result;
    }

    /**
     * Получает список адаптеров через PowerShell-команду:
     * Get-NetAdapter | Select Name, LinkSpeed
     */
    private List<AdapterSpeedInfo> getAdapters() throws IOException, InterruptedException {
        String psCommand = "Get-NetAdapter | Select-Object Name, LinkSpeed";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                psCommand
        );

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        List<AdapterSpeedInfo> adapters = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {

            String line;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                // Пропускаем заголовки:
                // Name          LinkSpeed
                // ----          ---------
                if (!headerSkipped) {
                    if (line.toLowerCase(Locale.ROOT).contains("name")
                            && line.toLowerCase(Locale.ROOT).contains("linkspeed")) {
                        continue;
                    }

                    if (line.trim().matches("^-+\\s+-+$")) {
                        headerSkipped = true;
                        continue;
                    }
                }

                AdapterSpeedInfo info = parseAdapterLine(line);
                if (info != null) {
                    adapters.add(info);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("PowerShell command failed with exit code: " + exitCode);
        }

        return adapters;
    }

    /**
     * Парсит строку вида:
     * Ethernet              1 Gbps
     * Wi-Fi                 100 Mbps
     * Local Area Connection 10 Mbps
     */
    private AdapterSpeedInfo parseAdapterLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String trimmed = line.trim();

        // Делим строку по 2+ пробелам:
        // [adapter name][2+ spaces][link speed]
        String[] parts = trimmed.split("\\s{2,}");
        if (parts.length < 2) {
            return null;
        }

        String name = parts[0].trim();
        String linkSpeedRaw = parts[1].trim();

        Optional<Long> speedMbps = parseSpeedToMbps(linkSpeedRaw);

        return new AdapterSpeedInfo(name, linkSpeedRaw, speedMbps);
    }

    /**
     * Поддерживает значения вроде:
     * 10 Mbps
     * 100 Mbps
     * 1 Gbps
     * 2.5 Gbps
     */
    private Optional<Long> parseSpeedToMbps(String rawSpeed) {
        if (rawSpeed == null || rawSpeed.isBlank()) {
            return Optional.empty();
        }

        Pattern pattern = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(Mbps|Gbps)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawSpeed.trim());

        if (!matcher.find()) {
            return Optional.empty();
        }

        double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);

        double speedMbps;
        if ("gbps".equals(unit)) {
            speedMbps = value * 1000;
        } else {
            speedMbps = value;
        }

        return Optional.of((long) speedMbps);
    }

    public static class AdapterSpeedInfo {
        private final String name;
        private final String rawLinkSpeed;
        private final Optional<Long> speedMbps;

        public AdapterSpeedInfo(String name, String rawLinkSpeed, Optional<Long> speedMbps) {
            this.name = name;
            this.rawLinkSpeed = rawLinkSpeed;
            this.speedMbps = speedMbps;
        }

        public String getName() {
            return name;
        }

        public String getRawLinkSpeed() {
            return rawLinkSpeed;
        }

        public Optional<Long> getSpeedMbps() {
            return speedMbps;
        }

        @Override
        public String toString() {
            return "AdapterSpeedInfo{" +
                    "name='" + name + '\'' +
                    ", rawLinkSpeed='" + rawLinkSpeed + '\'' +
                    ", speedMbps=" + speedMbps +
                    '}';
        }
    }
}
