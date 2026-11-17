package com.uncross.core.accounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Turns a PIN into a salted hash, and checks a PIN against one.
 *
 * <p>The exchange never keeps a PIN itself, only {@code salt:hash}. The salt
 * is random per account, so two people with PIN 1234 still have different
 * hashes. (A real system would use a slow hash such as bcrypt; SHA-256 keeps
 * this one free of extra libraries, and the point here is the principle.)
 */
final class Pins {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Pins() {
    }

    static void requireValid(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("a PIN is 4 to 8 digits");
        }
    }

    static String hash(String pin) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = HEX.formatHex(salt);
        return saltHex + ":" + digest(saltHex, pin);
    }

    static boolean matches(String pin, String stored) {
        int colon = stored.indexOf(':');
        if (pin == null || colon < 0) {
            return false;
        }
        byte[] expected = stored.substring(colon + 1).getBytes(StandardCharsets.UTF_8);
        byte[] actual = digest(stored.substring(0, colon), pin).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // constant time: no hint from how long it takes
    }

    private static String digest(String saltHex, String pin) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(sha.digest((saltHex + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }
}
