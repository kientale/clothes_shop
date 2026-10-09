package lemonadex.project.clothes.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import org.slf4j.MDC;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record ApiResponse<T>(boolean success, String code, String message, T data,
        @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime timestamp,
        String requestId, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> errors) {
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, OffsetDateTime.now(ZoneOffset.UTC),
                MDC.get("requestId"), Map.of());
    }

    public static ApiResponse<Void> failure(String code, String message, Map<String, String> errors) {
        return new ApiResponse<>(false, code, message, null, OffsetDateTime.now(ZoneOffset.UTC),
                MDC.get("requestId"), errors);
    }
}
