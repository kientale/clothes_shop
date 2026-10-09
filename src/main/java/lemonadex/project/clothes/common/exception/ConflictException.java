package lemonadex.project.clothes.common.exception;

/** A business conflict whose public error code is supplied by the owning feature. */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
