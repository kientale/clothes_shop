package lemonadex.project.clothes.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.model.AccountStatus;
import lemonadex.project.clothes.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AccountResponse(UUID id, String email, AccountStatus status, List<String> roles,
        List<String> permissions, CustomerProfileResponse customer,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime lastLoginAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}
