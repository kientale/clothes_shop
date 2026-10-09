package lemonadex.project.clothes.features.file.dto;

import java.util.UUID;

/** {@code url} is absolute, so it can be stored directly in an avatarUrl field. */
public record UploadResponse(UUID id, String url, String contentType, long size) {}
