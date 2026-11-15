package com.lifeplanner.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The authenticated user of the current request: the {@code sub} claim of the validated access token. Repositories use
 * it to scope every query to the caller's own data, so another user's rows are indistinguishable from missing (404).
 */
@Component
public class CurrentUser {

    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null) {
            return jwt.getToken().getSubject();
        }
        // Every data endpoint requires authentication; reaching this is a programming error, never a client error.
        throw new IllegalStateException("No authenticated user in the security context");
    }
}
