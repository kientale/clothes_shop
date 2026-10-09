package lemonadex.project.clothes.features.auth.dto;

import lemonadex.project.clothes.features.account.dto.AccountResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
