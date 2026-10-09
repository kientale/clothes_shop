package lemonadex.project.clothes.features.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param maxAvatarSize largest accepted avatar image
 * @param maxImageSize  largest accepted catalog image (product photo, brand logo, collection cover)
 * @param publicBaseUrl origin used in returned file URLs; blank means the origin of the upload request
 *                      (behind the Vite dev proxy that is the frontend origin, which also proxies /api)
 */
@ConfigurationProperties("app.uploads")
public record UploadProperties(DataSize maxAvatarSize, DataSize maxImageSize, String publicBaseUrl) {
    public UploadProperties {
        if (maxAvatarSize == null) maxAvatarSize = DataSize.ofMegabytes(2);
        if (maxImageSize == null) maxImageSize = DataSize.ofMegabytes(5);
        if (publicBaseUrl != null) publicBaseUrl = publicBaseUrl.strip().replaceAll("/+$", "");
    }
}
