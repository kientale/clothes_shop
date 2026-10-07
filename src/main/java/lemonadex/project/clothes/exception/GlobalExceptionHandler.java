package lemonadex.project.clothes.exception;

import jakarta.validation.ConstraintViolationException;
import lemonadex.project.clothes.dto.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.*;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> missing(ResourceNotFoundException ex) {
        return failure(404, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(EmailAlreadyRegisteredException ex) {
        return failure(409, "EMAIL_ALREADY_REGISTERED", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> credentials(BadCredentialsException ex) {
        return failure(401, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> illegal(IllegalArgumentException ex) {
        return failure(400, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_FAILED", "Request validation failed", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception ex) {
        return failure(400, "INVALID_REQUEST", "The request contains missing or invalid values");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException ex) {
        String cause = Objects.toString(NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), "");
        return cause.contains("uq_accounts_email_lower")
                ? failure(409, "EMAIL_ALREADY_REGISTERED", "Email is already registered")
                : failure(409, "DATA_CONFLICT", "The request conflicts with existing account data");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        return failure(403, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> noRoute(NoResourceFoundException ex) {
        return failure(404, "RESOURCE_NOT_FOUND", "Endpoint was not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> method(HttpRequestMethodNotSupportedException ex) {
        return failure(405, "METHOD_NOT_ALLOWED", "HTTP method is not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> media(HttpMediaTypeNotSupportedException ex) {
        return failure(415, "UNSUPPORTED_MEDIA_TYPE", "Request content type is not supported");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return failure(500, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> failure(int status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, Map.of()));
    }
}
