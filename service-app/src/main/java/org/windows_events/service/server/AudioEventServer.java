package org.windows_events.service.server;

import lombok.extern.slf4j.Slf4j;
import org.windows_events.audio.AudioEvent;
import org.windows_events.audio.AudioJson;
import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.DateFormatter;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.windows_events.constants.Constants.*;

@Slf4j
public final class AudioEventServer implements Runnable {

    private final DurableSeqLogger logger;
    private final int port;
    private final String hostNtp;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    public AudioEventServer(DurableSeqLogger logger, int port, String hostNtp) {
        this.logger = logger;
        this.port = port;
        this.hostNtp = hostNtp;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));

            while (running) {
                Socket socket = serverSocket.accept();
                clientPool.submit(() -> handleClient(socket));
            }

        } catch (Exception e) {
            if (running) {
                System.err.println("Audio event server failed " + e);
            }
        } finally {
            closeServerSocket();
        }
    }

    public void stop() {
        running = false;
        closeServerSocket();
        clientPool.shutdownNow();
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                AudioEvent event = AudioJson.fromJson(line);
                logAudioEvent(event);
            }

        } catch (Exception e) {
            System.err.println("Error while reading Audio event from agent " + e);
        }
    }

    private void logAudioEvent(AudioEvent event) {
        String message = buildSeqMessage(event);

        try {
            logger.log(message);
        } catch (Exception e) {
            System.err.println(message);
        }
    }

    private String buildSeqMessage(AudioEvent event) {
        try {
            StringBuilder str = new StringBuilder();

            str.append(IDENTIFIER_PROGRAM)
                    .append(String.format(
                            IDENTIFIER_PC,
                            DataPCMonitorService.getHostName(),
                            DataPCMonitorService.getIpAddress(),
                            UserMonitorService.getActiveUser()
                    ));

            NTPTimeService ntp = new NTPTimeService(hostNtp);
            boolean access = true;
            Date date = ntp.getNTPTime();
            if(date == null){
                date = Date.from(Instant.now());
                access = false;
            }
            str.append(DateFormatter.dateConvert(date));
            if(!access){
                str.append(TIME_PC);
            }

//            str.append(DateFormatter.dateConvert(ntp.getNTPTime()));

            str.append(" Заборонено вимкнення звуку. ");

            str.append("Процес: ")
                    .append(safe(event.getProcessName()))
                    .append(", PID: ")
                    .append(event.getPid())
                    .append(", дія: ")
                    .append(safe(event.getAction()))
                    .append(", рівень гучності до врегулювання: ")
                    .append(Math.round(event.getCurrentVolume() * 100))
                    .append(" %");

            if (event.isVolumeRestoredToMax()) {
                str.append(", гучність відновлена.");
            }

            return str.toString();

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
