package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AccountResponse(UUID id, String email, boolean emailVerified, AccountStatus status, List<String> roles,
        List<String> permissions, CustomerProfileResponse customer, String avatarUrl,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime lastLoginAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}
