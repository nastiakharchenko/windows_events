package org.windows_events.service.monitor.services;

import org.windows_events.logger.DurableSeqLogger;

import static org.windows_events.constants.Constants.KEYBOARD_HOOK_SERVICE;


public class KeyboardHookMonitorService {

    public KeyboardHookMonitorService() {}

    public void check(DurableSeqLogger durableLogger, String hostNtp) throws Exception {
        CheckStartServices checkStartServices = new CheckStartServices(durableLogger, hostNtp);

        if(!checkStartServices.isServiceRunning(KEYBOARD_HOOK_SERVICE)){
            checkStartServices.startService(KEYBOARD_HOOK_SERVICE);
        }
        if(!checkStartServices.isServiceAutoStart(KEYBOARD_HOOK_SERVICE)){
            checkStartServices.setServiceAutoStart(KEYBOARD_HOOK_SERVICE);
        }
    }


}
