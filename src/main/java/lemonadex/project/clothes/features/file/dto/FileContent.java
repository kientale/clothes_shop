package lemonadex.project.clothes.features.file.dto;

/** Bytes and media type of a stored file, for streaming it back to a client. */
public record FileContent(byte[] data, String contentType) {}
