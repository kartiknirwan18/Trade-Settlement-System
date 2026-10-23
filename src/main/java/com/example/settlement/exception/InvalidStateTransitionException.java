package com.example.settlement.exception;

/** Thrown when an entity is asked to move to an illegal state (e.g. PAID -> ISSUED). Maps to HTTP 409. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String entity, Object currentState, Object targetState) {
        super("Invalid " + entity + " state transition: " + currentState + " -> " + targetState);
    }

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
