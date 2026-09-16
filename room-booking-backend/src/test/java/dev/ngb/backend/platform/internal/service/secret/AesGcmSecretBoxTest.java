package dev.ngb.backend.platform.internal.service.secret;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import dev.ngb.backend.platform.SecretBox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretBoxTest {

    private static final String VALID_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Test
    void sealThenOpenRecoversTheOriginalPlaintext() {
        AesGcmSecretBox box = new AesGcmSecretBox(VALID_KEY);
        byte[] plaintext = "totp-seed-material".getBytes(StandardCharsets.UTF_8);

        SecretBox.SealedSecret sealed = box.seal(plaintext);
        byte[] recovered = box.open(sealed.reference(), sealed.keyVersion());

        assertThat(recovered).isEqualTo(plaintext);
        assertThat(sealed.keyVersion()).isEqualTo((short) 1);
    }

    @Test
    void sealingTheSamePlaintextTwiceProducesDifferentReferences() {
        AesGcmSecretBox box = new AesGcmSecretBox(VALID_KEY);
        byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);

        SecretBox.SealedSecret first = box.seal(plaintext);
        SecretBox.SealedSecret second = box.seal(plaintext);

        assertThat(first.reference()).isNotEqualTo(second.reference());
    }

    @Test
    void openingWithAnUnknownKeyVersionFails() {
        AesGcmSecretBox box = new AesGcmSecretBox(VALID_KEY);
        SecretBox.SealedSecret sealed = box.seal("data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> box.open(sealed.reference(), (short) 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankKeyFailsAtConstruction() {
        assertThatThrownBy(() -> new AesGcmSecretBox(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongLengthKeyFailsAtConstruction() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmSecretBox(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }
}
