package com.example.aipractice.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

/** Uses the security context established by Spring Security, never a supplied identity. */
public class SpringSecurityApprovalAuthorizer implements ApprovalAuthorizer {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public void requireCanEscalate(Principal principal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || trustResolver.isAnonymous(authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }

        boolean canEscalate = authentication.getAuthorities().stream()
                .anyMatch(authority -> "TICKET_ESCALATE".equals(authority.getAuthority()));
        if (!canEscalate) {
            throw new AccessDeniedException("Ticket escalation permission is required");
        }
    }
}
