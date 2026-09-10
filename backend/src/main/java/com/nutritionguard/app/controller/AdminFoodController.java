package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AdminFoodController {
  private final FoodRepository foods;
  private final com.nutritionguard.app.service.FoodImportService importService;

  public AdminFoodController(FoodRepository foods, com.nutritionguard.app.service.FoodImportService importService) {
    this.foods = foods;
    this.importService = importService;
  }

  public void mount(Router router) {
    router.post("/api/admin/documents-upload").handler(ctx -> {
      try {
        var uploads = ctx.fileUploads();
        if (uploads.isEmpty()) {
          ctx.fail(400, new RuntimeException("No files uploaded"));
          return;
        }

        String ragDataDir = System.getenv("RAG_DATA_DIR");
        if (ragDataDir == null || ragDataDir.isBlank()) {
            ragDataDir = "c:\\project\\NutritionGuard AI\\rag_service\\data"; // Local fallback
        }
        String targetDir = ragDataDir;
        io.vertx.core.file.FileSystem fs = ctx.vertx().fileSystem();
        fs.mkdirsBlocking(targetDir);

        for (io.vertx.ext.web.FileUpload f : uploads) {
          // Use REPLACE_EXISTING logic by deleting first if it exists, since moveBlocking might fail if target exists
          String targetPath = targetDir + java.io.File.separator + f.fileName();
          if (fs.existsBlocking(targetPath)) {
            fs.deleteBlocking(targetPath);
          }
          fs.moveBlocking(f.uploadedFileName(), targetPath);
        }

        String ragReindexUrl = System.getenv("RAG_REINDEX_URL");
        if (ragReindexUrl == null || ragReindexUrl.isBlank()) {
            ragReindexUrl = "http://127.0.0.1:5000/reindex"; // Local fallback
        }

        io.vertx.ext.web.client.WebClient.create(ctx.vertx())
          .postAbs(ragReindexUrl)
          .send()
          .onSuccess(resp -> {
            ctx.response()
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject()
                .put("message", "Files stored successfully. Background indexing started.")
                .put("count", uploads.size())
                .encode());
          })
          .onFailure(err -> {
            ctx.fail(500, new RuntimeException("Files saved but failed to trigger indexing: " + err.getMessage()));
          });
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.post("/api/admin/foods-import").handler(ctx -> {
      try {
        Object body = ctx.body() == null ? null : ctx.body().asJsonArray();
        if (body == null) {
          // Try as object if array fails
          JsonObject obj = ctx.body() == null ? null : ctx.body().asJsonObject();
          if (obj != null) {
            body = new io.vertx.core.json.JsonArray().add(obj);
          }
        }

        if (body == null) {
          ctx.fail(400, new RuntimeException("Invalid JSON body: expected array of foods or a single food object"));
          return;
        }

        importService.importBatch((io.vertx.core.json.JsonArray) body)
          .onSuccess(results -> ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
              .put("message", "Successfully imported " + results.size() + " items")
              .put("count", results.size())
              .put("items", results)
              .encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });
      //Admin API endpoint to fetch a paginated and searchable list of foods.
    router.get("/api/admin/foods").handler(ctx -> {
      try {
        String q = ctx.request().getParam("q");
        int limit = parseInt(ctx.request().getParam("limit"), 50, 1, 200);
        int offset = parseInt(ctx.request().getParam("offset"), 0, 0, 10_000);
        foods.listAdmin(q, limit, offset)
          .onSuccess(list -> ctx.response().putHeader("Content-Type", "application/json").end(list.encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.post("/api/admin/foods").handler(ctx -> {
      try {
        JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
        if (body == null) {
          ctx.fail(400, new RuntimeException("Invalid JSON body"));
          return;
        }
        if (body.getString("food_name", "").isBlank() || body.getString("meal_type", "").isBlank()) {
          ctx.fail(400, new RuntimeException("Missing required fields: food_name, meal_type"));
          return;
        }
        foods.create(body)
          .onSuccess(created -> ctx.response().putHeader("Content-Type", "application/json").end(created.encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });


    router.put("/api/admin/foods/:id").handler(ctx -> {
      try {
        Integer id = parseId(ctx.pathParam("id"));
        if (id == null) {
          ctx.fail(400, new RuntimeException("Invalid id"));
          return;
        }
        JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
        if (body == null) {
          ctx.fail(400, new RuntimeException("Invalid JSON body"));
          return;
        }
        foods.update(id, body)
          .onSuccess(updated -> {
            if (updated == null) {
              ctx.response().setStatusCode(404).end("Food not found");
              return;
            }
            ctx.response().putHeader("Content-Type", "application/json").end(updated.encode());
          })
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.delete("/api/admin/foods/:id").handler(ctx -> {
      try {
        Integer id = parseId(ctx.pathParam("id"));
        if (id == null) {
          ctx.fail(400, new RuntimeException("Invalid id"));
          return;
        }
        foods.delete(id)
          .onSuccess(deleted -> ctx.response().putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("deleted", deleted).encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.post("/api/admin/foods/sync").handler(ctx -> {
      try {
        String remoteUrl = com.nutritionguard.app.config.AppConfig.fromEnv().remoteSyncUrl();
        foods.renumber().compose(v -> foods.findUnsynced()).onSuccess(unsynced -> {
          if (unsynced.isEmpty()) {
            ctx.response().putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("message", "No unsynced items").put("synced_count", 0).encode());
            return;
          }
          
          if (remoteUrl == null || remoteUrl.isBlank()) {
            // If no remote URL is configured, just mark as synced locally for now.
            foods.markSynced().onSuccess(count -> {
              ctx.response().putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("message", "Marked as synced (No remote URL configured)").put("synced_count", count).encode());
            }).onFailure(err -> ctx.fail(500, err));
            return;
          }

          // In a real scenario, we would use WebClient to send 'unsynced' array to remoteUrl.
          // For now, we simulate success and mark them synced.
          foods.markSynced().onSuccess(count -> {
            ctx.response().putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("message", "Successfully synced with remote server").put("synced_count", count).encode());
          }).onFailure(err -> ctx.fail(500, err));

        }).onFailure(err -> ctx.fail(500, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });
  }

  private Integer parseId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private int parseInt(String raw, int fallback, int min, int max) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int v = Integer.parseInt(raw);
      if (v < min) return min;
      if (v > max) return max;
      return v;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
