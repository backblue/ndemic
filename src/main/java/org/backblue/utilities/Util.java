package org.backblue.utilities;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Util {

    private static final Logger Log = LoggerFactory.getLogger(Util.class);

    private Util() {
        throw new UnsupportedOperationException("Util cannot be instantiated");
    }

    // Time formatting

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

    // Resources

    public static String readResource(String path) throws IOException {
        try (InputStream stream = Util.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new String(stream.readAllBytes());
        }
    }

    // Writes to a temporary sibling file, flushes it to disk, then renames it over the target,
    // so a crash mid-write leaves either the old file or the new one, never a truncated one.
    public static void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                Channels.newOutputStream(channel).write(content.getBytes(StandardCharsets.UTF_8));
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // Attachments

    public static List<FileUpload> toUploads(List<Message.Attachment> attachmentList) {
        List<CompletableFuture<FileUpload>> futures = attachmentList.stream()
                .map(attachment ->
                        attachment.getProxy()
                                .download()
                                .thenApply(file ->
                                        FileUpload.fromData(file, attachment.getFileName())
                                )
                                .exceptionally(e -> {
                                    Log.error("Failed to download attachment: ", e);
                                    return null;
                                })
                )
                .toList();

        return futures.stream()
                .filter(Objects::nonNull)
                .map(CompletableFuture::join)
                .toList();
    }
}
