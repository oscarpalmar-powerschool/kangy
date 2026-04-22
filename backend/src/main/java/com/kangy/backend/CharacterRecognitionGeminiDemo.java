package com.kangy.backend;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Standalone demo: reads images/sample_image.jpg and asks Gemini (via Vertex AI)
 * to identify the person, their clothing, and any costume or character in the picture.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Application Default Credentials — run {@code gcloud auth application-default login}</li>
 *   <li>Environment variable {@code GOOGLE_CLOUD_PROJECT} set to your GCP project ID</li>
 *   <li>Vertex AI API enabled in that project</li>
 * </ul>
 *
 * <p>Optional CLI args:
 * <ol>
 *   <li>Path to the image (default: auto-resolved)</li>
 *   <li>Custom prompt (default: costume/character identification prompt)</li>
 * </ol>
 */
public class CharacterRecognitionGeminiDemo {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String LOCATION = "us-central1";
    private static final String DEFAULT_PROMPT =
        "Analyse this image in full. Note that the camera may be mounted upside down so the image "
        + "may appear rotated — mentally correct for that. Describe everything you see: "
        + "the environment and background (room, furniture, objects on walls), "
        + "the person's full physical appearance (face, hair, build), "
        + "and their complete outfit from head to toe including any accessories. "
        + "If they are wearing a costume or dressed as a recognisable character, identify the "
        + "character and franchise. If not, describe the clothing style and colours in detail. "
        + "Also note any unusual lighting conditions or image quality issues you observe.";

    private static Path resolveImagePath(String[] args) {
        if (args.length > 0) {
            Path p = Path.of(args[0]);
            return Files.exists(p) ? p : null;
        }
        for (String candidate : List.of("./images/sample_image.jpg", "./backend/images/sample_image.jpg")) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (projectId == null || projectId.isBlank()) {
            System.err.println("Set GOOGLE_CLOUD_PROJECT to your GCP project ID.");
            System.exit(1);
        }

        Path imagePath = resolveImagePath(args);
        if (imagePath == null) {
            System.err.println("Image not found. Pass the path as the first argument, "
                + "or run from the backend/ or project root directory.");
            System.exit(1);
        }

        String prompt = args.length > 1 ? args[1] : DEFAULT_PROMPT;

        System.out.println("Image : " + imagePath.toAbsolutePath());
        System.out.println("Model : " + MODEL);
        System.out.println("Prompt: " + prompt);
        System.out.println();

        byte[] imageBytes = Files.readAllBytes(imagePath);

        try (VertexAI vertexAi = new VertexAI(projectId, LOCATION)) {
            GenerativeModel model = new GenerativeModel(MODEL, vertexAi);
            var content = ContentMaker.fromMultiModalData(
                PartMaker.fromMimeTypeAndData("image/jpeg", imageBytes),
                prompt
            );
            GenerateContentResponse response = model.generateContent(content);
            System.out.println("=== GEMINI RESPONSE ===");
            System.out.println(ResponseHandler.getText(response));
        }
    }
}
