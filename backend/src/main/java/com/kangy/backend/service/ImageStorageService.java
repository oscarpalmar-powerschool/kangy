package com.kangy.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageStorageService {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter FILE_FMT =
      DateTimeFormatter.ofPattern("HH-mm-ss-SSS").withZone(ZoneOffset.UTC);

  private final Path storageRoot;

  public ImageStorageService(@Value("${kangy.images.storage-path}") String storagePath) {
    this.storageRoot = Paths.get(storagePath);
  }

  /**
   * Saves a JPEG image under {root}/{deviceId}/{yyyy-MM-dd}/{HH-mm-ss-SSS}.jpg
   * and returns the path relative to the storage root.
   */
  public String save(String deviceId, byte[] imageBytes) throws IOException {
    Instant now = Instant.now();
    Path dir = storageRoot.resolve(deviceId).resolve(DATE_FMT.format(now));
    Files.createDirectories(dir);
    String filename = FILE_FMT.format(now) + ".jpg";
    Files.write(dir.resolve(filename), imageBytes);
    return deviceId + "/" + DATE_FMT.format(now) + "/" + filename;
  }
}
