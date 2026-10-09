package lemonadex.project.clothes.features.role.dto;

import java.util.UUID;

public record PermissionResponse(UUID id, String name, String code, String module) {}
