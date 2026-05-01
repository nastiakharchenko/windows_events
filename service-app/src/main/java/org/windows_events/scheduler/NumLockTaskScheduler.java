package org.windows_events.scheduler;

import org.windows_events.logger.DurableSeqLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NumLockTaskScheduler {

    private static final String TASK_NAME = "WindowsEvents_NumLockAgent";
    private static final String AGENT_EXE_NAME = "WindowsEventsNumLockAgent.exe";

    private NumLockTaskScheduler() {
    }

    public static void recreateScheduledTask(DurableSeqLogger logger, String username) {
        try {
            validateUsername(username);

            Path agentExe = resolveAgentExePath();
            validateAgentExe(agentExe);

            deleteTaskIfExists(logger);
            createTask(logger, username, agentExe);


        } catch (Exception e) {
            System.err.println("Failed to recreate scheduled task: " + TASK_NAME + e);
        }
    }

    public static void ensureScheduledTaskExists(DurableSeqLogger logger, String username) {
        try {
            validateUsername(username);

            Path agentExe = resolveAgentExePath();
            validateAgentExe(agentExe);

            if (taskExists()) {
                return;
            }

            createTask(logger, username, agentExe);


        } catch (Exception e) {
            System.err.println("Failed to create scheduled task: " + TASK_NAME + e);
        }
    }

    public static void deleteTaskIfExists(DurableSeqLogger logger) {
        try {
            if (!taskExists()) {
                return;
            }

            List<String> command = new ArrayList<>();
            command.add(getSchtasksPath());
            command.add("/Delete");
            command.add("/TN");
            command.add(TASK_NAME);
            command.add("/F");

            ProcessResult result = runCommand(command);

            if (result.exitCode != 0) {
                throw new IllegalStateException(
                        "schtasks delete failed. exitCode=" + result.exitCode + ", output=" + result.output
                );
            }

        } catch (Exception e) {
            System.err.println("Failed to delete scheduled task: " + TASK_NAME + e);
        }
    }

    private static void createTask(DurableSeqLogger logger, String username, Path agentExe) throws Exception {
        String taskCommand = "\"" + agentExe.toString() + "\"";

        List<String> command = new ArrayList<>();
        command.add(getSchtasksPath());
        command.add("/Create");
        command.add("/F");
        command.add("/SC");
        command.add("ONLOGON");
        command.add("/RL");
        command.add("HIGHEST");
        command.add("/TN");
        command.add(TASK_NAME);
        command.add("/TR");
        command.add(taskCommand);
        command.add("/RU");
        command.add(username);
        command.add("/IT");

        ProcessResult result = runCommand(command);

        if (result.exitCode != 0) {
            throw new IllegalStateException(
                    "schtasks create failed. exitCode=" + result.exitCode + ", output=" + result.output
            );
        }
    }

    private static boolean taskExists() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(getSchtasksPath());
        command.add("/Query");
        command.add("/TN");
        command.add(TASK_NAME);

        ProcessResult result = runCommand(command);
        return result.exitCode == 0;
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is empty");
        }
    }

    private static Path resolveAgentExePath() {
        Path baseDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return baseDir.resolve(AGENT_EXE_NAME);
    }

    private static void validateAgentExe(Path agentExe) {
        if (!Files.exists(agentExe)) {
            throw new IllegalStateException("Agent exe not found: " + agentExe);
        }

        if (!Files.isRegularFile(agentExe)) {
            throw new IllegalStateException("Agent exe is not a file: " + agentExe);
        }
    }

    private static String getSchtasksPath() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            systemRoot = "C:\\Windows";
        }
        return systemRoot + "\\System32\\schtasks.exe";
    }

    public static void runTaskNow(DurableSeqLogger logger) {
        try {
            List<String> command = new ArrayList<>();
            command.add(getSchtasksPath());
            command.add("/Run");
            command.add("/TN");
            command.add(TASK_NAME);

            ProcessResult result = runCommand(command);

            if (result.exitCode != 0) {
                throw new IllegalStateException(
                        "schtasks run failed. exitCode=" + result.exitCode + ", output=" + result.output
                );
            }

        } catch (Exception e) {
            System.err.println("Failed to run scheduled task: " + TASK_NAME + e);
        }
    }

    private static ProcessResult runCommand(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.forName("CP866")))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output.toString());
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}