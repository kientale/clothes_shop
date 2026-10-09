package lemonadex.project.clothes.features.auth.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.util.Objects;

public final class PasswordResetRequests {
    private PasswordResetRequests() {}

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 32, max = 128) String token,
            @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
        @AssertTrue(message = "Passwords must match")
        @JsonIgnore
        public boolean isPasswordConfirmed() {
            return Objects.equals(password, confirmPassword);
        }
    }
}
