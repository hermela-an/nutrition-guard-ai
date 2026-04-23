package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class UserRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public static final String FIND_BY_EMAIL_SQL =
    "SELECT id, email, password_hash, role, is_active, is_verified FROM public.users WHERE LOWER(TRIM(email)) = LOWER(TRIM($1))";

  public UserRepository(Database db) {
    this.db = db;
  }

  public static String normalizeEmail(String email) {
    if (email == null) return "";
    return email.trim().toLowerCase();
  }

  private Future<Void> ensureSchema() {
    if (bootstrap != null) return bootstrap;
    synchronized (this) {
      if (bootstrap != null) return bootstrap;

      String ddl = """
      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL DEFAULT 'USER',
        is_active BOOLEAN NOT NULL DEFAULT true,
        is_verified BOOLEAN NOT NULL DEFAULT true,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );

      ALTER TABLE users
        ADD COLUMN IF NOT EXISTS password_hash TEXT,
        ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER',
        ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true,
        ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT true,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

      CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON users (email);

      DO $$
      BEGIN
        IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint
          WHERE conname = 'chk_users_role_valid'
            AND conrelid = 'public.users'::regclass
        ) THEN
          ALTER TABLE public.users
            ADD CONSTRAINT chk_users_role_valid
            CHECK (role IN ('USER', 'ADMIN'));
        END IF;
      END
      $$;
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<JsonObject> createUser(String email, String passwordHash) {
    return createUser(email, passwordHash, "USER");
  }

  public Future<JsonObject> createUser(String email, String passwordHash, String role) {
    return createUser(email, passwordHash, role, true);
  }

  public Future<JsonObject> createUser(String email, String passwordHash, String role, boolean isVerified) {
    String id = UUID.randomUUID().toString();
    String normalizedRole = normalizeRole(role);
    String sql = """
      INSERT INTO users (id, email, password_hash, role, is_verified)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING id, email, role, is_active, is_verified, created_at
      """;
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(id, email, passwordHash, normalizedRole, isVerified))
        .map(rows -> {
          Row row = rows.iterator().next();
          return new JsonObject()
            .put("id", row.getString("id"))
            .put("email", row.getString("email"))
            .put("role", row.getString("role"))
            .put("is_active", row.getBoolean("is_active"))
            .put("is_verified", row.getBoolean("is_verified"))
            .put("created_at", row.getOffsetDateTime("created_at").toString());
        })
    );
  }

  public Future<JsonObject> findByEmail(String email) {
    String sql = FIND_BY_EMAIL_SQL;
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(email))
        .map(rows -> {
          if (!rows.iterator().hasNext()) {
            return null;
          }
          Row row = rows.iterator().next();
          return new JsonObject()
            .put("id", row.getString("id"))
            .put("email", row.getString("email"))
            .put("role", row.getString("role"))
            .put("is_active", row.getBoolean("is_active"))
            .put("is_verified", row.getBoolean("is_verified"))
            .put("password_hash", row.getString("password_hash"));
        })
    );
  }

  private String normalizeRole(String role) {
    String r = role == null ? "" : role.trim().toUpperCase();
    return "ADMIN".equals(r) ? "ADMIN" : "USER";
  }

  public Future<JsonArray> list(String query, String status, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 50_000));
    String q = query == null ? "" : query.trim();
    String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase();

    String where = "WHERE ($1 = '' OR LOWER(email) LIKE LOWER($2))";
    if ("ACTIVE".equals(normalizedStatus)) {
      where += " AND is_active = true";
    } else if ("INACTIVE".equals(normalizedStatus)) {
      where += " AND is_active = false";
    }

    String sql = """
      SELECT id, email, role, is_active, created_at
      FROM users
      %s
      ORDER BY created_at DESC
      LIMIT $3 OFFSET $4
      """.formatted(where);

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(q, "%" + q + "%", safeLimit, safeOffset))
        .map(rows -> {
          JsonArray out = new JsonArray();
          for (Row row : rows) {
            out.add(new JsonObject()
              .put("id", row.getString("id"))
              .put("email", row.getString("email"))
              .put("role", row.getString("role"))
              .put("is_active", row.getBoolean("is_active"))
              .put("created_at", row.getOffsetDateTime("created_at").toString())
            );
          }
          return out;
        })
    );
  }

  public Future<Integer> count(String query, String status) {
    String q = query == null ? "" : query.trim();
    String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase();

    String where = "WHERE ($1 = '' OR LOWER(email) LIKE LOWER($2))";
    if ("ACTIVE".equals(normalizedStatus)) {
      where += " AND is_active = true";
    } else if ("INACTIVE".equals(normalizedStatus)) {
      where += " AND is_active = false";
    }

    String sql = ("SELECT COUNT(*)::int AS c FROM users " + where);
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(q, "%" + q + "%"))
        .map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getInteger("c") : 0)
    );
  }

  public Future<JsonObject> update(String id, JsonObject input) {
    JsonObject safe = input == null ? new JsonObject() : input;

    String sql = """
      UPDATE users
      SET
        role = COALESCE($2, role),
        is_active = COALESCE($3, is_active)
      WHERE id = $1
      RETURNING id, email, role, is_active, created_at
      """;

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(
        id,
        safe.containsKey("role") ? safe.getString("role") : null,
        safe.containsKey("is_active") ? safe.getBoolean("is_active") : null
      )).map(rows -> {
        if (!rows.iterator().hasNext()) return null;
        Row row = rows.iterator().next();
        return new JsonObject()
          .put("id", row.getString("id"))
          .put("email", row.getString("email"))
          .put("role", row.getString("role"))
          .put("is_active", row.getBoolean("is_active"))
          .put("created_at", row.getOffsetDateTime("created_at").toString());
      })
    );
  }

  public Future<Integer> delete(String id) {
    String sql = "DELETE FROM users WHERE id = $1";
    return ensureSchema().compose(ignored -> db.query(sql, Tuple.of(id)).map(RowSet::rowCount));
  }
}
