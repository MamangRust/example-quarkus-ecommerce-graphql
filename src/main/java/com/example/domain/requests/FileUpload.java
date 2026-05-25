package com.example.domain.requests;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FileUpload(
    String fileName,
    String contentType,
    String base64Data
) {}
