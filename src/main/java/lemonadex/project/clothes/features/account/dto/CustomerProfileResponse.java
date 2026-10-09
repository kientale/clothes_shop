package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerProfileResponse(UUID id, String fullName, String phone,
        @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth) {}
