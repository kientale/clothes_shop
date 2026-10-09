package lemonadex.project.clothes.features.auth.dto;

import jakarta.validation.constraints.*;

public final class SocialLoginRequests {
    private SocialLoginRequests() {}

    /** The ID token ("credential") returned by the Google Identity Services button. */
    public record GoogleLoginRequest(@NotBlank @Size(max = 8192) String credential) {}

    /** The user access token returned by the Facebook JavaScript SDK. */
    public record FacebookLoginRequest(@NotBlank @Size(max = 4096) String accessToken) {}

    public record EmailVerificationRequest(@NotBlank @Size(min = 32, max = 128) String token) {}

    /** Which sign-in buttons the shop should show; ids only, never secrets. */
    public record SocialProviders(String googleClientId, String facebookAppId) {}

    /** What a provider vouched for after its token checked out. */
    public record VerifiedIdentity(String provider, String subject, String email, boolean emailVerified, String name) {}
}
