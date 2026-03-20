package org.windows_events.service;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.windows_events.constants.Constants.DATE_FORMAT;

public class DateFormatter {

    public static String dateConvertString(long dateLong){
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        Date date = new Date(dateLong);
        return sdf.format(date);
    }

    public static String dateConvert(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(date);
    }
}
