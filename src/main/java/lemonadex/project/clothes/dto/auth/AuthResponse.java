package lemonadex.project.clothes.dto.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
