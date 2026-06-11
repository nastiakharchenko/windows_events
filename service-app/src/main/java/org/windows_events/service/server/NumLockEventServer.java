package org.windows_events.service.server;

import lombok.extern.slf4j.Slf4j;
import org.windows_events.numlock.NumLockEvent;
import org.windows_events.numlock.NumLockJson;

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
public final class NumLockEventServer implements Runnable {

    private final DurableSeqLogger logger;
    private final int port;
    private final String hostNtp;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private volatile String lastState = null;

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

    private synchronized void logNumLockEvent(NumLockEvent event) {
        String current = event.getNumLockState();

        if (current != null && current.equals(lastState)) {
            return;
        }

        lastState = current;

        String message = buildSeqMessage(event);

        try {
            logger.log(message);
        } catch (Exception e) {
            System.err.println(message);
        }
    }

//    private void logNumLockEvent(NumLockEvent event) {
//        String message = buildSeqMessage(event);
//
//        try {
//           logger.log(message);
//        } catch (Exception e) {
//            System.err.println(message);
//        }
//    }

    private String buildSeqMessage(NumLockEvent event) {
        try{
            StringBuilder str = new StringBuilder();
            str.append(IDENTIFIER_PROGRAM)
                    .append(String.format(IDENTIFIER_PC, DataPCMonitorService.getHostName()
                            , DataPCMonitorService.getIpAddress(), UserMonitorService.getActiveUser()));
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

            if (event.getNumLockState().equals("ON")){
                str.append(NUMLOCK_ON);
            } else if(event.getNumLockState().equals("OFF")){
                str.append(NUMLOCK_OFF);
            }

            return str.toString();
        }catch(Exception e){
            System.err.println(e.getMessage());
            return "";
        }
    }
}