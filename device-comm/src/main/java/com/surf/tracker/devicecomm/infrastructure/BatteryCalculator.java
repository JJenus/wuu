package com.surf.tracker.devicecomm.infrastructure;

public class BatteryCalculator {
    
    /**
     * Calculate battery percentage from hex voltage value
     * According to GT06 protocol: 0x0000-0x0299 represents battery voltage
     * Formula: voltage = (hex_value / 1024) * 5.6V
     * Percentage = (voltage / 3.6V) * 100%
     */
    public static float calculateBatteryPercentage(String batteryHex) {
        try {
            if (batteryHex == null || batteryHex.length() != 4) {
                return 0.0f;
            }
            
            int hexValue = Integer.parseInt(batteryHex, 16);
            
            // Calculate voltage
            float voltage = (hexValue / 1024.0f) * 5.6f;
            
            // Calculate percentage (nominal voltage is 3.6V)
            float percentage = (voltage / 3.6f) * 100.0f;
            
            // Clamp between 1% and 100%
            if (percentage < 1.0f) return 1.0f;
            if (percentage > 100.0f) return 100.0f;
            
            return percentage;
            
        } catch (Exception e) {
            return 0.0f;
        }
    }
    
    /**
     * Calculate battery voltage from hex value
     */
    public static float calculateBatteryVoltage(String batteryHex) {
        try {
            if (batteryHex == null || batteryHex.length() != 4) {
                return 0.0f;
            }
            
            int hexValue = Integer.parseInt(batteryHex, 16);
            return (hexValue / 1024.0f) * 5.6f;
            
        } catch (Exception e) {
            return 0.0f;
        }
    }
}