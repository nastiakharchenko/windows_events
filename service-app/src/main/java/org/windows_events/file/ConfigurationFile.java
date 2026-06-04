package org.windows_events.file;

import org.ini4j.Wini;
import org.windows_events.constants.Constants;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationFile {

    public static Map<String, String> readConfigurationSeq(){
        Map<String, String> map = new HashMap<>();
        try {
            Wini ini = new Wini(new File(Constants.FILE_PATH_CONFIG));
            map.put(Constants.URL, ini.get(Constants.SECTION_SEQ, Constants.URL));
            map.put(Constants.KEY, ini.get(Constants.SECTION_SEQ, Constants.KEY));
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return map;
    }

    public static String readTimeServerHost(){
        try {
            Wini ini = new Wini(new File(Constants.FILE_PATH_CONFIG));
            return ini.get(Constants.SECTION_TIME, Constants.NTP_HOST);
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    public static Map<String, Boolean> readModeListenServices(){
        Map<String, Boolean> map = new HashMap<>();
        try {
            Wini ini = new Wini(new File(Constants.FILE_PATH_CONFIG));
            map.put(Constants.KEYBOARD_HOOK_SERVICE, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.KEYBOARD_HOOK_SERVICE)));
            map.put(Constants.CHECK_AUTO_UPDATE_TIME, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.CHECK_AUTO_UPDATE_TIME)));
            map.put(Constants.CHECK_DAYLIGHT_SAVE_TIME, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.CHECK_DAYLIGHT_SAVE_TIME)));
            map.put(Constants.CHECK_NUMLOCK, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.CHECK_NUMLOCK)));
            map.put(Constants.ALERT_100_MBPS, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.ALERT_100_MBPS)));
            map.put(Constants.CHECK_SOUND_PROGRAM, Boolean.valueOf(ini.get(Constants.SECTION_SERVICES, Constants.CHECK_SOUND_PROGRAM)));
        } catch (Exception e) {
            System.err.println(Class.class.getSimpleName() + ": " + e.getMessage());
        }
        return map;
    }
}
