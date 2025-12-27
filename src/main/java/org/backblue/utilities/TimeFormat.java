package org.backblue.utilities;

public class TimeFormat {

    private TimeFormat() {}

    public static String relativeTime(Long seconds) {
        if (seconds <= 0) {
            return null;
        }
        return "<t:" + seconds + ":R>";
    }
    public static String formatTimeShort(Long seconds) {
        if (seconds <= 0) {
            return null;
        }

        String returnString = null;

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            returnString = days + "d " + hours + "h " + minutes + "m " + secs + "s";
        } else if (hours > 0) {
            returnString = hours + "h " + minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            returnString = minutes + "m " + secs + "s";
        } else if (secs > 0) {
            returnString = secs + "s";
        }
        return returnString;
    }
}
