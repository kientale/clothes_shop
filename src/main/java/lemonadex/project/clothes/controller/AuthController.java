package lemonadex.project.clothes.controller;

import jakarta.validation.Valid;
import lemonadex.project.clothes.dto.auth.*;
import lemonadex.project.clothes.dto.common.ApiResponse;
import lemonadex.project.clothes.security.CurrentUser;
import lemonadex.project.clothes.service.AuthService;
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

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('AUTH_PROFILE_READ')")
    ResponseEntity<ApiResponse<AccountResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("PROFILE_SUCCESS", "Account profile", auth.me(CurrentUser.id(jwt))));
    }
}
