package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record AdminAccountCreateRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "\\+?[0-9]{8,15}") String phone,
        @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String avatarUrl,
        @NotEmpty @Size(max = 50) Set<@NotNull UUID> roleIds) {}
