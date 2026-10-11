package com.example.aipractice.exception;

/**
 * Raised when an AI request would exceed its configured input-token budget.
 */
public class ContextBudgetExceededException extends RuntimeException {

    public ContextBudgetExceededException(String message) {
        super(message);
    }
}
