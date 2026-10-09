package lemonadex.project.clothes.features.auth.controller;

import jakarta.validation.Valid;
import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.features.auth.security.CurrentUser;
import lemonadex.project.clothes.features.auth.dto.PasswordResetRequests.*;
import lemonadex.project.clothes.features.auth.dto.SocialLoginRequests.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import lemonadex.project.clothes.features.auth.service.EmailVerificationService;
import lemonadex.project.clothes.features.auth.service.PasswordResetService;
import lemonadex.project.clothes.features.auth.service.SocialLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final PasswordResetService passwordReset;
    private final EmailVerificationService verification;
    private final SocialLoginService social;

    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("REGISTER_SUCCESS", "Registration successful", auth.register(request)));
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("LOGIN_SUCCESS", "Login successful", auth.login(request)));
    }

    /** Always 202, whether or not the email has an account, so the endpoint does not reveal who is registered. */
    @PostMapping("/password/forgot")
    ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.requestLink(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("PASSWORD_RESET_REQUESTED", "If the email is registered, a reset link has been sent", null));
    }

    @PostMapping("/password/reset")
    ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("PASSWORD_RESET_SUCCESS", "Password updated", null));
    }

    @PostMapping("/email/verify")
    ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        verification.verify(request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("EMAIL_VERIFIED", "Email verified", null));
    }

    @PostMapping("/email/resend")
    @PreAuthorize("hasAuthority('AUTH_PROFILE_READ')")
    ResponseEntity<ApiResponse<Void>> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        verification.resend(CurrentUser.id(jwt));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("VERIFICATION_SENT", "A new verification link has been sent", null));
    }

    /** Sign-in buttons the shop should show (public ids only). */
    @GetMapping("/providers")
    ResponseEntity<ApiResponse<SocialProviders>> providers() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("SUCCESS", "Sign-in providers", social.providers()));
    }

    @PostMapping("/google")
    ResponseEntity<ApiResponse<AuthResponse>> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("LOGIN_SUCCESS", "Login successful", social.google(request)));
    }

    @PostMapping("/facebook")
    ResponseEntity<ApiResponse<AuthResponse>> facebook(@Valid @RequestBody FacebookLoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("LOGIN_SUCCESS", "Login successful", social.facebook(request)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('AUTH_PROFILE_READ')")
    ResponseEntity<ApiResponse<AccountResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("PROFILE_SUCCESS", "Account profile", auth.me(CurrentUser.id(jwt))));
    }
}
