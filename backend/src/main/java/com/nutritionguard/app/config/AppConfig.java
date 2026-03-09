package com.nutritionguard.app.config;

public record AppConfig(
  int httpPort,
  String dbHost,
  int dbPort,
  String dbName,
  String dbUser,
  String dbPassword,
  String jwtSecret
) {
  public static AppConfig fromEnv() {
    return new AppConfig(
      intEnv("APP_PORT", 8080),
      strEnv("DB_HOST", "localhost"),
      intEnv("DB_PORT", 5432),
      strEnv("DB_NAME", "nutritionguard"),
      strEnv("DB_USER", "nutritionguard"),
      strEnv("DB_PASSWORD", "nutritionguard"),
      strEnv("JWT_SECRET", "change-this-secret-for-prod")
    );
  }

  private static String strEnv(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int intEnv(String key, int fallback) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException nfe) {
      return fallback;
    }
  }
}
