package dev.ngb.backend.platform.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords, and the RFC 4648 Base32 encoding a TOTP seed is
 * conventionally shared with an authenticator app in.
 *
 * <p>The private constructor prevents instantiation of this static-only utility, matching
 * {@link SecureTokenUtils}. Every method is a pure function of its arguments — no clock is read
 * here — so a caller supplies its own decision instant, following the same discipline
 * {@code docs/conventions/04-time-and-clock.md} applies everywhere else.</p>
 */
public final class TotpUtils {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTE_LENGTH = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpUtils() {
    }

    /**
     * Generates a new random TOTP seed.
     *
     * @return 160 random bits, the length RFC 4226 recommends for an HMAC-SHA1 seed
     */
    public static byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(secret);
        return secret;
    }

    /**
     * Encodes bytes as unpadded, uppercase RFC 4648 Base32 — the form an authenticator app expects
     * a seed typed or scanned in.
     *
     * @param data bytes to encode
     * @return Base32 text with no padding characters
     */
    public static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    /**
     * Decodes unpadded or padded, case-insensitive RFC 4648 Base32 text.
     *
     * @param base32 text produced by an authenticator app or {@link #base32Encode}
     * @return the original bytes
     * @throws IllegalArgumentException when the text contains a character outside the alphabet
     */
    public static byte[] base32Decode(String base32) {
        String cleaned = base32.trim().toUpperCase(Locale.ROOT).replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(cleaned.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * Computes the RFC 6238 code in force for one time step containing {@code instant}.
     *
     * @param secret raw TOTP seed
     * @param instant instant to compute the code for
     * @param digits code width, conventionally {@code 6}
     * @param step time-step size, conventionally 30 seconds
     * @return zero-padded numeric code of exactly {@code digits} characters
     */
    public static String generateCode(byte[] secret, Instant instant, int digits, Duration step) {
        long counter = instant.getEpochSecond() / step.getSeconds();
        return hotp(secret, counter, digits);
    }

    /**
     * Verifies a submitted code against a small window of time steps around {@code instant},
     * tolerating ordinary clock drift between the server and the authenticator app.
     *
     * @param secret raw TOTP seed
     * @param submittedCode code as the holder typed it
     * @param instant instant to verify against
     * @param digits code width, conventionally {@code 6}
     * @param step time-step size, conventionally 30 seconds
     * @param windowSteps how many steps before and after the current one are also accepted
     * @return {@code true} when the code matches any step in the window
     */
    public static boolean verifyCode(
            byte[] secret, String submittedCode, Instant instant, int digits, Duration step,
            int windowSteps) {
        long counter = instant.getEpochSecond() / step.getSeconds();
        for (long candidate = counter - windowSteps; candidate <= counter + windowSteps; candidate++) {
            if (hotp(secret, candidate, digits).equals(submittedCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds an {@code otpauth://totp/} provisioning URI an authenticator app can scan as a QR
     * code.
     *
     * @param issuer platform name shown alongside the account in the app
     * @param accountLabel identifies the account to the holder, such as their email address
     * @param secret raw TOTP seed
     * @return the provisioning URI, with the seed Base32-encoded and every part percent-escaped
     */
    public static String buildProvisioningUri(String issuer, String accountLabel, byte[] secret) {
        String encodedIssuer = java.net.URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedLabel = java.net.URLEncoder.encode(
                issuer + ":" + accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedLabel
                + "?secret=" + base32Encode(secret)
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * RFC 4226 HOTP: an HMAC-SHA1 of the counter, truncated to a fixed-width numeric code.
     */
    private static String hotp(byte[] secret, long counter, int digits) {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            hash = mac.doFinal(counterBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("could not compute HOTP", exception);
        }

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int truncated = binary % (int) Math.pow(10, digits);
        String pattern = "%0" + digits + "d";
        return String.format(pattern, truncated);
    }
}
