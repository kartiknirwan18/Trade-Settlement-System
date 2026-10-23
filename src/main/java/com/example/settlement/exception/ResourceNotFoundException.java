package com.example.settlement.exception;

/** Thrown when a requested business entity does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String identifier;

    public ResourceNotFoundException(String resource, Object identifier) {
        super(resource + " not found: " + identifier);
        this.resource = resource;
        this.identifier = String.valueOf(identifier);
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resource = "Resource";
        this.identifier = "";
    }

    public String getResource() {
        return resource;
    }

    public String getIdentifier() {
        return identifier;
    }
}
