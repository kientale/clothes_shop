package lemonadex.project.clothes.features.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A customer login account that has never had a customer profile, so one can be linked to it. */
public record LinkableAccountResponse(UUID id, String email,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt) {}
