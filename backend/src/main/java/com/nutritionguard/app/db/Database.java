package com.nutritionguard.app.db;

import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class Database {
  private final PgPool pool;
  private final String setSearchPathSql;

  public Database(Vertx vertx, AppConfig config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(config.dbHost())
      .setPort(config.dbPort())
      .setDatabase(config.dbName())
      .setUser(config.dbUser())
      .setPassword(config.dbPassword())
      .setCachePreparedStatements(true);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(10);
    this.pool = PgPool.pool(vertx, connectOptions, poolOptions);

    String schema = config.dbSchema();
    if (schema == null || schema.isBlank() || "public".equalsIgnoreCase(schema.trim())) {
      this.setSearchPathSql = "SET search_path TO public";
      return;
    }

    String normalized = schema.trim();
    if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid DB_SCHEMA: " + schema);
    }
    this.setSearchPathSql = "SET search_path TO " + normalized + ",public";
  }

  public PgPool pool() {
    return pool;
  }

  public Future<Void> ping() {
    return pool.withConnection(conn ->
      conn.query(setSearchPathSql).execute()
        .compose(ignored -> conn.preparedQuery("SELECT 1").execute())
        .mapEmpty()
    );
  }

  public Future<RowSet<Row>> query(String sql, Tuple params) {
    return pool.withConnection(conn ->
      conn.query(setSearchPathSql).execute()
        .compose(ignored -> conn.preparedQuery(sql).execute(params))
    );
  }
}
