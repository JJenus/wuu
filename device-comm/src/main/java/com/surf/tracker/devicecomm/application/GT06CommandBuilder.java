package com.surf.tracker.devicecomm.application;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class GT06CommandBuilder {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HHmmss");
    
    public static String buildChangePasswordCommand(String imei, String oldPassword, String newPassword) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,S1,%s,%s,%s#", imei, time, oldPassword, newPassword);
    }
    
    public static String buildSetCenterNumberCommand(String imei, String centerNumber) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,S2,%s,%s#", imei, time, centerNumber);
    }
    
    public static String buildSetAdminNumberCommand(String imei, String... adminNumbers) {
        String time = getCurrentTime();
        StringBuilder numbers = new StringBuilder();
        for (int i = 0; i < Math.min(adminNumbers.length, 5); i++) {
            if (i > 0) numbers.append(",");
            numbers.append(adminNumbers[i]);
        }
        return String.format("*HQ,%s,S3,%s,%s#", imei, time, numbers.toString());
    }
    
    public static String buildSetAlarmModeCommand(String imei, int mode) {
        // 0: close SMS and Calling alarm
        // 1: SMS alarm
        // 2: Calling center number as alarm
        String time = getCurrentTime();
        return String.format("*HQ,%s,S18,%s,%d#", imei, time, mode);
    }
    
    public static String buildSetGeoFenceCommand(String imei, int radiusMeters, int mode) {
        // mode: 1=out fence, 2=in fence, 3=out and in
        String time = getCurrentTime();
        return String.format("*HQ,%s,S21,%s,%d,%d#", imei, time, radiusMeters, mode);
    }
    
    public static String buildSetIPPortCommand(String imei, String ip, int port) {
        String time = getCurrentTime();
        String ipParts = ip.replace(".", ",");
        return String.format("*HQ,%s,S23,%s,%s,%d#", imei, time, ipParts, port);
    }
    
    public static String buildSetAPNCommand(String imei, String apn, String password) {
        String time = getCurrentTime();
        if (password == null || password.isEmpty()) {
            return String.format("*HQ,%s,S24,%s,%s,#", imei, time, apn);
        }
        return String.format("*HQ,%s,S24,%s,%s,%s#", imei, time, apn, password);
    }
    
    public static String buildRestartCommand(String imei) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,R1,%s#", imei, time);
    }
    
    public static String buildSetGPRSIntervalCommand(String imei, int intervalSeconds) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,D1,%s,%d#", imei, time, intervalSeconds);
    }
    
    public static String buildSetOverspeedAlarmCommand(String imei, int speedLimitKmh) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,S33,%s,%d#", imei, time, speedLimitKmh);
    }
    
    public static String buildSetWorkingModeCommand(String imei, int mode) {
        String time = getCurrentTime();
        return String.format("*HQ,%s,WKMD,%s,%d#", imei, time, mode);
    }
    
    public static String buildReadDeviceStateCommand(String imei, int type) {
        // type: 0=basic data, 1=software version, 2=other data
        String time = getCurrentTime();
        return String.format("*HQ,%s,S26,%s,%d#", imei, time, type);
    }
    
    private static String getCurrentTime() {
        return LocalTime.now().format(TIME_FORMATTER);
    }
    
    public static Map<String, String> getCommandExamples() {
        return Map.of(
            "Change Password", "*HQ,865205030330012,S1,130305,123456,000000#",
            "Set Center Number", "*HQ,865205030330012,S2,130305,13812341234#",
            "Fuel Cut", "*HQ,865205033365775,S20,092353,1,3,10,3,5,5,7#",
            "Fuel Restore", "*HQ,865205030330012,S20,130305,1,0#",
            "Set Geo-fence", "*HQ,865205030330012,S21,130305,1000,1#",
            "Set IP/Port", "*HQ,865205030330012,S23,130305,116,205,4,25,8800#"
        );
    }
}