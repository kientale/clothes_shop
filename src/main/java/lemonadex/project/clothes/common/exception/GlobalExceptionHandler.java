package lemonadex.project.clothes.common.exception;

import jakarta.validation.ConstraintViolationException;
import lemonadex.project.clothes.common.dto.ApiResponse;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(ConflictException ex) {
        return failure(409, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiResponse<Void>> badRequest(BadRequestException ex) {
        return failure(400, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex) {
        return failure(413, "FILE_TOO_LARGE", "The uploaded file exceeds the size limit");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ApiResponse<Void>> multipart(Exception ex) {
        return failure(400, "INVALID_REQUEST", "A multipart request with a 'file' part is required");
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
        if (cause.contains("uq_accounts_email_lower")) return failure(409, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
        if (cause.contains("uq_roles_code")) return failure(409, "ROLE_CODE_EXISTS", "Role code already exists");
        if (cause.contains("uq_customers_account")) return failure(409, "CUSTOMER_ACCOUNT_ALREADY_LINKED", "Account already has a customer profile");
        // Catalog uniqueness covers soft-deleted rows too, so an old slug or SKU never points at something new.
        if (cause.contains("uq_categories_slug") || cause.contains("uq_brands_slug") || cause.contains("uq_collections_slug")
                || cause.contains("uq_products_slug")) return failure(409, "SLUG_EXISTS", "Slug is already used");
        if (cause.contains("uq_colors_code") || cause.contains("uq_sizes_code")) return failure(409, "CODE_EXISTS", "Code is already used");
        if (cause.contains("uq_products_code")) return failure(409, "PRODUCT_CODE_EXISTS", "Product code is already used");
        if (cause.contains("uq_product_variants_sku")) return failure(409, "SKU_EXISTS", "SKU is already used");
        if (cause.contains("uq_product_variants_attributes")) return failure(409, "VARIANT_EXISTS", "This product already has a variant with that color and size");
        if (cause.contains("uq_inventories_warehouse_variant")) return failure(409, "STOCK_EXISTS", "This warehouse already has a stock record for the variant");
        if (cause.contains("uq_coupons_code_lower")) return failure(409, "COUPON_CODE_EXISTS", "Coupon code already exists");
        if (cause.contains("uq_articles_slug")) return failure(409, "ARTICLE_SLUG_EXISTS", "Article slug already exists");
        if (cause.contains("uq_store_policies_type_version") || cause.contains("uq_store_policies_active_type"))
            return failure(409, "POLICY_VERSION_CONFLICT", "Policy version or active policy already exists");
        if (cause.contains("uq_product_reviews_order_item")) return failure(409, "ORDER_ITEM_ALREADY_REVIEWED", "This order item has already been reviewed");
        if (cause.contains("uq_orders_code")) return failure(409, "ORDER_CODE_EXISTS", "Order code is already used");
        if (cause.contains("uq_shipments_provider_tracking")) return failure(409, "TRACKING_CODE_EXISTS", "This carrier tracking code is already used");
        if (cause.contains("uq_payment_transactions_provider_id") || cause.contains("uq_payment_transactions_code"))
            return failure(409, "PAYMENT_TRANSACTION_EXISTS", "The payment transaction has already been recorded");
        return failure(409, "DATA_CONFLICT", "The request conflicts with existing data");
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
