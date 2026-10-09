package lemonadex.project.clothes.features.role.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record RoleUpdateRequest(@NotBlank @Size(max = 100) String name,
        @NotNull @Size(max = 100) Set<@NotNull UUID> permissionIds) {}
