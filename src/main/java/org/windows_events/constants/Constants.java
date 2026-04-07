package org.windows_events.constants;

public class Constants {
    public static final int DELAY = 2;

    public static final String FILE_PATH_CONFIG = "data\\config.ini";
    public static final String SECTION_SEQ = "Seq";
    public static final String URL = "ServerUrl";
    public static final String KEY = "ApiKey";
    public static final String SECTION_TIME = "Time server";
    public static final String NTP_HOST = "NtpHost";
    public static final String KEYBOARD_HOOK_SERVICE = "KeyboardHookService";
    public static final String SECTION_SERVICES = "Services";
    public static final String W32_TIME_SERVICE = "W32Time";
    public static final String CHECK_AUTO_UPDATE_TIME = "CheckAutoUpdateTime";
    public static final String CHECK_DAYLIGHT_SAVE_TIME = "CheckDaylightSaveTime";
    public static final String CHECK_NUMLOCK = "CheckNumLock";
    public static final String ALERT_100_MBPS = "Alert100Mbps";

    public static final String DATE_FORMAT = "dd.MM.yyyy HH:mm:ss";

    public static final String IDENTIFIER_PROGRAM = "Windows_Events. ";
    public static final String IDENTIFIER_PC = " Host: %s  IP: %s  User: %s  |  ";

    public static final String START_SERVICE = "ПК ввімкнено: ";
    public static final String STOP_SERVICE = "ПК вимкнено: ";
    public static final String USB_CONNECTED = "USB-пристрій під'єднано: ";
    public static final String USB_DISCONNECTED = "USB-пристрій від'єднано: ";

    public static final String CODE_6005 = " 6005:Запущено службу журналу подій.";
    public static final String CODE_6006 = " 6006:Windows завершила всі процеси коректно.";
    public static final String CODE_6008 = " 6008:Непередбачене вимкнення.";
    public static final String CODE_1074 = " 1074:Планове вимкнення або перезавантаження.";

    public static final String CHANGE_TIME_PC = "Час ПК %s успішно змінено на %s.";
    public static final String ERROR_EXIT_CODE = "Помилка. Перевірте, чи надано права доступу. Код виходу: ";
    public static final String ERROR_NTP_HOST = "NTP-host недоступний або зчитаний з помилкою.";

    public static final String AUTOSTART_SERVICE = "Службі %s ввімкнено автозапуск. ";
    public static final String RUN_SERVICE = "Службу %s запущено. ";
    public static final String SET_AUTOUPDATE_TIME = "Ввімкнено автоматичне оновлення часу. ";
    public static final String SET_DAYLIGHT_SAVE_TIME = "Ввімкнено автоматичне переведення на літній час. ";
    public static final String LOW_NETWORK_SPEED = "Швидкість передачі даних у %s рівна %s. ";
}
