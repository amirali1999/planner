package com.lifeplanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeplanner.exception.ErrorCodes;
import com.lifeplanner.model.srv.ApiErrorSrv;
import com.lifeplanner.repository.SigningKeyRepository;
import com.lifeplanner.util.ApiPaths;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Stateless bearer-token security (OAuth 2.0 resource server, RFC 6750). Every API path needs a valid access token
 * except health and the auth endpoints that obtain one. Access tokens are validated for signature (HS256), {@code typ}
 * ({@code at+jwt}, RFC 9068), {@code iss}, {@code aud}, {@code exp}/{@code nbf} (60 s clock skew) and presence of
 * {@code sub}. No server sessions, no form login, no HTTP basic.
 */
@Configuration
public class SecurityConfig {

    static final String REALM = "planner";

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain api(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {
        String v1 = ApiPaths.V1;
        http
                // Bearer tokens are not sent automatically by browsers; the only cookie (refresh) is SameSite=Strict,
                // path-limited to /api/v1/auth and only accepted by refresh/logout.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(v1 + "/health", "/error").permitAll()
                        // API documentation (describes the API, contains no user data)
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, v1 + "/auth/register", v1 + "/auth/login",
                                v1 + "/auth/refresh", v1 + "/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SigningKeyRepository.SigningKey signingKey(SigningKeyRepository keys, Clock clock) {
        return keys.getOrCreate(clock.instant());
    }

    @Bean
    String signingKeyId(SigningKeyRepository.SigningKey key) {
        return key.id();
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeyRepository.SigningKey key) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key.secret())
                .keyID(key.id())
                .algorithm(JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(SigningKeyRepository.SigningKey key, PlannerProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(key))
                .macAlgorithm(MacAlgorithm.HS256)
                .jwtProcessorCustomizer(p -> p.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))))
                .build();
        PlannerProperties.Auth auth = properties.auth();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
        timestamps.setClock(clock);
        // exp/nbf are checked whenever present; only "must have exp" depends on the (temporary) switch
        List<OAuth2TokenValidator<Jwt>> validators = new java.util.ArrayList<>(List.of(
                timestamps,
                new JwtIssuerValidator(auth.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(auth.audience())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
        if (auth.accessTokenExpires()) {
            validators.add(new JwtClaimValidator<Object>(JwtClaimNames.EXP, exp -> exp != null));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * 401 with the standard {@code WWW-Authenticate: Bearer …} challenge (RFC 6750 §3) and the API's JSON error body.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
        challenge.setRealmName(REALM);
        return (request, response, ex) -> {
            challenge.commence(request, response, ex);
            writeError(mapper, response, ex);
        };
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, AuthenticationException ex) throws IOException {
        boolean badToken = ex instanceof OAuth2AuthenticationException;
        ApiErrorSrv body = badToken
                ? ApiErrorSrv.of(ErrorCodes.INVALID_TOKEN, "توکن دسترسی نامعتبر یا منقضی است.")
                : ApiErrorSrv.of(ErrorCodes.UNAUTHORIZED, "برای این درخواست باید وارد شوید.");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }

    private static SecretKey secretKey(SigningKeyRepository.SigningKey key) {
        return new SecretKeySpec(key.secret(), "HmacSHA256");
    }
}
