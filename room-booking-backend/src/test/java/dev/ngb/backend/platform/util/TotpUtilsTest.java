package dev.ngb.backend.platform.util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpUtilsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void base32RoundTripsArbitraryBytes() {
        byte[] original = TotpUtils.generateSecret();

        String encoded = TotpUtils.base32Encode(original);
        byte[] decoded = TotpUtils.base32Decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void base32DecodeIsCaseInsensitiveAndIgnoresPadding() {
        byte[] secret = TotpUtils.generateSecret();
        String encoded = TotpUtils.base32Encode(secret);

        byte[] decodedLower = TotpUtils.base32Decode(encoded.toLowerCase(java.util.Locale.ROOT) + "===");

        assertThat(decodedLower).isEqualTo(secret);
    }

    @Test
    void rfc6238KnownAnswerTestVectorForHmacSha1() {
        // RFC 6238 Appendix B: 20-byte ASCII secret, T = 59s -> counter 1, 8-digit code 94287082.
        // This codebase always requests 6 digits, so the check compares the low-order 6 digits,
        // which are the actual value truncation always keeps.
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        Instant t59 = Instant.ofEpochSecond(59);

        String code = TotpUtils.generateCode(secret, t59, 6, Duration.ofSeconds(30));

        assertThat(code).isEqualTo("287082");
    }

    @Test
    void codeGeneratedForAnInstantVerifiesAtThatInstant() {
        byte[] secret = TotpUtils.generateSecret();

        String code = TotpUtils.generateCode(secret, NOW, 6, Duration.ofSeconds(30));

        assertThat(TotpUtils.verifyCode(secret, code, NOW, 6, Duration.ofSeconds(30), 1)).isTrue();
    }

    @Test
    void codeIsAcceptedOneStepAwayWithinTheWindow() {
        byte[] secret = TotpUtils.generateSecret();
        String code = TotpUtils.generateCode(secret, NOW, 6, Duration.ofSeconds(30));

        boolean verified = TotpUtils.verifyCode(
                secret, code, NOW.plusSeconds(30), 6, Duration.ofSeconds(30), 1);

        assertThat(verified).isTrue();
    }

    @Test
    void codeIsRejectedOutsideTheWindow() {
        byte[] secret = TotpUtils.generateSecret();
        String code = TotpUtils.generateCode(secret, NOW, 6, Duration.ofSeconds(30));

        boolean verified = TotpUtils.verifyCode(
                secret, code, NOW.plusSeconds(120), 6, Duration.ofSeconds(30), 1);

        assertThat(verified).isFalse();
    }

    @Test
    void wrongSecretIsRejected() {
        byte[] secret = TotpUtils.generateSecret();
        byte[] otherSecret = TotpUtils.generateSecret();
        String code = TotpUtils.generateCode(secret, NOW, 6, Duration.ofSeconds(30));

        assertThat(TotpUtils.verifyCode(otherSecret, code, NOW, 6, Duration.ofSeconds(30), 1))
                .isFalse();
    }

    @Test
    void provisioningUriCarriesTheBase32SecretAndIssuer() {
        byte[] secret = TotpUtils.generateSecret();

        String uri = TotpUtils.buildProvisioningUri("Room Booking", "person@example.com", secret);

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + TotpUtils.base32Encode(secret));
        assertThat(uri).contains("issuer=Room+Booking");
    }
}
