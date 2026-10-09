package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
import lemonadex.project.clothes.features.account.model.AccountStatus;

public record AdminAccountUpdateRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "\\+?[0-9]{8,15}") String phone,
        @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String avatarUrl,
        @NotNull AccountStatus status,
        @NotEmpty @Size(max = 50) Set<@NotNull UUID> roleIds) {}
