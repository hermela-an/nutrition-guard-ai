package com.nutritionguard.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AppConfig(
  int httpPort,
  String dbHost,
  int dbPort,
  String dbName,
  String dbSchema,
  String dbUser,
  String dbPassword,
  String jwtSecret
) {
  private static final EnvFile ENV_FILE = EnvFile.loadFromWorkingDirectory();

  public static AppConfig fromEnv() {
    return new AppConfig(
      intEnv("APP_PORT", 8080),
      strEnv("DB_HOST", "localhost"),
      // docker-compose exposes Postgres on localhost:5433 by default for this repo
      intEnv("DB_PORT", 5433),
      strEnv("DB_NAME", "nutritionguard"),
      strEnv("DB_SCHEMA", "public"),
      strEnv("DB_USER", "nutritionguard"),
      strEnv("DB_PASSWORD", "nutritionguard"),
      strEnv("JWT_SECRET", "change-this-secret-for-prod")
    );
  }

  public static String loadedEnvFile() {
    return ENV_FILE.path();
  }

  private static String strEnv(String key, String fallback) {
    String value = lookup(key);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int intEnv(String key, int fallback) {
    String value = lookup(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException nfe) {
      return fallback;
    }
  }

  private static String lookup(String key) {
    String env = System.getenv(key);
    if (env != null) {
      return env;
    }
    return ENV_FILE.values().get(key);
  }

  private record EnvFile(String path, Map<String, String> values) {
    static EnvFile loadFromWorkingDirectory() {
      Path cwd = Paths.get(System.getProperty("user.dir"));

      // Prefer local overrides, then regular .env. Support running from either:
      // - backend/ (scripts)
      // - repo root (IDE / mvn from root)
      Path local = cwd.resolve(".env.local");
      Path regular = cwd.resolve(".env");
      Path backendLocal = cwd.resolve("backend").resolve(".env.local");
      Path backendRegular = cwd.resolve("backend").resolve(".env");

      if (Files.exists(local)) {
        return new EnvFile(local.toString(), parse(local));
      }
      if (Files.exists(regular)) {
        return new EnvFile(regular.toString(), parse(regular));
      }
      if (Files.exists(backendLocal)) {
        return new EnvFile(backendLocal.toString(), parse(backendLocal));
      }
      if (Files.exists(backendRegular)) {
        return new EnvFile(backendRegular.toString(), parse(backendRegular));
      }
      return new EnvFile(null, Map.of());
    }

    private static Map<String, String> parse(Path file) {
      Map<String, String> map = new HashMap<>();
      List<String> lines;
      try {
        lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return Map.of();
      }

      for (String line : lines) {
        if (line == null) continue;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        int idx = trimmed.indexOf('=');
        if (idx <= 0) continue;

        String key = trimmed.substring(0, idx).trim();
        String value = trimmed.substring(idx + 1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1);
        }

        if (!key.isEmpty()) {
          map.put(key, value);
        }
      }
      return Map.copyOf(map);
    }
  }
}
