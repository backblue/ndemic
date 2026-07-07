package org.backblue.cloud;

import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.DetectedTextBlock;
import com.azure.ai.vision.imageanalysis.models.DetectedTextLine;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.BinaryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

public class CloudOCR {

    private static final Logger log = LoggerFactory.getLogger(CloudOCR.class);
    private ImageAnalysisClient client;

    public CloudOCR(String endpoint, String key) {

        try {
            this.client = new ImageAnalysisClientBuilder()
                    .endpoint(endpoint)
                    .credential(new KeyCredential(key))
                    .buildClient();
        } catch (Exception e) {
            client = null;
            log.error("Error initiating Cloud OCR (Azure Vision)", e);
        }

    }

    public boolean enabled() {
        return this.client != null;
    }

    public String extractText(File file) throws IOException {
        byte[] imageBytes = Files.readAllBytes(file.toPath());
        BinaryData imageData = BinaryData.fromBytes(imageBytes);
        ImageAnalysisResult result = client.analyze(
                imageData,
                Collections.singletonList(VisualFeatures.READ),
                null
        );

        StringBuilder str = new StringBuilder();
        if (result.getRead() != null) {
            for (DetectedTextBlock block : result.getRead().getBlocks()) {
                for (DetectedTextLine line : block.getLines()) {
                    str.append(line.getText());
                }
            }
        }
        return str.toString().trim();
    }

}