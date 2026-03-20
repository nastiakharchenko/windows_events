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
}
