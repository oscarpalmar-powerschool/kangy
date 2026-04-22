package com.kangy.backend;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.WebDetection;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Standalone demo: reads images/sample_image.jpg and asks Google Cloud Vision
 * to identify the character or costume in the picture.
 *
 * <p>Requires Application Default Credentials:
 * run {@code gcloud auth application-default login} before executing,
 * or set the {@code GOOGLE_APPLICATION_CREDENTIALS} env var to a service-account key file.
 */
public class CharacterRecognitionDemo {

    private static final int MAX_RESULTS = 10;

    /** Resolves the image path: explicit arg → ./images/... → ./backend/images/... */
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
        Path imagePath = resolveImagePath(args);
        if (imagePath == null) {
            System.err.println("sample_image.jpg not found. Pass the path as the first argument, "
                + "or run from the backend/ or project root directory.");
            System.exit(1);
        }

        System.out.println("Reading image: " + imagePath.toAbsolutePath());
        ByteString imageBytes = ByteString.copyFrom(Files.readAllBytes(imagePath));
        Image image = Image.newBuilder().setContent(imageBytes).build();

        Feature labelFeature = Feature.newBuilder()
            .setType(Feature.Type.LABEL_DETECTION)
            .setMaxResults(MAX_RESULTS)
            .build();

        Feature webFeature = Feature.newBuilder()
            .setType(Feature.Type.WEB_DETECTION)
            .setMaxResults(MAX_RESULTS)
            .build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
            .setImage(image)
            .addFeatures(labelFeature)
            .addFeatures(webFeature)
            .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            System.out.println("Calling Google Cloud Vision API...\n");
            BatchAnnotateImagesResponse batch = client.batchAnnotateImages(List.of(request));
            AnnotateImageResponse result = batch.getResponsesList().get(0);

            if (result.hasError()) {
                System.err.println("API error: " + result.getError().getMessage());
                System.exit(1);
            }

            System.out.println("=== LABEL DETECTION ===");
            for (EntityAnnotation label : result.getLabelAnnotationsList()) {
                System.out.printf("  %-45s %.1f%%%n", label.getDescription(), label.getScore() * 100);
            }

            WebDetection web = result.getWebDetection();

            System.out.println("\n=== WEB ENTITIES (best character / costume matches) ===");
            for (WebDetection.WebEntity entity : web.getWebEntitiesList()) {
                if (!entity.getDescription().isEmpty()) {
                    System.out.printf("  %-45s score: %.3f%n", entity.getDescription(), entity.getScore());
                }
            }

            System.out.println("\n=== BEST GUESS ===");
            for (WebDetection.WebLabel label : web.getBestGuessLabelsList()) {
                System.out.println("  " + label.getLabel());
            }
        }
    }
}
