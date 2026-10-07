package lemonadex.project.clothes.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.model.AccountStatus;
import lemonadex.project.clothes.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountSummaryResponse(UUID id, String email, AccountStatus status,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime lastLoginAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt) {}
