package com.surf.tracker.devicecomm.protocol.hq;

import com.surf.tracker.devicecomm.protocol.Frame;

/** Shared handling of the {@code *XX,<id>,<type>,...#} text frame family. */
public final class HqFrame {
    private HqFrame() {
    }

    /**
     * Returns the frame as text only if it is bounded printable ASCII with '#' solely as the last
     * character; otherwise null. Never throws, so it is safe inside {@code recognizes}.
     */
    public static String text(Frame frame, int maxLength) {
        if (frame.length() > maxLength) return null;
        byte[] bytes = frame.bytes();
        for (int i = 0; i < bytes.length; i++) {
            int c = bytes[i];
            if (c < 32 || c > 126 || (c == '#' && i != bytes.length - 1)) return null;
        }
        return frame.ascii();
    }

    /** Strips the leading '*' and trailing '#', keeping empty trailing fields. */
    public static String[] split(String text) {
        return text.substring(1, text.length() - 1).split(",", -1);
    }

    /** A short non-negative integer field, or null when absent or not numeric. */
    public static Integer intField(String[] parts, int index) {
        if (index >= parts.length || !parts[index].matches("[0-9]{1,5}")) return null;
        return Integer.parseInt(parts[index]);
    }
}
