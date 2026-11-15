package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import com.lifeplanner.repository.SigningKeyRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The temporary default {@code planner.auth.access-token-expires=false}: access tokens carry no {@code exp} and stay
 * valid; every other check (signature, typ, iss, aud, sub) still applies, and an {@code exp} that IS present is still
 * enforced. Delete this class when expiry is switched back on for good.
 */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/noexp-${random.uuid}.db")
class AccessTokenWithoutExpiryTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    SigningKeyRepository.SigningKey signingKey;

    @Test
    void accessTokensHaveNoExpiryButAreStillFullyValidated() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noexp@test.local\",\"password\":\"password-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String userId = JsonPath.read(body, "$.user.id");
        Map<String, Object> claims = JsonPath.read(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8), "$");
        assertThat(claims).doesNotContainKey("exp").containsKeys("iss", "sub", "aud", "iat", "jti");
        mvc.perform(get("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());

        // a token without exp but with a wrong audience is still rejected
        Instant now = Instant.now();
        JWTClaimsSet.Builder base = new JWTClaimsSet.Builder().issuer("planner-backend").subject(userId)
                .issueTime(Date.from(now)).jwtID(UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(base.audience("other").build())))
                .andExpect(status().isUnauthorized());
        // an exp that is present is still honoured
        mvc.perform(get("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(base.audience("planner-api")
                        .expirationTime(Date.from(now.minusSeconds(3600))).build())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(base.audience("planner-api")
                        .expirationTime(null).build())))
                .andExpect(status().isOk());
    }

    private String sign(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt")).build(), claims);
        jwt.sign(new MACSigner(signingKey.secret()));
        return jwt.serialize();
    }
}
