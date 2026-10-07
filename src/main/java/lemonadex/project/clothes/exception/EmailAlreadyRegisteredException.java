package lemonadex.project.clothes.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Email is already registered");
    }
}
