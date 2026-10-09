package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FileUploadIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;

    private static final String UPLOAD = "/api/v1/admin/uploads/avatars";
    private static final byte[] PNG = bytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
    private final List<UUID> createdFiles = new ArrayList<>();
    private final List<UUID> createdCustomers = new ArrayList<>();
    private final List<UUID> createdAccounts = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        createdCustomers.forEach(id -> jdbc.update("delete from customers where id = ?", id));
        createdFiles.forEach(id -> jdbc.update("delete from uploaded_files where id = ?", id));
        createdAccounts.forEach(id -> {
            jdbc.update("delete from customers where account_id = ?", id);
            jdbc.update("delete from accounts where id = ?", id);
        });
    }

    @Test
    void avatarUploadDetectsTheImageTypeAndServesItPubliclyWithSafeHeaders() throws Exception {
        // The client claims text/plain; the PNG signature decides the stored type.
        MvcResult result = mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "me.txt", "text/plain", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("AVATAR_UPLOADED"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(PNG.length))
                .andExpect(jsonPath("$.data.url").value(startsWith("http://localhost/api/v1/files/")))
                .andReturn();
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asString());
        createdFiles.add(id);
        String url = json.readTree(result.getResponse().getContentAsString()).get("data").get("url").asString();

        byte[] served = mvc.perform(get(URI.create(url).getPath()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);
        assertThat(jdbc.queryForObject("select uploaded_by from uploaded_files where id = ?", UUID.class, id))
                .isEqualTo(jdbc.queryForObject("select id from accounts where email = 'admin@example.com'", UUID.class));

        // The absolute URL satisfies the avatarUrl validation of profile requests.
        Map<String, Object> customer = new LinkedHashMap<>(Map.of("fullName", "Avatar Customer", "status", "ACTIVE", "avatarUrl", url));
        MvcResult created = mvc.perform(post("/api/v1/admin/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(customer)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.avatarUrl").value(url)).andReturn();
        createdCustomers.add(UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asString()));
    }

    @Test
    void avatarUploadRejectsNonImagesEmptyAndOversizedFiles() throws Exception {
        String token = adminToken();
        upload(token, new MockMultipartFile("file", "fake.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(token, new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        byte[] large = bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 2 * 1024 * 1024 + 1);
        upload(token, new MockMultipartFile("file", "large.jpg", "image/jpeg", large))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        upload(token, new MockMultipartFile("other", "me.png", "image/png", PNG))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (byte[] header : List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "GIF89a".getBytes(),
                "RIFF\0\0\0\0WEBP".getBytes())) {
            MvcResult ok = upload(token, new MockMultipartFile("file", "x", "application/octet-stream", bytes(header, 32)))
                    .andExpect(status().isCreated()).andReturn();
            createdFiles.add(UUID.fromString(json.readTree(ok.getResponse().getContentAsString()).get("data").get("id").asString()));
        }
        assertThat(jdbc.queryForList("select content_type from uploaded_files where id in (?, ?, ?)", String.class,
                createdFiles.toArray())).containsExactlyInAnyOrder("image/jpeg", "image/gif", "image/webp");
    }

    @Test
    void catalogImagesAcceptLargerFilesThanAvatars() throws Exception {
        String token = adminToken();
        byte[] threeMb = bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 3 * 1024 * 1024);
        upload(token, new MockMultipartFile("file", "photo.jpg", "image/jpeg", threeMb))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        MvcResult ok = mvc.perform(multipart("/api/v1/admin/uploads/images").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", threeMb))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg")).andReturn();
        UUID id = UUID.fromString(json.readTree(ok.getResponse().getContentAsString()).get("data").get("id").asString());
        createdFiles.add(id);
        assertThat(jdbc.queryForObject("select purpose from uploaded_files where id = ?", String.class, id)).isEqualTo("CATALOG_IMAGE");
        byte[] sixMb = bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 5 * 1024 * 1024 + 1);
        mvc.perform(multipart("/api/v1/admin/uploads/images").file(new MockMultipartFile("file", "big.jpg", "image/jpeg", sixMb))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void uploadNeedsAWritePermissionAndUnknownFilesAreNotFound() throws Exception {
        AuthResponse customer = auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", "Password-1234", "Password-1234", "Upload Customer", null, null));
        createdAccounts.add(customer.account().id());
        upload(null, new MockMultipartFile("file", "me.png", "image/png", PNG)).andExpect(status().isUnauthorized());
        upload(customer.accessToken(), new MockMultipartFile("file", "me.png", "image/png", PNG)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/admin/uploads/images").file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer.accessToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/files/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/not-a-uuid")).andExpect(status().isBadRequest());
    }

    private ResultActions upload(String token, MockMultipartFile file) throws Exception {
        var request = multipart(UPLOAD).file(file);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(request);
    }

    private String adminToken() {
        return auth.login(new LoginRequest("admin", "admin123")).accessToken();
    }

    /** {@code prefix} followed by zero padding up to {@code length} bytes. */
    private static byte[] bytes(byte[] prefix, int length) {
        return Arrays.copyOf(prefix, Math.max(length, prefix.length));
    }
}
