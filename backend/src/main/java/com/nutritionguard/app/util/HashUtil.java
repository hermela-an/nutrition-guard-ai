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
}
