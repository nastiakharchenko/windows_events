package org.windows_events.time;

import lombok.Getter;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;
import java.util.Date;

@Getter
public class NTPTimeService {

    private final String ntpHost;

    public NTPTimeService(String host) {
        this.ntpHost = host;
    }

    public Date getNTPTime() throws Exception {
        int maxAttempts = 5;
        int delayMs = 3000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(5000);

            try {
                client.open();
                InetAddress address = InetAddress.getByName(ntpHost);

                TimeInfo info = client.getTime(address);
                info.computeDetails();

                long returnTime = info.getMessage().getTransmitTimeStamp().getTime();
                return new Date(returnTime);

            } catch (Exception e) {
                System.err.println("Спроба " + attempt + " не вдалась: " + e.getMessage());

                if (attempt == maxAttempts) {
                    throw new Exception("Не вдалося отримати час з NTP після "
                            + maxAttempts + " спроб", e);
                }

                Thread.sleep(delayMs);
            } finally {
                client.close();
            }
        }

        throw new Exception("Неочікувана помилка");
    }

//    public Date getNTPTime() throws Exception {
//        NTPUDPClient client = new NTPUDPClient();
//        client.setDefaultTimeout(5000);     //если сервер не отвечает или сеть медленная
//        client.open();
//        InetAddress address = InetAddress.getByName(ntpHost);
//        TimeInfo info = client.getTime(address);
//        info.computeDetails();
//
//        long returnTime = info.getMessage().getTransmitTimeStamp().getTime();
//        Date time = new Date(returnTime);
//
//        client.close();
//        return time;
//    }
}
