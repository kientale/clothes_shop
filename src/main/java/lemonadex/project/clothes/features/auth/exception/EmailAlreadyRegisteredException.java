package lemonadex.project.clothes.features.auth.exception;

import lemonadex.project.clothes.common.exception.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {
    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }
}
