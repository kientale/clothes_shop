package trankien.project.clothes.features.auth.dto;

import trankien.project.clothes.features.account.dto.AccountResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
