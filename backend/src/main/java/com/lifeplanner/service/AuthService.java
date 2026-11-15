package com.lifeplanner.service;

import com.lifeplanner.config.PlannerProperties;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.exception.ErrorCodes;
import com.lifeplanner.model.RefreshToken;
import com.lifeplanner.model.User;
import com.lifeplanner.model.srv.TokenSrv;
import com.lifeplanner.model.srv.UserSrv;
import com.lifeplanner.model.vo.LoginVO;
import com.lifeplanner.model.vo.RegisterVO;
import com.lifeplanner.repository.RefreshTokenRepository;
import com.lifeplanner.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Registration, login, refresh-token rotation and logout.
 * <ul>
 *   <li>Passwords: BCrypt; 8..72 UTF-8 bytes (BCrypt ignores anything longer, so longer input is rejected).</li>
 *   <li>Refresh tokens: 256 random bits, only the SHA-256 hash is stored; every refresh rotates the token; presenting
 *       a rotated token again revokes the whole session (reuse detection), except within a short grace period that
 *       covers two browser tabs refreshing at the same moment.</li>
 *   <li>Login errors never reveal whether an e-mail exists.</li>
 * </ul>
 */
@Service
public class AuthService {

    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int PASSWORD_MIN_CHARS = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final String MSG_INVALID = "اطلاعات حساب کاربری معتبر نیست.";
    private static final String BEARER_REALM = "Bearer realm=\"planner\"";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder passwords;
    private final LoginThrottle throttle;
    private final CurrentUser currentUser;
    private final PlannerProperties.Auth auth;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Hash of a random password, checked when the e-mail is unknown so both paths take similar time. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, AccessTokenService accessTokens,
                       PasswordEncoder passwords, LoginThrottle throttle, CurrentUser currentUser,
                       PlannerProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwords = passwords;
        this.throttle = throttle;
        this.currentUser = currentUser;
        this.auth = properties.auth();
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** Body for the client plus the raw refresh token, which the controller puts into the httpOnly cookie. */
    public record Session(TokenSrv tokens, String refreshToken, Instant refreshExpiresAt) {
    }

    @Transactional
    public Session register(RegisterVO req) {
        String email = normalizeEmail(req.email());
        Map<String, Object> errors = new LinkedHashMap<>();
        if (!EMAIL.matcher(email).matches()) {
            errors.put("email", "invalid_value");
        }
        String password = req.password();
        if (password.codePointCount(0, password.length()) < PASSWORD_MIN_CHARS) {
            errors.put("password", "too_short");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            errors.put("password", "too_long");
        } else if (password.isBlank()) {
            errors.put("password", "invalid_value");
        }
        String displayName = req.displayName() == null || req.displayName().isBlank() ? null : req.displayName().trim();
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID, errors);
        }
        if (users.findByEmail(email).isPresent()) {
            throw emailTaken();
        }
        boolean first = users.count() == 0;
        Instant now = clock.instant();
        User user = new User(UUID.randomUUID().toString(), email, passwords.encode(password), displayName, now, now);
        try {
            users.insert(user);
        } catch (DuplicateKeyException e) { // concurrent registration with the same e-mail
            throw emailTaken();
        }
        if (first) {
            users.claimUnownedData(user.id()); // data from before accounts existed belongs to the first account
        }
        return startSession(user, now);
    }

    @Transactional
    public Session login(LoginVO req) {
        String email = normalizeEmail(req.email());
        throttle.check(email);
        User user = users.findByEmail(email).orElse(null);
        boolean ok = passwords.matches(req.password(), user == null ? dummyHash : user.passwordHash());
        if (user == null || !ok) {
            throttle.recordFailure(email);
            throw new ApiException.UnauthorizedException(ErrorCodes.INVALID_CREDENTIALS,
                    "ایمیل یا رمز عبور درست نیست.", null);
        }
        throttle.reset(email);
        return startSession(user, clock.instant());
    }

    /** Rotates the refresh token: the presented one is revoked and a new one of the same session is issued. */
    @Transactional(noRollbackFor = ApiException.UnauthorizedException.class) // keep the revocations of a detected reuse
    public Session refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = rawRefreshToken == null ? null
                : refreshTokens.findByHash(hash(rawRefreshToken)).orElse(null);
        if (current == null) {
            throw invalidRefresh();
        }
        if (current.isRevoked()) {
            boolean justRotated = "rotated".equals(current.revokedReason())
                    && current.revokedAt().plus(ROTATION_GRACE).isAfter(now);
            if (justRotated) {
                throw new ApiException.UnauthorizedException(ErrorCodes.REFRESH_TOKEN_ROTATED,
                        "نشست هم‌زمان در جای دیگری تمدید شد؛ دوباره تلاش کنید.", null);
            }
            if (!"logout".equals(current.revokedReason())) {
                refreshTokens.revokeFamily(current.familyId(), "reuse_detected", now);
            }
            throw invalidRefresh();
        }
        if (!current.expiresAt().isAfter(now)) {
            throw invalidRefresh();
        }
        if (!refreshTokens.revokeIfActive(current.id(), "rotated", now)) {
            // a concurrent refresh with the same token won the race
            throw new ApiException.UnauthorizedException(ErrorCodes.REFRESH_TOKEN_ROTATED,
                    "نشست هم‌زمان در جای دیگری تمدید شد؛ دوباره تلاش کنید.", null);
        }
        User user = users.findById(current.userId()).orElseThrow(this::invalidRefresh);
        String raw = newRawToken();
        refreshTokens.insert(new RefreshToken(UUID.randomUUID().toString(), user.id(), current.familyId(), hash(raw),
                current.expiresAt(), now, null, null)); // absolute session lifetime: rotation does not extend it
        return new Session(tokenBody(user), raw, current.expiresAt());
    }

    /** Ends the session of the presented refresh token (all its rotations). Idempotent; unknown tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        refreshTokens.findByHash(hash(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), "logout", clock.instant()));
    }

    public UserSrv me() {
        return users.findById(currentUser.id()).map(UserSrv::from)
                .orElseThrow(() -> new ApiException.UnauthorizedException(ErrorCodes.INVALID_TOKEN,
                        "حساب کاربری این توکن دیگر وجود ندارد.", BEARER_REALM + ", error=\"invalid_token\""));
    }

    private Session startSession(User user, Instant now) {
        String raw = newRawToken();
        Instant expires = now.plus(auth.refreshTokenTtl());
        refreshTokens.insert(new RefreshToken(UUID.randomUUID().toString(), user.id(), UUID.randomUUID().toString(),
                hash(raw), expires, now, null, null));
        return new Session(tokenBody(user), raw, expires);
    }

    private TokenSrv tokenBody(User user) {
        AccessTokenService.AccessToken at = accessTokens.issue(user);
        return new TokenSrv(at.value(), "Bearer", at.expiresInSeconds(), UserSrv.from(user));
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException.ConflictException emailTaken() {
        return new ApiException.ConflictException(ErrorCodes.EMAIL_TAKEN, "با این ایمیل قبلاً حساب ساخته شده است.",
                Map.of("email", "taken"));
    }

    private ApiException.UnauthorizedException invalidRefresh() {
        return new ApiException.UnauthorizedException(ErrorCodes.INVALID_REFRESH_TOKEN,
                "نشست شما منقضی شده است؛ دوباره وارد شوید.", null);
    }
}
