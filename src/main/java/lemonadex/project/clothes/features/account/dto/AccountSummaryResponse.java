package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountSummaryResponse(UUID id, String email, AccountStatus status,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime lastLoginAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt) {}
