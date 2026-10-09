package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminAccountResponse(UUID id, String email, AccountStatus status,
        String fullName, String phone, String avatarUrl, List<String> roles, List<UUID> roleIds,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime lastLoginAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}
