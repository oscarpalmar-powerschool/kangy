package com.kangy.backend.api;

import java.util.Map;

public record ApiError(
    String message,
    Map<String, String> fieldErrors
) {}

