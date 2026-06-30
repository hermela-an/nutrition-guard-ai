package com.nutritionguard.app.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class RagService {
  private final WebClient webClient;
  private final String ragApiUrl;

  public RagService(Vertx vertx, String ragApiUrl) {
    this.webClient = vertx != null
      ? WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true))
      : null;
    this.ragApiUrl = ragApiUrl != null && !ragApiUrl.isBlank()
      ? ragApiUrl
      : null;
  }

  public Future<String> query(String question) {
    if (ragApiUrl == null || ragApiUrl.isBlank() || webClient == null) {
      return Future.succeededFuture("");
    }

    JsonObject body = new JsonObject().put("query", question != null ? question : "");

    return webClient.postAbs(ragApiUrl)
      .timeout(20000)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .map(response -> {
        if (response.statusCode() != 200) return "";
        JsonObject json = response.bodyAsJsonObject();
        if (json == null) return "";
        String context = json.getString("context");
        if (context != null && !context.isBlank()) return context;
        JsonObject results = json.getJsonObject("results");
        if (results != null) return results.encode();
        return "";
      })
      .recover(err -> {
        System.err.println("RAG service error: " + err.getMessage());
        return Future.succeededFuture("");
      });
  }
}
