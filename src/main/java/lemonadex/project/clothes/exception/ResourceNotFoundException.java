package lemonadex.project.clothes.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource) {
        super(resource + " was not found");
    }
}
