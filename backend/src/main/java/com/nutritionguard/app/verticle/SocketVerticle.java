package com.nutritionguard.app.verticle;

import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.service.VitaminChatService;
import com.nutritionguard.app.util.EventAddresses;
import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class SocketVerticle extends AbstractVerticle {
  private final Database db;
  private final AppConfig config;

  public SocketVerticle(Database db, AppConfig config) {
    this.db = db;
    this.config = config;
  }

  @Override
  public void start() {
    VitaminChatService service = new VitaminChatService(vertx, config, new FoodRepository(db));
    vertx.eventBus().consumer(EventAddresses.CHAT_ASK, msg -> {
      JsonObject body = (JsonObject) msg.body();
      service.ask(body)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(400, err.getMessage()));
    });
  }
}
