package org.windows_events.service;

import lombok.extern.slf4j.Slf4j;
import org.windows_events.NumLockEvent;
import org.windows_events.NumLockJson;

import org.windows_events.logger.DurableSeqLogger;
import org.windows_events.service.monitor.DataPCMonitorService;
import org.windows_events.service.monitor.UserMonitorService;
import org.windows_events.time.NTPTimeService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.windows_events.constants.Constants.*;

@Slf4j
public final class NumLockEventServer implements Runnable {

    private final DurableSeqLogger logger;
    private final int port;
    private final String hostNtp;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    public NumLockEventServer(DurableSeqLogger logger, int port, String hostNtp) {
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
                System.err.println("NumLock event server failed " + e);
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
                NumLockEvent event = NumLockJson.fromJson(line);
                logNumLockEvent(event);
            }
        } catch (Exception e) {
            System.err.println("Error while reading NumLock event from agent " + e);
        }
    }

    private void logNumLockEvent(NumLockEvent event) {
        String message = buildSeqMessage(event);

        try {
           logger.log(message);
        } catch (Exception e) {
            System.err.println(message);
        }
    }

    private String buildSeqMessage(NumLockEvent event) {
        try{
            StringBuilder str = new StringBuilder();
            str.append(IDENTIFIER_PROGRAM)
                    .append(String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser()));
            NTPTimeService ntp = new NTPTimeService(hostNtp);
            str.append(DateFormatter.dateConvert(ntp.getNTPTime()));

            if (event.getNumLockState().equals("ON")){
                str.append(NUMLOCK_ON);
//                        .append(safe(event.getNumLockState()));
            } else if(event.getNumLockState().equals("OFF")){
                str.append(NUMLOCK_OFF);
//                        .append(safe(event.getNumLockState()))
//                        .append(NUMLOCK_ON);
            }

            return str.toString();
        }catch(Exception e){
            System.err.println(e.getMessage());
            return "";
        }
//        return "NumLock event"
//                + " | type=" + safe(event.getEventType())
//                + " | user=" + safe(event.getUsername())
//                + " | state=" + safe(event.getNumLockState())
//                + " | host=" + safe(event.getHost())
//                + " | process=" + safe(event.getProcessName())
//                + " | pid=" + event.getPid()
//                + " | window=" + safe(event.getWindowTitle())
//                + " | timestamp=" + event.getTimestamp()
//                + " | message=" + safe(event.getMessage());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}