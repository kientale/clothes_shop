package lemonadex.project.clothes.features.content.dto;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.content.model.*;
import java.util.List;

public final class ContentRequests {
    private ContentRequests() {}
    public record ArticleRequest(@NotBlank @Size(max = 255) String title,
            @Size(max = 300) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String thumbnailUrl,
            @NotBlank @Size(max = 100000) String content, @NotNull ArticleType articleType,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String> images) {}
    public record ArticleStatusRequest(@NotNull ArticleStatus status) {}
    public record PolicyRequest(@NotNull PolicyType policyType, @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 100000) String content) {}
    public record PolicyStatusRequest(@NotNull PolicyStatus status) {}
}
