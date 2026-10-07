package lemonadex.project.clothes.exception;

import jakarta.servlet.http.HttpServletResponse;
import lemonadex.project.clothes.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiErrorWriter {
    private final ObjectMapper mapper;

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == 401) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message, Map.of()));
    }
}
