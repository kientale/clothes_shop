package lemonadex.project.clothes.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.util.DateTimeUtils;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerProfileResponse(UUID id, String fullName, String phone,
        @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth) {}
