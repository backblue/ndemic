package org.backblue.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class TesseractOCR implements TextRecognizer {

    private static final Logger Log = LoggerFactory.getLogger(TesseractOCR.class);

    // Tesseract instances are not thread-safe; calls are serialized on it.
    private final ITesseract tesseract;

    public TesseractOCR(String dataPath) {
        tesseract = new Tesseract();
        tesseract.setDatapath(dataPath);
        tesseract.setLanguage("eng");
        tesseract.setOcrEngineMode(1);
        tesseract.setPageSegMode(11);
    }

    @Override
    public @Nullable String extractText(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            Log.error("Unable to decode image: {}", file.getName());
            return null;
        }
        try {
            synchronized (tesseract) {
                return tesseract.doOCR(image).strip();
            }
        } catch (TesseractException e) {
            throw new IOException("Tesseract failed on " + file.getName(), e);
        } finally {
            image.flush();
        }
    }

    @Override
    public String name() {
        return "Tesseract";
    }
}
