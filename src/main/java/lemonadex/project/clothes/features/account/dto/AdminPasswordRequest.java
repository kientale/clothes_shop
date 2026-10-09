package lemonadex.project.clothes.features.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record AdminPasswordRequest(@NotBlank @Size(min = 8, max = 72)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {}
