package lemonadex.project.clothes.features.file.controller;

import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.features.file.dto.*;
import lemonadex.project.clothes.features.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService service;

    /** Avatar upload for administrator or customer profiles; returns a URL to put in avatarUrl. */
    @PostMapping(path = "/api/v1/admin/uploads/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ACCOUNT_WRITE', 'CUSTOMER_WRITE')")
    ResponseEntity<ApiResponse<UploadResponse>> uploadAvatar(@RequestPart("file") MultipartFile file,
                                                             @AuthenticationPrincipal Jwt jwt) {
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        UploadResponse uploaded = service.storeAvatar(file, UUID.fromString(jwt.getSubject()), origin);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("AVATAR_UPLOADED", "Avatar uploaded", uploaded));
    }

    /** Images for catalog, banners and review moderation; up to 5 MB. */
    @PostMapping(path = "/api/v1/admin/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PRODUCT_WRITE', 'BANNER_WRITE', 'REVIEW_WRITE', 'ARTICLE_WRITE', 'SETTINGS_STORE_WRITE')")
    ResponseEntity<ApiResponse<UploadResponse>> uploadImage(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        UploadResponse uploaded = service.storeCatalogImage(file, UUID.fromString(jwt.getSubject()), origin);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("IMAGE_UPLOADED", "Image uploaded", uploaded));
    }

    /** Photos customers attach to reviews and return requests; up to 5 MB, rate limited per client. */
    @PostMapping(path = "/api/v1/me/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    ResponseEntity<ApiResponse<UploadResponse>> uploadCustomerImage(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        UploadResponse uploaded = service.storeCustomerImage(file, UUID.fromString(jwt.getSubject()), origin);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("IMAGE_UPLOADED", "Image uploaded", uploaded));
    }

    /** Public read: ids are random UUIDs and stored files never change, so they cache forever. */
    @GetMapping("/api/v1/files/{id}")
    ResponseEntity<byte[]> read(@PathVariable UUID id) {
        FileContent content = service.read(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.data().length)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(content.data());
    }
}
