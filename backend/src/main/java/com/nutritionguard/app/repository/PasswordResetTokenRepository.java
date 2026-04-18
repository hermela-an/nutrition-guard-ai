package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PasswordResetTokenRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public PasswordResetTokenRepository(Database db) {
    this.db = db;
  }

  private Future<Void> ensureSchema() {
    if (bootstrap != null) return bootstrap;
    synchronized (this) {
      if (bootstrap != null) return bootstrap;

      String ddl = """
      CREATE TABLE IF NOT EXISTS password_reset_tokens (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        token_hash TEXT UNIQUE NOT NULL,
        expires_at TIMESTAMPTZ NOT NULL,
        consumed_at TIMESTAMPTZ NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );

      ALTER TABLE password_reset_tokens
        ADD COLUMN IF NOT EXISTS user_id TEXT,
        ADD COLUMN IF NOT EXISTS token_hash TEXT,
        ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
        ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ NULL,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

      -- Best-effort FK (may fail if users table isn't present yet; safe to ignore in that case).
      DO $$
      BEGIN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='users') THEN
          IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_password_reset_tokens_user'
              AND conrelid = 'public.password_reset_tokens'::regclass
          ) THEN
            ALTER TABLE public.password_reset_tokens
              ADD CONSTRAINT fk_password_reset_tokens_user
              FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
          END IF;
        END IF;
      EXCEPTION
        WHEN undefined_table THEN
          -- ignore
      END
      $$;

      CREATE UNIQUE INDEX IF NOT EXISTS idx_password_reset_tokens_hash_unique ON password_reset_tokens (token_hash);
      CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
      CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<Void> revokeAllForUser(String userId) {
    String sql = "DELETE FROM password_reset_tokens WHERE user_id = $1";
    return ensureSchema().compose(ignored -> db.query(sql, Tuple.of(userId)).mapEmpty());
  }

  public Future<JsonObject> create(String userId, String tokenHash, OffsetDateTime expiresAt) {
    String id = UUID.randomUUID().toString();
    String sql = """
      INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at)
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

  public Future<String> consumeAndUpdatePasswordHash(String tokenHash, String newPasswordHash) {
    String sql = """
      WITH consumed AS (
        UPDATE password_reset_tokens prt
        SET consumed_at = now()
        FROM users u
        WHERE prt.user_id = u.id
          AND u.is_active = true
          AND prt.token_hash = $1
          AND prt.consumed_at IS NULL
          AND prt.expires_at > now()
        RETURNING prt.user_id
      )
      UPDATE users u
      SET password_hash = $2
      FROM consumed c
      WHERE u.id = c.user_id
      RETURNING u.id AS user_id
      """;

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(tokenHash, newPasswordHash))
        .map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getString("user_id") : null)
    );
  }
}
