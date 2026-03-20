package org.windows_events.logger;

import org.windows_events.service.JsonConvertor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class DurableSeqLogger {

    private final Path filePath;
    private final String seqUrl;
    private final String apiKey;

    // Очередь событий для отправки в Seq
    private final Queue<String> eventQueue = new LinkedList<>();

    public DurableSeqLogger(String seqUrl, String apiKey, String fileName) throws IOException {
        this.seqUrl = seqUrl + "/api/events/raw";
        this.apiKey = apiKey;
        this.filePath = Paths.get(fileName);

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        // Загружаем все события из файла в очередь
        loadEventsFromFile();

        // Фоновый поток отправки
        Thread sender = new Thread(this::senderLoop);
        sender.setDaemon(true);
        sender.start();
    }

    /** Логирование нового события */
    public synchronized void log(String message) {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.APPEND)) {
            writer.write(message);
            writer.newLine();
            eventQueue.offer(message); // добавляем в очередь для отправки
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Загрузка всех событий из файла при старте */
    private synchronized void loadEventsFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                eventQueue.offer(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Фоновый поток: отправка событий в Seq */
    private void senderLoop() {
        while (true) {
            try {
                String event;
                synchronized (this) {
                    event = eventQueue.peek(); // берём только первый элемент
                }

                if (event == null) {
                    Thread.sleep(1000);
                    continue;
                }

                if (sendToSeq(event)) {
                    // Успешно отправлено → удаляем из очереди и файла
                    removeFirstLineFromFile();
                    synchronized (this) {
                        eventQueue.poll();
                    }
                } else {
                    Thread.sleep(5000); // Seq недоступен, повтор через 5 сек
                }

            } catch (Exception e) {
                System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    /** Отправка события в Seq */
    private boolean sendToSeq(String message) {
        try {
            URL url = new URL(seqUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/vnd.serilog.clef");
            conn.setRequestProperty("X-Seq-ApiKey", apiKey);
            conn.setDoOutput(true);

            String payload = "{ \"@t\":\"" + Instant.now() + "\", \"@m\":\"" + JsonConvertor.escapeJson(message) + "\" }";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
            }

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Безопасное удаление первой строки из файла */
    private synchronized void removeFirstLineFromFile() throws IOException {
        Path temp = Paths.get(filePath.toString() + ".tmp");
        try (BufferedReader reader = Files.newBufferedReader(filePath);
             BufferedWriter writer = Files.newBufferedWriter(temp)) {

            boolean skipFirst = true;
            String line;
            while ((line = reader.readLine()) != null) {
                if (skipFirst) {
                    skipFirst = false;
                    continue;
                }
                writer.write(line);
                writer.newLine();
            }
        }

        Files.delete(filePath);
        Files.move(temp, filePath);
    }
}