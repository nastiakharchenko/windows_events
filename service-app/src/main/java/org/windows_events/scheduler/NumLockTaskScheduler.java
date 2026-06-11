package org.windows_events.scheduler;

import org.windows_events.logger.DurableSeqLogger;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
//            createTask(logger, username, agentExe);
            createTask(logger, agentExe);

        } catch (Exception e) {
            System.err.println("Failed to recreate scheduled task: " + TASK_NAME + e);
        }
    }

    public static void ensureScheduledTaskExists(DurableSeqLogger logger, String username) {
        try {
            validateUsername(username);

            Path agentExe = resolveAgentExePath();
            validateAgentExe(agentExe);

//            if (taskExists()) {
//                return;
//            }

//            createTask(logger, username, agentExe);
            createTask(logger, agentExe);
            Thread.sleep(1000);
            runAgentRightNow(logger, agentExe);

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

    private static void createTask(DurableSeqLogger logger, Path agentExe) throws Exception {
        String taskCommand = agentExe.toAbsolutePath().toString();

        // 1. Возвращаем надежную глобальную папку ProgramData
        Path startupFolder = Paths.get(System.getenv("ProgramData"),
                "Microsoft", "Windows", "Start Menu", "Programs", "StartUp");

        if (!Files.exists(startupFolder)) {
            Files.createDirectories(startupFolder);
        }

        Path vbsScriptPath = startupFolder.resolve("WindowsEvents_NumLockAgent.vbs");

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ:
        // Если служба уже создала этот файл ранее, просто выходим.
        // Обычные пользователи при смене сессии не будут пытаться его перезаписать,
        // и ошибка "Разрешение отклонено" (800A0046) полностью исчезнет!
        if (Files.exists(vbsScriptPath)) {
            return;
        }

        // 2. Формируем VBS-скрипт
        String vbsContent = "Set WshShell = CreateObject(\"WScript.Shell\")\n" +
                "WshShell.Run \"\"\"" + taskCommand + "\"\"\", 0, False\n";

        // 3. Записываем скрипт на диск (отработает один раз при старте службы под SYSTEM)
        try (FileWriter writer = new FileWriter(vbsScriptPath.toFile())) {
            writer.write(vbsContent);
        }

        logger.log("Универсальный лончер агента успешно прописан в автозагрузку всех сессий.");

//        String taskCommand = agentExe.toAbsolutePath().toString();
//
//        // КОрректная конфигурация для ЛЮБЫХ типов пользователей (включая Standard Users)
//        String psScript =
//                "$scheduler = New-Object -ComObject 'Schedule.Service'; " +
//                        "$scheduler.Connect(); " +
//                        "$rootFolder = $scheduler.GetFolder('\\'); " +
//
//                        "$taskDefinition = $scheduler.NewTask(0); " +
//                        "$settings = $taskDefinition.Settings; " +
//                        "$settings.Enabled = $true; " +
//                        "$settings.MultipleInstances = 2; " + // Разрешаем параллельные процессы в разных сессиях
//                        "$settings.ExecutionTimeLimit = 'PT0S'; " + // Без ограничений по времени
//                        "$settings.DisallowStartIfOnBatteries = $false; " +
//                        "$settings.StopIfGoingOnBatteries = $false; " +
//
//                        "$triggers = $taskDefinition.Triggers; " +
//
//                        // Триггер 1: Вход в систему (Logon)
//                        "$logonTrigger = $triggers.Create(9); " +
//                        "$logonTrigger.Enabled = $true; " +
//
//                        // Триггер 2: Переключение пользователя (Быстрая смена пользователей)
//                        "$sessionTrigger = $triggers.Create(11); " +
//                        "$sessionTrigger.Enabled = $true; " +
//                        "$sessionTrigger.StateChange = 3; " + // 3 = ConsoleConnect (экран переключен на юзера)
//
//                        // Триггер 3: Разблокировка экрана (Unlock)
//                        "$unlockTrigger = $triggers.Create(11); " +
//                        "$unlockTrigger.Enabled = $true; " +
//                        "$unlockTrigger.StateChange = 7; " + // 7 = SessionUnlock
//
//                        // НАСТРОЙКА БЕЗОПАСНОСТИ:
//                        "$principal = $taskDefinition.Principal; " +
//                        "$principal.GroupId = 'S-1-5-32-545'; " + // Встроенная группа "Пользователи" (Users)
//
//                        // КРИТИЧНО: Используем InteractiveToken (3) и ОБЫЧНЫЕ ПРАВА LeastPrivilege (0).
//                        // Это заставит Windows запускать агент для ЛЮБОГО юзера, не спотыкаясь об UAC.
//                        "$principal.LogonType = 3; " +
//                        "$principal.RunLevel = 0; " + // 0 = LeastPrivilege (Запуск в обычном пользовательском режиме)
//
//                        "$actions = $taskDefinition.Actions; " +
//                        "$action = $actions.Create(0); " +
//                        "$action.Path = '" + taskCommand + "'; " +
//
//                        "$rootFolder.RegisterTaskDefinition('" + TASK_NAME + "', $taskDefinition, 6, $null, $null, 3);";
//
//        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
//        Process process = pb.start();
//
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            String errorOutput = new String(process.getErrorStream().readAllBytes(), "CP866");
//            throw new IllegalStateException("Ошибка регистрации задачи: " + errorOutput);
//        }
    }

//    private static void createTask(DurableSeqLogger logger, String username, Path agentExe) throws Exception {
////        String taskCommand = "\"" + agentExe.toString() + "\"";
//
//        String taskCommand = agentExe.toAbsolutePath().toString();
//
//        List<String> command = new ArrayList<>();
//        command.add(getSchtasksPath());
//        command.add("/Create");
//        command.add("/F");
//        command.add("/SC");
//        command.add("ONLOGON");
////        command.add("/RL");
////        command.add("HIGHEST");
//        command.add("/TN");
//        command.add(TASK_NAME);
//        command.add("/TR");
//        command.add(taskCommand);
//        command.add("/RU");
////        command.add("NT AUTHORITY\\\\INTERACTIVE");
//        command.add(username);
////        command.add(".\\" + username);
//        command.add("/IT");
//
//        ProcessResult result = runCommand(command);
//
//        if (result.exitCode != 0) {
//            throw new IllegalStateException(
//                    "schtasks create failed. exitCode=" + result.exitCode + ", output=" + result.output
//            );
//        }
//    }

    private static void runAgentRightNow(DurableSeqLogger logger, Path agentExe) {
        try {
            String taskCommand = agentExe.toAbsolutePath().toString();

            // 1. Динамически определяем имя пользователя, который СЕЙЧАС сидит за компьютером (активная консоль)
            // Команда query session возвращает список всех сессий. Ищем ту, у которой статус "Активно" (Active)
            Process queryProc = Runtime.getRuntime().exec("cmd.exe /c query session");
            BufferedReader reader = new BufferedReader(new InputStreamReader(queryProc.getInputStream(), "CP866"));
            String line;
            String currentActiveUser = null;

            while ((line = reader.readLine()) != null) {
                if (line.contains("console") && line.contains("Активно") || line.contains("Active")) {
                    // Разбираем строку, чтобы вытащить чистое имя пользователя
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        // В зависимости от конфигурации Windows имя пользователя будет во 2-й или 3-й колонке
                        currentActiveUser = parts[1].equals("console") ? parts[2] : parts[1];
                    }
                }
            }

            if (currentActiveUser == null || currentActiveUser.isEmpty() || currentActiveUser.equalsIgnoreCase("system")) {
                logger.log("Активный пользователь в консоли не найден (экран заблокирован или никого нет). Запуск отложен до Logon.");
                return;
            }

            logger.log("Обнаружен активный пользователь: " + currentActiveUser + ". Запускаем агент в его сессии...");

            // 2. Создаем и тут же запускаем ВРЕМЕННУЮ интерактивную задачу ОДИН РАЗ персонально под этого пользователя
            // Используем PowerShell COM, так как он гарантированно привяжет InteractiveToken к конкретному имени юзера
            String psScript =
                    "$scheduler = New-Object -ComObject 'Schedule.Service'; " +
                            "$scheduler.Connect(); " +
                            "$rootFolder = $scheduler.GetFolder('\\'); " +
                            "$taskDefinition = $scheduler.NewTask(0); " +

                            // Настройки интерактивного запуска под конкретного живого юзера
                            "$principal = $taskDefinition.Principal; " +
                            "$principal.UserId = '" + currentActiveUser + "'; " +
                            "$principal.LogonType = 3; " + // 3 = InteractiveToken (полный доступ к UI, окнам и WinAPI клавиатуры)
                            "$principal.RunLevel = 0; " +  // 0 = LeastPrivilege (чтобы запустилось даже у обычного пользователя)

                            // Действие - запуск нашего экзешника
                            "$actions = $taskDefinition.Actions; " +
                            "$action = $actions.Create(0); " +
                            "$action.Path = '" + taskCommand + "'; " +

                            // Регистрируем временную задачу (6 = CreateOrUpdate)
                            "$rootFolder.RegisterTaskDefinition('Temp_NumLock_Launcher', $taskDefinition, 6, $null, $null, 3); " +

                            // Запускаем её немедленно
                            "$task = $rootFolder.GetTask('Temp_NumLock_Launcher'); " +
                            "$task.Run($null); " +

                            // Сразу же удаляем саму задачу из планировщика (процесс агента останется жить в сессии пользователя!)
                            "[System.Threading.Thread]::Sleep(1000); " + // Небольшая пауза, чтобы Windows успела породить процесс
                            "$rootFolder.DeleteTask('Temp_NumLock_Launcher', 0);";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
            Process pc = pb.start();
            pc.waitFor();

            logger.log("Агент успешно заброшен и запущен в сессию пользователя " + currentActiveUser);

        } catch (Exception e) {
            logger.log("Ошибка мгновенного запуска агента: " + e.getMessage());
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