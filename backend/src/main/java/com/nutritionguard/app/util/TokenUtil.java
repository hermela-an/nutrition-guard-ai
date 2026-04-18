package com.nutritionguard.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class TokenUtil {
  private static final SecureRandom RNG = new SecureRandom();

  private TokenUtil() {}

  public static String generateUrlSafeToken(int numBytes) {
    int n = Math.max(16, Math.min(numBytes, 64));
    byte[] buf = new byte[n];
    RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  public static String sha256Hex(String input) {
    if (input == null) return "";
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing SHA-256", e);
    }
    byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
    return toHex(digest);
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}

