package lemonadex.project.clothes.common.exception;

/** A rejected request whose public error code is supplied by the owning feature (HTTP 400). */
public class BadRequestException extends RuntimeException {
    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
