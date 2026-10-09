package lemonadex.project.clothes.features.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code email} is the linked account's login email; null for guests and for soft-deleted accounts. */
public record CustomerResponse(UUID id, UUID accountId, String email, String fullName, String phone, String gender,
        @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth,
        String avatarUrl, String status,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}
