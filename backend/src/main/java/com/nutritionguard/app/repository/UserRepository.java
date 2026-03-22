package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class UserRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public UserRepository(Database db) {
    this.db = db;
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
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );

      ALTER TABLE users
        ADD COLUMN IF NOT EXISTS password_hash TEXT,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

      CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON users (email);
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<JsonObject> createUser(String email, String passwordHash) {
    String id = UUID.randomUUID().toString();
    String sql = "INSERT INTO users (id, email, password_hash) VALUES ($1, $2, $3) RETURNING id, email, created_at";
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(id, email, passwordHash))
        .map(rows -> {
          Row row = rows.iterator().next();
          return new JsonObject()
            .put("id", row.getString("id"))
            .put("email", row.getString("email"))
            .put("created_at", row.getOffsetDateTime("created_at").toString());
        })
    );
  }

  public Future<JsonObject> findByEmail(String email) {
    String sql = "SELECT id, email, password_hash FROM users WHERE email = $1";
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
            .put("password_hash", row.getString("password_hash"));
        })
    );
  }
}
