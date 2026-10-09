package lemonadex.project.clothes.features.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.customer.model.CustomerStatus;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerCreateRequest(UUID accountId,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "\\+?[0-9]{8,15}") String phone,
        @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
        @Past @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth,
        @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String avatarUrl,
        @NotNull CustomerStatus status) {}
