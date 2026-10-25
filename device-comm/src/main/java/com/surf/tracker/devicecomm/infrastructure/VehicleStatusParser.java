package com.surf.tracker.devicecomm.infrastructure;

public class VehicleStatusParser {

    public static DeviceStatus parseGT06Status(String statusHex) {
        if (statusHex == null || statusHex.length() != 8) {
            return DeviceStatus.allClear();
        }

        try {
            // Parse 4-byte status field
            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Byte 4 (bits 24-31)
            char b4b0 = binary.charAt(31);
            char b4b2 = binary.charAt(29);
            char b4b4 = binary.charAt(27);
            char b4b7 = binary.charAt(24);

            // Byte 3 (bits 16-23)
            char b3b0 = binary.charAt(23);
            char b3b1 = binary.charAt(22);
            char b3b2 = binary.charAt(21);

            // Byte 2 (bits 8-15)
            char b2b0 = binary.charAt(15);
            char b2b1 = binary.charAt(14);

            // Byte 1 (bits 0-7)
            char b1b0 = binary.charAt(7);
            char b1b1 = binary.charAt(6);
            char b1b2 = binary.charAt(5);
            char b1b3 = binary.charAt(4);
            char b1b4 = binary.charAt(3);
            char b1b5 = binary.charAt(2);

            return new DeviceStatus(
                    b1b0 == '0',
                    b1b1 == '0',
                    b1b2 == '0',
                    b1b3 == '0',
                    b1b4 == '0',
                    b1b5 == '0',
                    b2b0 == '0',
                    b2b1 == '0',
                    b3b0 == '0',
                    b3b1 == '0',
                    b3b2 == '0',
                    b4b0 == '0',
                    b4b2 == '0',
                    b4b4 == '0',
                    b4b7 == '0'
            );
        } catch (Exception e) {
            return DeviceStatus.allClear();
        }
    }
}
