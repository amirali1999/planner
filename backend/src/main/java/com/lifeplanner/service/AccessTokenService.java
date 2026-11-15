package com.lifeplanner.service;

import com.lifeplanner.config.PlannerProperties;
import com.lifeplanner.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues access tokens as signed JWTs (RFC 7519, JWS HS256) following the JWT access-token profile (RFC 9068):
 * header {@code typ=at+jwt}; claims {@code iss, sub, aud, iat, jti, client_id} and {@code exp} unless expiry is
 * switched off ({@code planner.auth.access-token-expires=false}, the temporary default).
 */
@Service
public class AccessTokenService {

    public static final String TOKEN_TYPE = "at+jwt";
    public static final String CLIENT_ID = "planner-web";

    private final JwtEncoder encoder;
    private final PlannerProperties.Auth auth;
    private final Clock clock;
    private final String keyId;

    public AccessTokenService(JwtEncoder encoder, PlannerProperties properties, Clock clock,
                              @org.springframework.beans.factory.annotation.Qualifier("signingKeyId") String keyId) {
        this.encoder = encoder;
        this.auth = properties.auth();
        this.clock = clock;
        this.keyId = keyId;
    }

    /** {@code expiresAt}/{@code expiresInSeconds} are null while access-token expiry is switched off. */
    public record AccessToken(String value, Instant expiresAt, Long expiresInSeconds) {
    }

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant exp = auth.accessTokenExpires() ? now.plus(auth.accessTokenTtl()) : null;
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(auth.issuer())
                .subject(user.id())
                .audience(List.of(auth.audience()))
                .issuedAt(now)
                .id(UUID.randomUUID().toString())
                .claim("client_id", CLIENT_ID);
        if (exp != null) {
            builder.expiresAt(exp);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type(TOKEN_TYPE).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, exp, exp == null ? null : auth.accessTokenTtl().toSeconds());
    }
}
