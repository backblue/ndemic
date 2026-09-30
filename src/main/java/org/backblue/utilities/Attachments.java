package org.backblue.utilities;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Attachments {

    private static final Logger Log = LoggerFactory.getLogger(Attachments.class);

    private Attachments() {}

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
