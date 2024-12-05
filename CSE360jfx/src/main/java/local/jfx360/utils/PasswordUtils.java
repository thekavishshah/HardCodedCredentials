package local.jfx360.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;

public class PasswordUtils {

    // Length of the salt
    private static final int SALT_LENGTH = 16;

    // Number of iterations for PBKDF2 hashing
    private static final int ITERATIONS = 10000;

    // Length of the hash output
    private static final int HASH_LENGTH = 256;

    /**
     * Hashes a given password with a randomly generated salt.
     *
     * @param password The plain-text password to hash
     * @return A hashed password in the format "salt:hash"
     */
    public static String hashPassword(String password) {
        byte[] salt = generateSalt();
        byte[] hash = hashPasswordWithSalt(password.toCharArray(), salt);

        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        String encodedHash = Base64.getEncoder().encodeToString(hash);

        return encodedSalt + ":" + encodedHash;
    }

    /**
     * Verifies a given password against a stored hashed password.
     *
     * @param password The plain-text password to verify
     * @param stored   The stored hashed password in the format "salt:hash"
     * @return True if the password matches, false otherwise
     */
    public static boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Stored password must have the format 'salt:hash'");
        }

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] hash = Base64.getDecoder().decode(parts[1]);

        byte[] hashedInput = hashPasswordWithSalt(password.toCharArray(), salt);

        return Base64.getEncoder().encodeToString(hash).equals(Base64.getEncoder().encodeToString(hashedInput));
    }

    // Generates a random salt for hashing
    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        return salt;
    }

    // Hashes a password with the provided salt
    private static byte[] hashPasswordWithSalt(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_LENGTH);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error while hashing password", e);
        }
    }
}
