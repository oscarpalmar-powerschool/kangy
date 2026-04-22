package com.kangy.backend.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends device images to Gemini for person / costume identification.
 * Only the first image per device is processed — once a description is obtained
 * all subsequent images from that device are ignored.
 */
@Service
public class GeminiService {

    private static final String PROMPT =
        "Describe the person in this image concisely: their approximate age, gender, hair, "
        + "and clothing. If they are wearing a costume or dressed as a recognisable character, "
        + "name the character and the franchise. "
        + "Note: the camera may be mounted upside down — correct for that mentally. "
        + "If the image quality is too poor to make a reliable identification, say so.";

    private final String projectId;
    private final String location;
    private final String model;

    private final ConcurrentHashMap<String, String> descriptions = new ConcurrentHashMap<>();

    public GeminiService(
        @Value("${kangy.gemini.project-id}") String projectId,
        @Value("${kangy.gemini.location}") String location,
        @Value("${kangy.gemini.model}") String model
    ) {
        this.projectId = projectId;
        this.location = location;
        this.model = model;
    }

    /**
     * Processes the image only if no description has been obtained for this device yet.
     * Returns the description on the first successful call, null on all subsequent calls.
     */
    public String describeOnce(String deviceId, byte[] imageBytes) throws IOException {
        if (descriptions.containsKey(deviceId)) {
            System.out.printf("[GEMINI] %s already described — skipping%n", deviceId);
            return null;
        }
        System.out.printf("[GEMINI] %s — calling Gemini (%d bytes)%n", deviceId, imageBytes.length);
        String description = callGemini(imageBytes);
        if (description != null && !description.isBlank()) {
            descriptions.putIfAbsent(deviceId, description);
            System.out.printf("[GEMINI] %s — description stored%n", deviceId);
            return descriptions.get(deviceId);
        }
        System.out.printf("[GEMINI] %s — empty response, will retry on next image%n", deviceId);
        return null;
    }

    /** Returns the stored description for a device, or null if not yet described. */
    public String getDescription(String deviceId) {
        return descriptions.get(deviceId);
    }

    private String callGemini(byte[] imageBytes) throws IOException {
        try (VertexAI vertexAi = new VertexAI(projectId, location)) {
            GenerativeModel generativeModel = new GenerativeModel(model, vertexAi);
            var content = ContentMaker.fromMultiModalData(
                PartMaker.fromMimeTypeAndData("image/jpeg", imageBytes),
                PROMPT
            );
            GenerateContentResponse response = generativeModel.generateContent(content);
            return ResponseHandler.getText(response);
        }
    }
}
