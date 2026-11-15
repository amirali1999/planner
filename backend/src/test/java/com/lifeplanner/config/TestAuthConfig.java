package com.lifeplanner.config;

import com.lifeplanner.model.User;
import com.lifeplanner.model.vo.RegisterVO;
import com.lifeplanner.repository.UserRepository;
import com.lifeplanner.service.AccessTokenService;
import com.lifeplanner.service.AuthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Test-only: every request made through the auto-configured {@code MockMvc} carries a fresh access token of the test
 * account {@value #EMAIL} (created on first use in each context's database), unless the request already sets an
 * {@code Authorization} header. Tests about authentication itself build their own MockMvc without this.
 */
@Configuration(proxyBeanMethods = false)
public class TestAuthConfig {

    public static final String EMAIL = "owner@test.local";
    public static final String PASSWORD = "test-password-1";

    /** Runs {@code body} as {@code userId} outside an HTTP request (for tests that call repositories directly). */
    public static <T> T runAs(String userId, java.util.function.Supplier<T> body) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "HS256").subject(userId).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        try {
            return body.get();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Bean
    MockMvcBuilderCustomizer bearerTokenByDefault(ObjectProvider<AuthService> auth, ObjectProvider<UserRepository> users,
                                                  ObjectProvider<AccessTokenService> tokens) {
        RequestPostProcessor bearer = new RequestPostProcessor() {
            private volatile User owner;

            @Override
            public org.springframework.mock.web.MockHttpServletRequest postProcessRequest(
                    org.springframework.mock.web.MockHttpServletRequest request) {
                if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getObject().issue(owner()).value());
                }
                return request;
            }

            private User owner() {
                if (owner == null) {
                    synchronized (this) {
                        if (owner == null) {
                            owner = users.getObject().findByEmail(EMAIL).orElseGet(() -> {
                                auth.getObject().register(new RegisterVO(EMAIL, PASSWORD, "Test owner"));
                                return users.getObject().findByEmail(EMAIL).orElseThrow();
                            });
                        }
                    }
                }
                return owner;
            }
        };
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(bearer));
    }
}
