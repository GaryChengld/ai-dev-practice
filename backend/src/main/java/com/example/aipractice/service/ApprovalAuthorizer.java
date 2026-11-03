package com.example.aipractice.service;

import java.security.Principal;

/**
 * Authorization boundary for consuming escalation proposals.
 * Implementations must deny unauthenticated or unauthorized callers before returning.
 * A real authentication/authorization implementation must be supplied before exposing
 * the demo controller; a client-supplied identity is not authentication.
 */
@FunctionalInterface
public interface ApprovalAuthorizer {
    void requireCanEscalate(Principal principal);
}
