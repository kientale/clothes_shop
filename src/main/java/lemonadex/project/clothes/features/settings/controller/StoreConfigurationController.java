package lemonadex.project.clothes.features.settings.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.settings.dto.SettingsRequests.*;
import lemonadex.project.clothes.features.settings.dto.SettingsResponses.*;
import lemonadex.project.clothes.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/store")
public class StoreConfigurationController {
    private final SettingsService service;
    @GetMapping("/configuration")
    ResponseEntity<ApiResponse<StoreConfigurationResponse>> configuration() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", service.publicConfiguration()));
    }
}
