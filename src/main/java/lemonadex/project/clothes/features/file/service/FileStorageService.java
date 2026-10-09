package lemonadex.project.clothes.features.file.service;

import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.features.file.config.UploadProperties;
import lemonadex.project.clothes.features.file.dto.*;
import lemonadex.project.clothes.features.file.model.*;
import lemonadex.project.clothes.features.file.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileStorageService {
    private final StoredFileRepository files;
    private final UploadProperties properties;

    /**
     * Stores an avatar image; the media type comes from the file's signature, never the client header.
     * @param requestOrigin origin of the upload request, used when no public base URL is configured
     */
    @Transactional
    public UploadResponse storeAvatar(MultipartFile upload, UUID uploaderId, String requestOrigin) {
        return store(upload, FilePurpose.AVATAR, properties.maxAvatarSize(), uploaderId, requestOrigin);
    }

    /** Stores a catalog image (product photo, brand logo, collection cover) under the larger image limit. */
    @Transactional
    public UploadResponse storeCatalogImage(MultipartFile upload, UUID uploaderId, String requestOrigin) {
        return store(upload, FilePurpose.CATALOG_IMAGE, properties.maxImageSize(), uploaderId, requestOrigin);
    }

    /** Stores a photo a customer attaches to a review or a return request (catalog image size limit). */
    @Transactional
    public UploadResponse storeCustomerImage(MultipartFile upload, UUID uploaderId, String requestOrigin) {
        return store(upload, FilePurpose.CUSTOMER_IMAGE, properties.maxImageSize(), uploaderId, requestOrigin);
    }

    private UploadResponse store(MultipartFile upload, FilePurpose purpose, DataSize limit, UUID uploaderId, String requestOrigin) {
        if (upload == null || upload.isEmpty()) throw new BadRequestException("EMPTY_FILE", "The uploaded file is empty");
        if (upload.getSize() > limit.toBytes()) {
            throw new BadRequestException("FILE_TOO_LARGE", "Images can be at most " + limit.toMegabytes() + " MB");
        }
        byte[] data;
        try {
            data = upload.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        String contentType = imageType(data);
        if (contentType == null) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG, WebP or GIF images are accepted");
        }
        StoredFile file = new StoredFile();
        file.setPurpose(purpose);
        file.setContentType(contentType);
        file.setSize(data.length);
        file.setData(data);
        file.setUploadedBy(uploaderId);
        files.saveAndFlush(file);
        String configured = properties.publicBaseUrl();
        String base = configured == null || configured.isEmpty() ? requestOrigin : configured;
        return new UploadResponse(file.getId(), base + "/api/v1/files/" + file.getId(), contentType, data.length);
    }

    public FileContent read(UUID id) {
        StoredFile file = files.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("File"));
        return new FileContent(file.getData(), file.getContentType());
    }

    static String imageType(byte[] data) {
        if (startsWith(data, 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return "image/jpeg";
        if (startsWith(data, 0, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) return "image/png";
        if (startsWith(data, 0, ascii("GIF87a")) || startsWith(data, 0, ascii("GIF89a"))) return "image/gif";
        if (startsWith(data, 0, ascii("RIFF")) && startsWith(data, 8, ascii("WEBP"))) return "image/webp";
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
