package com.nutritionguard.app.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

  @Test
  void bcryptHashesVerify() {
    String hash = HashUtil.hashPassword("belport");
    assertTrue(HashUtil.isBcryptHash(hash));
    assertTrue(HashUtil.verifyPasswordFlexible("belport", hash));
    assertFalse(HashUtil.verifyPasswordFlexible("wrong", hash));
  }

  @Test
  void plaintextLegacyVerifies() {
    assertFalse(HashUtil.isBcryptHash("belport"));
    assertTrue(HashUtil.verifyPasswordFlexible("belport", "belport"));
    assertFalse(HashUtil.verifyPasswordFlexible("belport", "Belport"));
  }
}

