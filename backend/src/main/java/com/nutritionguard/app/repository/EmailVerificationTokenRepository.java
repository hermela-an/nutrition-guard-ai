package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.OffsetDateTime;
import java.util.UUID;

public class EmailVerificationTokenRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public EmailVerificationTokenRepository(Database db) {
    this.db = db;
  }

  private Future<Void> ensureSchema() {
    if (bootstrap != null) return bootstrap;
    synchronized (this) {
      if (bootstrap != null) return bootstrap;

      String ddl = """
      CREATE TABLE IF NOT EXISTS email_verification_tokens (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        token_hash TEXT UNIQUE NOT NULL,
        expires_at TIMESTAMPTZ NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );

      ALTER TABLE email_verification_tokens
        ADD COLUMN IF NOT EXISTS user_id TEXT,
        ADD COLUMN IF NOT EXISTS token_hash TEXT,
        ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

      -- Best-effort FK (may fail if users table isn't present yet; safe to ignore in that case).
      DO $$
      BEGIN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='users') THEN
          IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_email_verification_tokens_user'
              AND conrelid = 'public.email_verification_tokens'::regclass
          ) THEN
            ALTER TABLE public.email_verification_tokens
              ADD CONSTRAINT fk_email_verification_tokens_user
              FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
          END IF;
        END IF;
      EXCEPTION
        WHEN undefined_table THEN
          -- ignore
      END
      $$;

      CREATE UNIQUE INDEX IF NOT EXISTS idx_email_verification_tokens_hash_unique ON email_verification_tokens (token_hash);
      CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
      CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_expires_at ON email_verification_tokens (expires_at);
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<Void> revokeAllForUser(String userId) {
    String sql = "DELETE FROM email_verification_tokens WHERE user_id = $1";
    return ensureSchema().compose(ignored -> db.query(sql, Tuple.of(userId)).mapEmpty());
  }

  public Future<JsonObject> create(String userId, String tokenHash, OffsetDateTime expiresAt) {
    String id = UUID.randomUUID().toString();
    String sql = """
      INSERT INTO email_verification_tokens (id, user_id, token_hash, expires_at)
      VALUES ($1, $2, $3, $4)
      RETURNING id, user_id, expires_at, created_at
      """;
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(id, userId, tokenHash, expiresAt))
        .map(rows -> {
          Row row = rows.iterator().next();
          return new JsonObject()
            .put("id", row.getString("id"))
            .put("user_id", row.getString("user_id"))
            .put("expires_at", row.getOffsetDateTime("expires_at").toString())
            .put("created_at", row.getOffsetDateTime("created_at").toString());
        })
    );
  }

  /**
   * Verifies the user linked to the token and deletes the token if valid.
   * Returns the verified user id, or null if token invalid/expired.
   */
  public Future<String> consumeAndVerify(String tokenHash) {
    String sql = """
      WITH token_row AS (
        SELECT user_id
        FROM email_verification_tokens
        WHERE token_hash = $1
          AND expires_at > now()
        LIMIT 1
      ),
      updated AS (
        UPDATE users u
        SET is_verified = true
        FROM token_row t
        WHERE u.id = t.user_id
          AND u.is_active = true
        RETURNING u.id AS user_id
      ),
      deleted AS (
        DELETE FROM email_verification_tokens evt
        USING token_row t
        WHERE evt.user_id = t.user_id
        RETURNING evt.user_id
      )
      SELECT user_id FROM updated
      """;

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(tokenHash))
        .map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getString("user_id") : null)
    );
  }
}

