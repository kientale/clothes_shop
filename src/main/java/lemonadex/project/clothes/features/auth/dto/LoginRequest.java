package lemonadex.project.clothes.features.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    public LoginRequest {
        // Retain email validation while supporting the requested administrator login name.
        if ("admin".equalsIgnoreCase(email)) email = "admin@example.com";
    }
}
