package com.kangy.backend.api;

import com.kangy.backend.api.dto.DeviceActionAckRequest;
import com.kangy.backend.api.dto.DeviceActionAckResponse;
import com.kangy.backend.api.dto.DeviceActionEnqueueRequest;
import com.kangy.backend.api.dto.DeviceActionEnqueueResponse;
import com.kangy.backend.api.dto.DeviceActionPollResponse;
import com.kangy.backend.api.dto.DevicePendingAction;
import com.kangy.backend.api.dto.DeviceRegistrationRequest;
import com.kangy.backend.api.dto.DeviceRegistrationResponse;
import com.kangy.backend.api.dto.DeviceStatusGetResponse;
import com.kangy.backend.api.dto.DeviceStatusPublishRequest;
import com.kangy.backend.api.dto.DeviceStatusPublishResponse;
import com.kangy.backend.api.dto.RegisteredDeviceDto;
import com.kangy.backend.service.DeviceActionQueue;
import com.kangy.backend.service.DeviceRegistry;
import com.kangy.backend.service.DeviceStatusStore;
import com.kangy.backend.service.ImageStorageService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

  private static final int MAX_TTS_CACHE_SIZE = 20;

  private static final String[] GREETING_MESSAGES = {
      "Hey there, good to see you!",
      "Welcome back!",
      "Hello, how are you doing?",
      "Hi! Hope you're having a great day.",
      "Good to have you here!"
  };

  private static final Random RANDOM = new Random();

  private final Map<String, byte[]> ttsCache = Collections.synchronizedMap(
      new LinkedHashMap<String, byte[]>(MAX_TTS_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
          return size() > MAX_TTS_CACHE_SIZE;
        }
      }
  );

  private final DeviceRegistry deviceRegistry;
  private final DeviceStatusStore deviceStatusStore;
  private final DeviceActionQueue deviceActionQueue;
  private final ImageStorageService imageStorageService;

  @Value("${kangy.security.device-registration-token}")
  private String deviceRegistrationToken;

  @Value("${kangy.security.frontend-api-key}")
  private String frontendApiKey;

  public DeviceController(
      DeviceRegistry deviceRegistry,
      DeviceStatusStore deviceStatusStore,
      DeviceActionQueue deviceActionQueue,
      ImageStorageService imageStorageService
  ) {
    this.deviceRegistry = deviceRegistry;
    this.deviceStatusStore = deviceStatusStore;
    this.deviceActionQueue = deviceActionQueue;
    this.imageStorageService = imageStorageService;
  }

  @PostMapping(value = "/{deviceId}/image", consumes = "image/jpeg")
  public ResponseEntity<Map<String, String>> uploadImage(
      @PathVariable String deviceId,
      @RequestHeader(value = "X-Registration-Token", required = false) String registrationToken,
      @RequestBody byte[] imageBytes
  ) throws java.io.IOException {
    System.err.println("[IMAGE] POST /" + deviceId + "/image — " + (imageBytes == null ? 0 : imageBytes.length) + " bytes, token=" + (registrationToken != null ? "present" : "MISSING"));
    if (!deviceRegistrationToken.equals(registrationToken)) {
      System.err.println("[IMAGE] REJECTED — bad token");
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    deviceRegistry.findByDeviceId(deviceId)
        .orElseThrow(() -> {
          System.err.println("[IMAGE] REJECTED — unknown deviceId: " + deviceId);
          return new IllegalArgumentException("Unknown deviceId: " + deviceId);
        });

    String savedPath = imageStorageService.save(deviceId, imageBytes);
    System.err.println("[IMAGE] SAVED — " + savedPath);
    return ResponseEntity.ok(Map.of(
        "deviceId", deviceId,
        "path", savedPath,
        "status", "SAVED"
    ));
  }

  @GetMapping("/speak")
  public ResponseEntity<byte[]> speak(
      @RequestHeader(value = "Authorization", required = false) String authorization
  ) throws Exception {
    String token = extractBearerToken(authorization);
    boolean validToken = token != null
        && deviceRegistry.list().stream().anyMatch(d -> deviceRegistry.tokenMatches(d.deviceId(), token));
    if (!validToken) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    String text = GREETING_MESSAGES[RANDOM.nextInt(GREETING_MESSAGES.length)];

    byte[] wav = ttsCache.get(text);
    if (wav == null) {
      try (TextToSpeechClient client = TextToSpeechClient.create()) {
        SynthesisInput input = SynthesisInput.newBuilder()
            .setText(text)
            .build();

        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
            .setLanguageCode("en-US")
            .setSsmlGender(SsmlVoiceGender.NEUTRAL)
            .build();

        AudioConfig audioConfig = AudioConfig.newBuilder()
            .setAudioEncoding(AudioEncoding.LINEAR16)
            .setSampleRateHertz(16000)
            .build();

        SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voice, audioConfig);
        byte[] pcm = response.getAudioContent().toByteArray();
        wav = buildWav(pcm, 16000, 1, 16);
        ttsCache.put(text, wav);
      }
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("audio/wav"));
    headers.setContentLength(wav.length);

    return ResponseEntity.ok().headers(headers).body(wav);
  }

  private static byte[] buildWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
    int byteRate = sampleRate * channels * bitsPerSample / 8;
    int blockAlign = channels * bitsPerSample / 8;
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(44 + pcm.length)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    buf.put(new byte[]{'R', 'I', 'F', 'F'});
    buf.putInt(36 + pcm.length);
    buf.put(new byte[]{'W', 'A', 'V', 'E'});
    buf.put(new byte[]{'f', 'm', 't', ' '});
    buf.putInt(16);
    buf.putShort((short) 1);
    buf.putShort((short) channels);
    buf.putInt(sampleRate);
    buf.putInt(byteRate);
    buf.putShort((short) blockAlign);
    buf.putShort((short) bitsPerSample);
    buf.put(new byte[]{'d', 'a', 't', 'a'});
    buf.putInt(pcm.length);
    buf.put(pcm);
    return buf.array();
  }

  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceRegistrationResponse registerDevice(
      @RequestHeader(value = "X-Registration-Token", required = false) String registrationToken,
      @Valid @RequestBody DeviceRegistrationRequest request) {
    if (!deviceRegistrationToken.equals(registrationToken)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    DeviceRegistry.RegisteredDevice registered = deviceRegistry.register(request);

    return new DeviceRegistrationResponse(
        registered.deviceId(),
        registered.token(),
        "SUCCESS"
    );
  }

  @GetMapping
  public List<RegisteredDeviceDto> listRegisteredDevices(
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
    if (!frontendApiKey.equals(apiKey)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    return deviceRegistry.list().stream()
        .map(d -> new RegisteredDeviceDto(
            d.deviceId(),
            d.deviceType().name(),
            d.registeredAt(),
            d.inputCapabilities(),
            d.outputCapabilities()
        ))
        .toList();
  }

  @PostMapping(value = "/{deviceId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceStatusPublishResponse publishDeviceStatus(
      @PathVariable String deviceId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody DeviceStatusPublishRequest request
  ) {
    String token = extractBearerToken(authorization);
    if (!deviceRegistry.tokenMatches(deviceId, token)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }

    int acked = 0;
    if (request.ackedActionIds() != null && !request.ackedActionIds().isEmpty()) {
      acked = deviceActionQueue.ack(deviceId, request.ackedActionIds());
    }

    deviceStatusStore.append(deviceId, request);

    int actionLimit = request.actionLimit() == null ? 25 : Math.max(0, Math.min(request.actionLimit(), 100));
    List<DevicePendingAction> actions = deviceActionQueue.poll(deviceId, actionLimit).stream()
        .map(DeviceController::toPendingAction)
        .toList();

    return new DeviceStatusPublishResponse(
        deviceId,
        Instant.now().toString(),
        "ACCEPTED",
        actions,
        acked
    );
  }

  @GetMapping("/{deviceId}/status")
  public DeviceStatusGetResponse getLatestDeviceStatus(
      @PathVariable String deviceId,
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "50") int limit
  ) {
    if (!frontendApiKey.equals(apiKey)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    DeviceRegistry.RegisteredDevice device = deviceRegistry.findByDeviceId(deviceId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown deviceId: " + deviceId));

    DeviceStatusStore.StatusReport latest = deviceStatusStore.getLatest(deviceId);

    return new DeviceStatusGetResponse(
        deviceId,
        device.deviceType().name(),
        device.registeredAt(),
        deviceStatusStore.getLastSeenAt(deviceId),
        latest == null ? null : new DeviceStatusGetResponse.StatusReport(latest.reportedAt(), latest.observations()),
        deviceStatusStore.getMostRecentFirst(deviceId, limit).stream()
            .map(r -> new DeviceStatusGetResponse.StatusReport(r.reportedAt(), r.observations()))
            .toList()
    );
  }

  @GetMapping("/{deviceId}/actions")
  public DeviceActionPollResponse pollDeviceActions(
      @PathVariable String deviceId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "25") int limit
  ) {
    String token = extractBearerToken(authorization);
    if (!deviceRegistry.tokenMatches(deviceId, token)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }

    int safeLimit = Math.max(0, Math.min(limit, 100));
    List<DevicePendingAction> actions = deviceActionQueue.poll(deviceId, safeLimit).stream()
        .map(DeviceController::toPendingAction)
        .toList();

    return new DeviceActionPollResponse(
        deviceId,
        actions,
        Instant.now().toString()
    );
  }

  @PostMapping(value = "/{deviceId}/actions:ack", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceActionAckResponse ackDeviceActions(
      @PathVariable String deviceId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody DeviceActionAckRequest request
  ) {
    String token = extractBearerToken(authorization);
    if (!deviceRegistry.tokenMatches(deviceId, token)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    int removed = deviceActionQueue.ack(deviceId, request.actionIds());
    return new DeviceActionAckResponse(deviceId, removed, "ACKED");
  }

  @PostMapping(value = "/{deviceId}/actions:enqueue", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceActionEnqueueResponse enqueueDeviceActionsForDevice(
      @PathVariable String deviceId,
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @Valid @RequestBody DeviceActionEnqueueRequest request
  ) {
    if (!frontendApiKey.equals(apiKey)) {
      throw new UnauthorizedException("UNAUTHORIZED");
    }
    deviceRegistry.findByDeviceId(deviceId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown deviceId: " + deviceId));

    int count = deviceActionQueue.enqueue(
        deviceId,
        request.actions().stream()
            .peek(DeviceController::validateKnownAction)
            .map(a -> new DeviceActionQueue.Action(a.type(), a.payload()))
            .toList()
    ).size();

    return new DeviceActionEnqueueResponse(deviceId, count, "ENQUEUED");
  }

  private static void validateKnownAction(DeviceActionEnqueueRequest.Action action) {
    if (action == null || action.type() == null) {
      return;
    }
    String type = action.type();
    if (!Objects.equals(type, "servo.setPosition") && !Objects.equals(type, "led.command")) {
      return; // unknown types allowed for future expansion
    }

    if (!(action.payload() instanceof Map<?, ?> rawMap)) {
      throw new IllegalArgumentException("Action payload must be an object for type: " + type);
    }

    String id = asNonBlankString(rawMap.get("id"));
    if (id == null) {
      throw new IllegalArgumentException("Action payload.id is required for type: " + type);
    }

    if (Objects.equals(type, "servo.setPosition")) {
      Number degrees = asNumber(rawMap.get("degrees"));
      if (degrees == null) {
        throw new IllegalArgumentException("Action payload.degrees is required for type: servo.setPosition");
      }
      double d = degrees.doubleValue();
      if (d < 0 || d > 180) {
        throw new IllegalArgumentException("Action payload.degrees must be between 0 and 180");
      }
      return;
    }

    // led.command
    String mode = asNonBlankString(rawMap.get("mode"));
    if (mode == null) {
      throw new IllegalArgumentException("Action payload.mode is required for type: led.command");
    }
    if (!Objects.equals(mode, "on") && !Objects.equals(mode, "off") && !Objects.equals(mode, "blink")) {
      throw new IllegalArgumentException("Action payload.mode must be one of: on, off, blink");
    }

    Number durationMs = asNumber(rawMap.get("durationMs"));
    if (durationMs != null && durationMs.longValue() < 0) {
      throw new IllegalArgumentException("Action payload.durationMs must be >= 0");
    }
    Number periodMs = asNumber(rawMap.get("periodMs"));
    if (periodMs != null && periodMs.longValue() <= 0) {
      throw new IllegalArgumentException("Action payload.periodMs must be > 0");
    }
    Number count = asNumber(rawMap.get("count"));
    if (count != null && count.longValue() <= 0) {
      throw new IllegalArgumentException("Action payload.count must be > 0");
    }
  }

  private static String asNonBlankString(Object v) {
    if (!(v instanceof String s)) {
      return null;
    }
    String t = s.trim();
    return t.isBlank() ? null : t;
  }

  private static Number asNumber(Object v) {
    return (v instanceof Number n) ? n : null;
  }

  private static DevicePendingAction toPendingAction(DeviceActionQueue.PendingAction a) {
    return new DevicePendingAction(
        a.actionId(),
        a.type(),
        a.payload(),
        a.createdAt()
    );
  }

  private static String extractBearerToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String prefix = "Bearer ";
    if (!authorization.startsWith(prefix)) {
      return null;
    }
    String token = authorization.substring(prefix.length()).trim();
    return token.isBlank() ? null : token;
  }
}

