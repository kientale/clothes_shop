package lemonadex.project.clothes.features.role.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String code, List<PermissionResponse> permissions,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}
