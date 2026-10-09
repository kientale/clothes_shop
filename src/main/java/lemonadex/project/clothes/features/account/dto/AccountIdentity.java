package lemonadex.project.clothes.features.account.dto;

import java.util.List;
import java.util.UUID;

public record AccountIdentity(UUID id, String email, List<String> roles, List<String> permissions) {}
