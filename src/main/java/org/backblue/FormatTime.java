package org.backblue;


public class FormatTime {

    public static String relativeTime(String seconds) {
        return relativeTime(Long.parseLong(seconds));
    }

    public static String relativeTime(Long seconds) {
        if (seconds <= 0) {
            return null;
        }
        return "<t:" + seconds + ":R>";
    }

    public static String formatTimeFull(String seconds) {
        return formatTimeFull(Long.parseLong(seconds));
    }

    public static String formatTimeFull(Long seconds) {
        if (seconds <= 0) {
            return null;
        }

        String returnString = null;

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            returnString = days + " days, " + hours + " hours, " + minutes + " minutes, and " + secs + " seconds";
        } else if (hours > 0) {
            returnString = hours + " hours, " + minutes + " minutes, and " + secs + " seconds";
        } else if (minutes > 0) {
            returnString = minutes + " minutes, and " + secs + " seconds";
        } else if (secs > 0) {
            returnString = secs + " seconds";
        }

        if (hours == 1) {
            returnString = returnString.replace("hours", "hour");
        }
        if (days == 1) {
            returnString = returnString.replace("days", "day");
        }
        if (minutes == 1) {
            returnString = returnString.replace("minutes", "minute");
        }
        if (secs == 1) {
            returnString = returnString.replace("seconds", "second");
        }

        return returnString;
    }

}
