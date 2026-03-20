package org.windows_events.constants;

public class Constants {
    public static final String FILE_PATH_CONFIG = "data\\config.ini";
    public static final String SECTION_SEQ = "Seq";
    public static final String URL = "ServerUrl";
    public static final String KEY = "ApiKey";
    public static final String SECTION_TIME = "Time server";
    public static final String NTP_HOST = "NtpHost";

    public static final String DATE_FORMAT = "dd.MM.yyyy HH:mm:ss";

    public static final String IDENTIFIER_PROGRAM = "Windows_Events. ";
    public static final String IDENTIFIER_PC = " Host: %s  IP: %s  |  ";

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
}
