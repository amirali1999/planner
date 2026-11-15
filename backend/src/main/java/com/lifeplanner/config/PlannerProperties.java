package com.lifeplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Application settings under the {@code planner.*} prefix. */
@ConfigurationProperties(prefix = "planner")
public record PlannerProperties(Cors cors, Datasource datasource, Auth auth) {

    public PlannerProperties {
        if (auth == null) {
            auth = new Auth(null, null, null, null, null, null, null);
        }
        if (cors == null) {
            cors = new Cors(List.of());
        }
        if (datasource == null) {
            datasource = new Datasource(null, null, null);
        }
    }

    /**
     * Token settings.
     *
     * @param issuer           {@code iss} of issued access tokens (and the only accepted issuer)
     * @param audience         {@code aud} of access tokens (the only accepted audience)
     * @param accessTokenTtl   lifetime of a JWT access token (short: it cannot be revoked); used only while
     *                         {@code accessTokenExpires} is true
     * @param accessTokenExpires TEMPORARY SWITCH, default false: access tokens carry no {@code exp} claim and stay valid
     *                         until the signing key changes. Set {@code planner.auth.access-token-expires=true}
     *                         (env {@code PLANNER_AUTH_ACCESS_TOKEN_EXPIRES=true}) to restore expiring tokens; nothing
     *                         else needs to change (clients already refresh on 401).
     * @param refreshTokenTtl  absolute lifetime of a login session (a refresh-token family); rotation does not extend it
     * @param cookieSecure     {@code Secure} attribute of the refresh cookie; off by default because the backend
     *                         serves plain HTTP on loopback; turn on behind HTTPS
     * @param cookieName       name of the httpOnly refresh-token cookie
     */
    public record Auth(String issuer, String audience, Duration accessTokenTtl, Duration refreshTokenTtl,
                       Boolean cookieSecure, String cookieName, Boolean accessTokenExpires) {
        public Auth {
            issuer = issuer == null || issuer.isBlank() ? "planner-backend" : issuer;
            audience = audience == null || audience.isBlank() ? "planner-api" : audience;
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
            refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
            cookieSecure = cookieSecure != null && cookieSecure;
            cookieName = cookieName == null || cookieName.isBlank() ? "planner_refresh" : cookieName;
            accessTokenExpires = accessTokenExpires != null && accessTokenExpires;
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    /**
     * @param path        SQLite file; relative paths resolve against the working directory. Bound as a String
     *                    on purpose: Spring's String→Path conversion goes through the servlet resource loader in
     *                    a web app and rejects paths starting with "..", such as the default.
     * @param busyTimeout how long a connection waits for a lock before failing with SQLITE_BUSY
     * @param maxPoolSize upper bound of pooled connections
     */
    public record Datasource(String path, Duration busyTimeout, Integer maxPoolSize) {
        public Datasource {
            if (path == null || path.isBlank()) {
                path = "../data/planner.db";
            }
            if (busyTimeout == null) {
                busyTimeout = Duration.ofSeconds(5);
            }
            if (maxPoolSize == null) {
                maxPoolSize = 4;
            }
        }

        /** The configured database file as a plain filesystem path. */
        public Path file() {
            return Path.of(path);
        }
    }
}
