package org.backblue.utilities;

import org.jspecify.annotations.NonNull;

public final class TimeFormat {

    private TimeFormat() {}

    public static @NonNull String formattedTime(long seconds, boolean abbreviated, int maxUnits) {
        if (maxUnits <= 0) {
            return "";
        }

        long[] values = getValues(seconds);

        String[] longNames = {
                "year", "month", "week", "day", "hour", "minute", "second"
        };

        String[] shortNames = {"y", "mo", "w", "d", "h", "m", "s"};
        StringBuilder sb = new StringBuilder();
        int unitsAdded = 0;

        for (int i = 0; i < values.length && unitsAdded < maxUnits; i++) {
            long value = values[i];

            if (value == 0 && (unitsAdded > 0 || i != values.length - 1)) {
                continue;
            }

            if (abbreviated) {
                sb.append(String.format("%02d%s", value, shortNames[i]));
            } else {
                if (unitsAdded > 0) {
                    sb.append(", ");
                }
                sb.append(value)
                        .append(" ")
                        .append(longNames[i])
                        .append(value == 1 ? "" : "s");
            }

            unitsAdded++;
        }

        return sb.toString();
    }

    public static String formattedTime(long seconds, boolean abbreviated) {
        return formattedTime(seconds, abbreviated, Integer.MAX_VALUE);
    }

    private static long @NonNull [] getValues(long seconds) {
        final long SECOND = 1;
        final long MINUTE = 60 * SECOND;
        final long HOUR = 60 * MINUTE;
        final long DAY = 24 * HOUR;
        final long WEEK = 7 * DAY;
        final long MONTH = 30 * DAY;
        final long YEAR = 365 * DAY;

        return new long[]{
                seconds / YEAR,
                (seconds % YEAR) / MONTH,
                (seconds % MONTH) / WEEK,
                (seconds % WEEK) / DAY,
                (seconds % DAY) / HOUR,
                (seconds % HOUR) / MINUTE,
                seconds % MINUTE
        };
    }
}
