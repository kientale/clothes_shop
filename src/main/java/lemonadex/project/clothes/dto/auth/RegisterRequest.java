package lemonadex.project.clothes.dto.auth;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.util.DateTimeUtils;
import java.time.LocalDate;
import java.util.Objects;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "\\+?[0-9]{8,15}") String phone,
        @Past @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth) {
    @AssertTrue(message = "Passwords must match")
    @JsonIgnore
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, confirmPassword);
    }
}
