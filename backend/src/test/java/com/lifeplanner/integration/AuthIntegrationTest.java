package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import com.lifeplanner.repository.SigningKeyRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication end to end: registration/login, JWT access tokens (RFC 7519/9068) with RFC 6750 bearer errors,
 * refresh-token rotation with reuse detection, logout, brute-force limit, per-user data isolation, and the first
 * account claiming pre-existing data. Fresh database per test (no default token: this class sends its own).
 */
// expiring access tokens (the mode to return to): see AccessTokenWithoutExpiryTest for the temporary default
@SpringBootTest(properties = {"planner.datasource.path=target/test-db/auth-${random.uuid}.db", "planner.auth.access-token-expires=true"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthIntegrationTest {

    static final String COOKIE = "planner_refresh";

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcClient db;
    @Autowired
    SigningKeyRepository.SigningKey signingKey;

    MockMvc mvc;

    @BeforeEach
    void plainMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    record Login(String accessToken, String refreshToken, String userId, MvcResult result) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    Login register(String email, String password) throws Exception {
        return session(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password))).andExpect(status().isCreated()).andReturn());
    }

    Login login(String email, String password) throws Exception {
        return session(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password))).andExpect(status().isOk()).andReturn());
    }

    ResultActions refresh(String refreshToken) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/auth/refresh");
        if (refreshToken != null) {
            req.cookie(new Cookie(COOKIE, refreshToken));
        }
        return mvc.perform(req);
    }

    static Login session(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        return new Login(JsonPath.read(body, "$.accessToken"), cookieValue(r), JsonPath.read(body, "$.user.id"), r);
    }

    static String setCookie(MvcResult r) {
        return r.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream().filter(h -> h.startsWith(COOKIE + "="))
                .findFirst().orElse(null);
    }

    static String cookieValue(MvcResult r) {
        String h = setCookie(r);
        return h == null ? null : h.substring(COOKIE.length() + 1, h.indexOf(';'));
    }

    static Map<String, Object> part(String jwt, int index) {
        String decoded = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
        return JsonPath.read(decoded, "$");
    }

    ResultActions getAs(String bearer, String path) throws Exception {
        MockHttpServletRequestBuilder req = get(path);
        if (bearer != null) {
            req.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mvc.perform(req);
    }

    String postAs(Login who, String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, who.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    /** A token signed with the real key but with the given header/claims tweaks. */
    String craft(JOSEObjectType typ, byte[] key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(typ).keyID(signingKey.id()).build(), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    JWTClaimsSet.Builder validClaims(String sub) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("planner-backend").audience("planner-api").subject(sub)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).jwtID(UUID.randomUUID().toString());
    }

    // ---- registration / login / token format --------------------------------------------------------------------------

    @Test
    void registerReturnsBearerTokenAndHttpOnlyRefreshCookieNeverCached() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  Ali@Example.COM \",\"password\":\"correct horse\",\"displayName\":\" علی \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("ali@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("علی"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        String cookie = setCookie(r);
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth")
                .containsPattern("Max-Age=259(1|2)\\d{3}").doesNotContain("Secure");
        assertThat(cookieValue(r)).hasSizeGreaterThanOrEqualTo(43); // 256 random bits
        // stored hashed, never in clear text; password stored as BCrypt
        assertThat(db.sql("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?").param(cookieValue(r)).query(Integer.class).single()).isZero();
        assertThat(db.sql("SELECT password_hash FROM users").query(String.class).single()).startsWith("$2").doesNotContain("correct horse");
    }

    @Test
    void accessTokenIsAStandardJwtAccessToken() throws Exception {
        Login a = register("jwt@test.local", "password-123");
        Map<String, Object> header = part(a.accessToken(), 0);
        Map<String, Object> claims = part(a.accessToken(), 1);
        assertThat(header).containsEntry("alg", "HS256").containsEntry("typ", "at+jwt").containsKey("kid");
        assertThat(claims).containsEntry("iss", "planner-backend").containsEntry("sub", a.userId())
                .containsEntry("client_id", "planner-web").containsKeys("iat", "exp", "jti");
        assertThat(claims.get("aud")).isIn("planner-api", List.of("planner-api"));
        assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue()).isEqualTo(900);
        getAs(a.bearer(), "/api/v1/auth/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.userId())).andExpect(jsonPath("$.email").value("jwt@test.local"));
    }

    @Test
    void protectedEndpointsNeedABearerTokenWithStandardChallenge() throws Exception {
        getAs(null, "/api/v1/goals").andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").isString());
        getAs("Bearer not-a-jwt", "/api/v1/goals").andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("error=\"invalid_token\"")))
                .andExpect(jsonPath("$.code").value("invalid_token"));
        getAs("Basic dXNlcjpwYXNz", "/api/v1/goals").andExpect(status().isUnauthorized());
        getAs(null, "/api/v1/health").andExpect(status().isOk()); // public, no personal data
        getAs(null, "/api/v1/auth/me").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedExpiredOrMisaddressedTokensAreRejected() throws Exception {
        Login a = register("forge@test.local", "password-123");
        JOSEObjectType at = new JOSEObjectType("at+jwt");
        byte[] otherKey = new byte[32];
        new java.security.SecureRandom().nextBytes(otherKey);
        Instant past = Instant.now().minusSeconds(3600);
        List<String> bad = List.of(
                craft(at, otherKey, validClaims(a.userId()).build()),                                   // wrong signature
                craft(JOSEObjectType.JWT, signingKey.secret(), validClaims(a.userId()).build()),         // not an access token (typ)
                craft(at, signingKey.secret(), validClaims(a.userId()).audience("other-api").build()),   // wrong audience
                craft(at, signingKey.secret(), validClaims(a.userId()).issuer("evil").build()),          // wrong issuer
                craft(at, signingKey.secret(), validClaims(a.userId())                                    // expired
                        .issueTime(Date.from(past)).expirationTime(Date.from(past.plusSeconds(60))).build()),
                craft(at, signingKey.secret(), new JWTClaimsSet.Builder().issuer("planner-backend")       // no subject
                        .audience("planner-api").expirationTime(Date.from(Instant.now().plusSeconds(60))).build()),
                // unsigned ("alg":"none")
                Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"at+jwt\"}".getBytes())
                        + "." + a.accessToken().split("\\.")[1] + ".");
        for (String token : bad) {
            getAs("Bearer " + token, "/api/v1/goals").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_token"));
        }
        // control: a correctly crafted token works
        getAs("Bearer " + craft(at, signingKey.secret(), validClaims(a.userId()).build()), "/api/v1/goals").andExpect(status().isOk());
    }

    @Test
    void loginIsCaseInsensitiveAndFailuresDoNotRevealWhichPartWasWrong() throws Exception {
        register("login@test.local", "password-123");
        Login l = login("LOGIN@test.local", "password-123");
        getAs(l.bearer(), "/api/v1/goals").andExpect(status().isOk());
        for (String body : List.of(json("login@test.local", "wrong-password"), json("nobody@test.local", "password-123"))) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"))
                    .andExpect(jsonPath("$.message").value("ایمیل یا رمز عبور درست نیست."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void repeatedLoginFailuresAreThrottled() throws Exception {
        register("brute@test.local", "password-123");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("brute@test.local", "guess-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("brute@test.local", "password-123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_attempts"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"no-at-sign\",\"password\":\"password-123\"}|email",
            "{\"email\":\"a@b\",\"password\":\"password-123\"}|email",
            "{\"email\":\"short@test.local\",\"password\":\"1234567\"}|password",
            "{\"email\":\"long@test.local\",\"password\":\"ــــــــــــــــــــــــــــــــــــــــ\"}|password",
            "{\"password\":\"password-123\"}|email",
            "{\"email\":\"x@test.local\"}|password",
            "{\"email\":\"x@test.local\",\"password\":\"password-123\",\"role\":\"admin\"}|role",
    })
    void registrationValidation(String spec) throws Exception {
        String[] parts = spec.split("\\|");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(parts[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details." + parts[1]).exists());
        assertThat(db.sql("SELECT count(*) FROM users").query(Integer.class).single()).isZero();
    }

    @Test
    void duplicateEmailIs409RegardlessOfCase() throws Exception {
        register("dup@test.local", "password-123");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("DUP@test.local", "other-password")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("email_taken"));
    }

    // ---- refresh rotation ------------------------------------------------------------------------------------------------

    @Test
    void refreshRotatesTheTokenAndIssuesANewAccessToken() throws Exception {
        Login a = register("rot@test.local", "password-123");
        MvcResult r = refresh(a.refreshToken()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        Login b = session(r);
        assertThat(b.refreshToken()).isNotEqualTo(a.refreshToken());
        assertThat(b.accessToken()).isNotEqualTo(a.accessToken());
        getAs(b.bearer(), "/api/v1/goals").andExpect(status().isOk());
        // absolute session lifetime: the rotated cookie does not get a fresh 30 days
        assertThat(setCookie(r)).containsPattern("Max-Age=259(1|2)\\d{3}");
        refresh(b.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() throws Exception {
        Login a = register("reuse@test.local", "password-123");
        Login b = session(refresh(a.refreshToken()).andExpect(status().isOk()).andReturn());
        // a parallel tab within the grace period: told to retry, session untouched
        refresh(a.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_rotated"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        // later (grace over): reuse = theft signal -> whole family revoked
        db.sql("UPDATE refresh_tokens SET revoked_at = ? WHERE revoked_reason = 'rotated'")
                .param("2020-01-01T00:00:00.000Z").update();
        MvcResult reuse = refresh(a.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh_token")).andReturn();
        assertThat(setCookie(reuse)).contains("Max-Age=0");
        refresh(b.refreshToken()).andExpect(status().isUnauthorized()); // the newest token is dead too
        assertThat(db.sql("SELECT count(*) FROM refresh_tokens WHERE revoked_reason = 'reuse_detected'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void missingUnknownAndExpiredRefreshTokensAre401() throws Exception {
        refresh(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_refresh_token"));
        refresh("garbage").andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        Login a = register("exp@test.local", "password-123");
        db.sql("UPDATE refresh_tokens SET expires_at = '2020-01-01T00:00:00.000Z'").update();
        refresh(a.refreshToken()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_refresh_token"));
        // an access token cannot be used as a refresh token
        refresh(a.accessToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Login a = register("out@test.local", "password-123");
        Login other = login("out@test.local", "password-123"); // a second session (another device)
        MvcResult r = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, a.refreshToken())))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(setCookie(r)).contains("Max-Age=0");
        refresh(a.refreshToken()).andExpect(status().isUnauthorized());
        refresh(other.refreshToken()).andExpect(status().isOk()); // other devices stay signed in
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent()); // idempotent, no cookie needed
    }

    // ---- data ownership --------------------------------------------------------------------------------------------------

    @Test
    void usersOnlyEverSeeTheirOwnData() throws Exception {
        Login a = register("a@test.local", "password-123");
        Login b = register("b@test.local", "password-123");
        String goal = JsonPath.read(postAs(a, "/api/v1/goals",
                "{\"title\":\"A\",\"periodType\":\"monthly\",\"periodStart\":\"2026-10-23\",\"periodEnd\":\"2026-11-21\"}"), "$.id");
        String kr = JsonPath.read(postAs(a, "/api/v1/goals/" + goal + "/key-results", "{\"name\":\"k\",\"baseline\":0,\"target\":10}"), "$.id");
        String task = JsonPath.read(postAs(a, "/api/v1/tasks", "{\"title\":\"t\",\"goalId\":\"" + goal + "\"}"), "$.id");
        String focus = JsonPath.read(postAs(a, "/api/v1/focus-items", "{\"title\":\"f\",\"kind\":\"book\"}"), "$.id");
        String milestone = JsonPath.read(postAs(a, "/api/v1/goals/" + goal + "/milestones", "{\"title\":\"m\"}"), "$.id");
        String inbox = JsonPath.read(postAs(a, "/api/v1/inbox-items", "{\"rawText\":\"note\"}"), "$.id");
        postAs(a, "/api/v1/plans/daily/2026-11-09/items", "{\"taskId\":\"" + task + "\"}");

        // lists are empty for B
        for (String path : List.of("/api/v1/goals", "/api/v1/tasks", "/api/v1/focus-items", "/api/v1/inbox-items")) {
            getAs(b.bearer(), path).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        getAs(b.bearer(), "/api/v1/goals/tree").andExpect(jsonPath("$.goalCount").value(0));
        getAs(b.bearer(), "/api/v1/plans/daily?date=2026-11-09").andExpect(jsonPath("$.items.length()").value(0));
        getAs(b.bearer(), "/api/v1/dashboard/today?date=2026-11-09").andExpect(jsonPath("$.goals.length()").value(0));
        // A's rows are 404 for B, reads and writes alike
        for (String path : List.of("/api/v1/goals/" + goal, "/api/v1/goals/" + goal + "/progress", "/api/v1/goals/" + goal + "/tasks",
                "/api/v1/goals/" + goal + "/key-results", "/api/v1/goals/" + goal + "/milestones", "/api/v1/key-results/" + kr,
                "/api/v1/key-results/" + kr + "/check-ins", "/api/v1/key-results/" + kr + "/history", "/api/v1/tasks/" + task,
                "/api/v1/focus-items/" + focus, "/api/v1/inbox-items/" + inbox)) {
            getAs(b.bearer(), path).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/goals/progress").param("ids", goal).header(HttpHeaders.AUTHORIZATION, b.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/goals/" + goal).header(HttpHeaders.AUTHORIZATION, b.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stolen\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/milestones/" + milestone).header(HttpHeaders.AUTHORIZATION, b.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/key-results/" + kr + "/check-ins").header(HttpHeaders.AUTHORIZATION, b.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":5,\"measuredAt\":\"2026-11-09T08:00:00Z\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/goals/" + goal + "/archive").header(HttpHeaders.AUTHORIZATION, b.bearer())).andExpect(status().isNotFound());
        // B cannot link to A's rows either
        for (String body : List.of("{\"title\":\"x\",\"goalId\":\"" + goal + "\"}", "{\"title\":\"x\",\"focusItemId\":\"" + focus + "\"}")) {
            int code = mvc.perform(post("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, b.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            assertThat(code).isIn(400, 404);
        }
        mvc.perform(post("/api/v1/plans/daily/2026-11-09/items").header(HttpHeaders.AUTHORIZATION, b.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + task + "\"}")).andExpect(status().isNotFound());
        // the same date can be planned by both users
        String bTask = JsonPath.read(postAs(b, "/api/v1/tasks", "{\"title\":\"b\"}"), "$.id");
        postAs(b, "/api/v1/plans/daily/2026-11-09/items", "{\"taskId\":\"" + bTask + "\"}");
        getAs(a.bearer(), "/api/v1/plans/daily?date=2026-11-09").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].task.id").value(task));
        // nothing of A changed
        getAs(a.bearer(), "/api/v1/goals/" + goal).andExpect(jsonPath("$.title").value("A")).andExpect(jsonPath("$.archivedAt").doesNotExist());
    }

    @Test
    void theFirstAccountClaimsDataFromBeforeAuthentication() throws Exception {
        String now = "2026-11-01T10:00:00.000Z";
        String legacyGoal = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO goals (id, title, period_type, period_start, period_end, status, created_at, updated_at)
                VALUES (?, 'قدیمی', 'monthly', '2026-10-23', '2026-11-21', 'doing', ?, ?)""").params(legacyGoal, now, now).update();
        String legacyTask = UUID.randomUUID().toString();
        db.sql("INSERT INTO tasks (id, title, goal_id, status, created_at, updated_at) VALUES (?, 'old', ?, 'done', ?, ?)")
                .params(legacyTask, legacyGoal, now, now).update();

        Login first = register("first@test.local", "password-123");
        getAs(first.bearer(), "/api/v1/goals").andExpect(jsonPath("$[0].id").value(legacyGoal));
        getAs(first.bearer(), "/api/v1/goals/" + legacyGoal + "/progress").andExpect(jsonPath("$.executionProgress").value(100.0));
        Login second = register("second@test.local", "password-123");
        getAs(second.bearer(), "/api/v1/goals").andExpect(jsonPath("$.length()").value(0));
        getAs(second.bearer(), "/api/v1/tasks/" + legacyTask).andExpect(status().isNotFound());
        assertThat(db.sql("SELECT count(*) FROM goals WHERE user_id IS NULL").query(Integer.class).single()).isZero();
    }
}
