package org.backblue.ocr;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

// Extracts the text shown in an image (OCR).
public interface TextRecognizer {

    /** @return the recognized text, or null if the image could not be decoded. */
    @Nullable String extractText(File image) throws IOException, InterruptedException;

    /** Provider name, for log messages. */
    String name();
}
