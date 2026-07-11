package com.nutritionguard.app.service;

import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.LoginOption;

import java.util.List;

public class PasswordResetMailer {
  private final AppConfig config;
  private final MailClient client; // nullable

  public PasswordResetMailer(Vertx vertx, AppConfig config) {
    this.config = config;
    if (isBlank(config.smtpHost())) {
      this.client = null;
      return;
    }

    String host = config.smtpHost().trim();
    int port = config.smtpPort();
    boolean startTls = config.smtpStartTls();

    MailConfig mailConfig = new MailConfig()
      .setHostname(host)
      .setPort(port)
      .setTrustAll(true)
      .setAuthMethods("PLAIN LOGIN")
      .setStarttls(startTls ? io.vertx.ext.mail.StartTLSOptions.REQUIRED : io.vertx.ext.mail.StartTLSOptions.DISABLED);

    if (!isBlank(config.smtpUsername())) {
      mailConfig.setUsername(config.smtpUsername().trim());
      // Some SMTP servers won't attempt auth unless login is explicitly required.
      mailConfig.setLogin(LoginOption.REQUIRED);
    }
    if (!isBlank(config.smtpPassword())) {
      String raw = config.smtpPassword();
      String sanitized = sanitizeLikelyAppPassword(raw);
      mailConfig.setPassword(sanitized);
      if (!raw.equals(sanitized)) {
        System.out.println("SMTP_PASSWORD contains whitespace; using a whitespace-stripped value. Update your .env to remove spaces.");
      }
    }

    if (!isBlank(config.smtpUsername()) && isBlank(config.smtpPassword())) {
      System.err.println("SMTP is configured but SMTP_PASSWORD is empty; password-reset emails will fail to send.");
    }

    this.client = MailClient.createShared(vertx, mailConfig);
  }

  public Future<Void> sendPasswordResetEmail(String toEmail, String resetLink, int ttlMinutes) {
    if (isBlank(toEmail)) {
      return Future.succeededFuture();
    }

    if (client == null) {
      // Avoid logging the full token (URLs often get ingested into logs).
      System.out.printf("Password reset requested for %s. SMTP not configured.%n", maskEmail(toEmail));
      return Future.succeededFuture();
    }

    String from = isBlank(config.smtpFrom()) ? "no-reply@nutritionguard.local" : config.smtpFrom().trim();
    MailMessage message = new MailMessage()
      .setFrom(from)
      .setTo(List.of(toEmail))
      .setSubject("Reset your NutritionGuard AI password")
      .setText("""
        A password reset was requested for your NutritionGuard AI account.

        Reset link (expires in %d minutes):
        %s

        If you did not request this, you can ignore this email.
        """.formatted(ttlMinutes, resetLink));

    return client.sendMail(message).mapEmpty();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String maskEmail(String email) {
    if (email == null) return "(null)";
    String e = email.trim();
    int at = e.indexOf('@');
    if (at <= 1) return "***";
    String local = e.substring(0, at);
    String domain = e.substring(at + 1);
    String maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
    return maskedLocal + "@" + domain;
  }

  /**
   * Gmail app passwords are often copied with spaces every 4 characters (e.g. "abcd efgh ijkl mnop").
   * SMTP auth expects the password without those spaces.
   */
  private static String sanitizeLikelyAppPassword(String raw) {
    if (raw == null) return "";
    String trimmed = raw.trim();
    if (!trimmed.matches("(?i)^[a-z0-9]{4}(\\s+[a-z0-9]{4}){3}$")) {
      return trimmed;
    }
    return trimmed.replaceAll("\\s+", "");
  }
}
