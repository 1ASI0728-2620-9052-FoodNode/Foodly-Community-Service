package com.foodly.community.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;

import static com.foodly.community.infrastructure.security.JwtTestTokens.*;
import static org.assertj.core.api.Assertions.*;

class IamJwtDecoderFactoryTest {

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsTheObservedIamProducerShapeWithExplicitCurrentContract() {
        var jwt = IamJwtDecoderFactory.create(current(), clock).decode(token(EXPLORER, "GASTRONOMIC_EXPLORER"));
        assertThat(jwt.getSubject()).isEqualTo(EXPLORER);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("GASTRONOMIC_EXPLORER");
        assertThat(jwt.getClaims()).doesNotContainKeys("iss", "aud");
    }

    @ParameterizedTest
    @EnumSource(value = Invalid.class, names = {"MISSING_ISS", "MISSING_AUD", "WRONG_ISS", "WRONG_AUD"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsInvalidCryptographyLifetimeOrRequiredClaims(Invalid invalid) {
        var decoder = IamJwtDecoderFactory.create(current(), clock);
        assertThatThrownBy(() -> decoder.decode(token(EXPLORER, "GASTRONOMIC_EXPLORER", invalid, false)))
                .isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-jwt", "a.b.c"})
    void rejectsMalformedTokens(String token) {
        var decoder = IamJwtDecoderFactory.create(current(), clock);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void strictContractRequiresAndValidatesIssuerAndAudienceWithoutFallback() {
        var decoder = IamJwtDecoderFactory.create(strict(), clock);
        assertThat(decoder.decode(token(EXPLORER, "GASTRONOMIC_EXPLORER", null, true)).getSubject()).isEqualTo(EXPLORER);
        assertThatThrownBy(() -> decoder.decode(token(EXPLORER, "GASTRONOMIC_EXPLORER"))).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Invalid.class, names = {"MISSING_ISS", "MISSING_AUD", "WRONG_ISS", "WRONG_AUD"})
    void strictContractRejectsMissingOrWrongIssuerAndAudience(Invalid invalid) {
        var decoder = IamJwtDecoderFactory.create(strict(), clock);
        assertThatThrownBy(() -> decoder.decode(token(EXPLORER, "GASTRONOMIC_EXPLORER", invalid, true))).isInstanceOf(JwtException.class);
    }

    @Test
    void currentContractDoesNotSilentlyIgnoreNewIssuerOrAudienceClaims() {
        var decoder = IamJwtDecoderFactory.create(current(), clock);
        assertThatThrownBy(() -> decoder.decode(token(EXPLORER, "GASTRONOMIC_EXPLORER", null, true))).isInstanceOf(JwtException.class);
    }

    @Test
    void numericSubjectCannotBeCoercedToAnAuthenticatedIdentity() throws Exception {
        var payload = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload("{\"sub\":123,\"role\":\"GASTRONOMIC_EXPLORER\",\"iat\":"
                + NOW.minusSeconds(10).getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(300).getEpochSecond() + "}"));
        payload.sign(new MACSigner(KEY));
        var decoder = IamJwtDecoderFactory.create(current(), clock);
        assertThatThrownBy(() -> decoder.decode(payload.serialize())).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"iss\":123", "\"aud\":[123]", "\"nbf\":\"123\""})
    void optionalRegisteredClaimsCannotUseCoercedTypes(String malformedClaim) throws Exception {
        var payload = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload("{\"sub\":\"explorer\",\"role\":\"GASTRONOMIC_EXPLORER\",\"iat\":"
                + NOW.minusSeconds(10).getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(300).getEpochSecond() + "," + malformedClaim + "}"));
        payload.sign(new MACSigner(KEY));
        var decoder = IamJwtDecoderFactory.create(current(), clock);
        assertThatThrownBy(() -> decoder.decode(payload.serialize())).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-contract", "missing-key", "invalid-base64", "short-key", "wrong-algorithm", "strict-no-issuer", "strict-no-audience", "current-with-issuer", "current-with-audience"})
    void invalidConfigurationFailsRatherThanCreatingAPermissiveDecoder(String failure) {
        String algorithm = failure.equals("wrong-algorithm") ? "HS384" : "HS256";
        String contract = failure.startsWith("strict") ? "issuer-audience" : failure.equals("missing-contract") ? "" : "iam-current";
        String key = switch (failure) {
            case "missing-key" -> "";
            case "invalid-base64" -> "not base64!";
            case "short-key" -> Base64.getEncoder().encodeToString(new byte[16]);
            default -> encodedKey();
        };
        String issuer = failure.equals("strict-no-audience") || failure.equals("current-with-issuer") ? "test-issuer" : "";
        String audience = failure.equals("strict-no-issuer") || failure.equals("current-with-audience") ? "test-audience" : "";
        assertThatThrownBy(() -> IamJwtDecoderFactory.create(new IamJwtProperties(algorithm, contract, key, issuer, audience), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationToStringCannotRevealTheKey() {
        assertThat(current().toString()).isEqualTo("IamJwtProperties[redacted]").doesNotContain(encodedKey());
    }

    private IamJwtProperties current() {
        return new IamJwtProperties("HS256", "iam-current", encodedKey(), "", "");
    }

    private IamJwtProperties strict() {
        return new IamJwtProperties("HS256", "issuer-audience", encodedKey(), "test-issuer", "test-audience");
    }
}
