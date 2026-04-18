package com.nutritionguard.app.util;

import org.mindrot.jbcrypt.BCrypt;

public final class HashUtil {
  private HashUtil() {}

  public static String hashPassword(String password) {
    return BCrypt.hashpw(password, BCrypt.gensalt(10));
  }

  public static boolean verifyPassword(String password, String hash) {
    return BCrypt.checkpw(password, hash);
  }

  public static boolean isBcryptHash(String hash) {
    if (hash == null) return false;
    String h = hash.trim();
    return h.startsWith("$2a$") || h.startsWith("$2b$") || h.startsWith("$2y$");
  }

  /**
   * Backwards-compatible verification: supports BCrypt hashes and legacy plaintext stored passwords.
   */
  public static boolean verifyPasswordFlexible(String password, String stored) {
    if (password == null || stored == null) return false;
    if (!isBcryptHash(stored)) {
      return password.equals(stored);
    }
    try {
      return verifyPassword(password, stored);
    } catch (Exception ignored) {
      return false;
    }
  }
}
