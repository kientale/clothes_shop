package lemonadex.project.clothes.features.role.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record RoleCreateRequest(@NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}") String code,
        @NotNull @Size(max = 100) Set<@NotNull UUID> permissionIds) {}
