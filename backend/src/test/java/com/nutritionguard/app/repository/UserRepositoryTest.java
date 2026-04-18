package com.nutritionguard.app.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

  @Test
  void normalizeEmail_trimsAndLowercases() {
    assertEquals("hermelaanteneh21@gmail.com", UserRepository.normalizeEmail("  HermelaAnteneh21@Gmail.com  "));
  }

  @Test
  void findByEmailSql_isCaseAndTrimInsensitive() {
    assertTrue(UserRepository.FIND_BY_EMAIL_SQL.contains("LOWER(TRIM(email))"));
    assertTrue(UserRepository.FIND_BY_EMAIL_SQL.contains("public.users"));
  }
}
