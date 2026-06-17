package com.nutritionguard.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
  String jwtSecret,
  String adminEmails,
  String frontendBaseUrl,
  int passwordResetTtlMinutes,
  String smtpHost,
  int smtpPort,
  boolean smtpStartTls,
  String smtpUsername,
  String smtpPassword,
  String smtpFrom,
  String geminiApiKey,
  String usdaApiKey,
  String remoteSyncUrl,
  String ragApiUrl
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
      strEnv("JWT_SECRET", "change-this-secret-for-prod"),
      strEnv("ADMIN_EMAILS", strEnv("ADMIN_EMAIL", "")),
      strEnv("FRONTEND_BASE_URL", "http://localhost:4200"),
      intEnv("PASSWORD_RESET_TTL_MINUTES", 20),
      strEnv("SMTP_HOST", ""),
      intEnv("SMTP_PORT", 587),
      boolEnv("SMTP_STARTTLS", true),
      strEnv("SMTP_USERNAME", ""),
      strEnv("SMTP_PASSWORD", ""),
      strEnv("SMTP_FROM", "no-reply@nutritionguard.local"),
      strEnv("GEMINI_API_KEY", ""),
      strEnv("USDA_API_KEY", ""),
      strEnv("REMOTE_SYNC_URL", ""),
      strEnv("RAG_API_URL", "http://localhost:5000/query")
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

  private static boolean boolEnv(String key, boolean fallback) {
    String value = lookup(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String v = value.trim().toLowerCase();
    if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v) || "on".equals(v)) return true;
    if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v) || "off".equals(v)) return false;
    return fallback;
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

      // Support running from either:
      // - backend/ (scripts)
      // - repo root (IDE / mvn from root)
      //
      // Load BOTH .env and .env.local when present, with .env.local overriding .env.
      EnvFile fromCwd = loadMerged(cwd.resolve(".env"), cwd.resolve(".env.local"));
      if (fromCwd != null) return fromCwd;

      EnvFile fromBackend = loadMerged(
        cwd.resolve("backend").resolve(".env"),
        cwd.resolve("backend").resolve(".env.local")
      );
      if (fromBackend != null) return fromBackend;

      return new EnvFile(null, Map.of());
    }

    private static EnvFile loadMerged(Path regular, Path local) {
      boolean hasRegular = Files.exists(regular);
      boolean hasLocal = Files.exists(local);
      if (!hasRegular && !hasLocal) {
        return null;
      }

      Map<String, String> merged = new HashMap<>();
      List<String> paths = new ArrayList<>();

      if (hasRegular) {
        merged.putAll(parse(regular));
        paths.add(regular.toString());
      }
      if (hasLocal) {
        merged.putAll(parse(local));
        paths.add(local.toString());
      }

      String joined = paths.isEmpty() ? null : String.join(" + ", paths);
      return new EnvFile(joined, Map.copyOf(merged));
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
