package com.nutritionguard.app.verticle;

import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.repository.ProfileRepository; // <-- ADD THIS IMPORT
import com.nutritionguard.app.service.RagService;
import com.nutritionguard.app.service.VitaminChatService;
import com.nutritionguard.app.service.ProfileService; // <-- ADD THIS IMPORT
import com.nutritionguard.app.util.EventAddresses;
import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.AbstractVerticle;
import com.nutritionguard.app.service.QueryUnderstandingService;
import io.vertx.core.Future;
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
    RagService ragService = new RagService(vertx, config.ragApiUrl());
    QueryUnderstandingService queryUnderstandingService = new QueryUnderstandingService(config, q -> Future.succeededFuture(""));
    
    // 1. CREATE THE PROFILE REPOSITORY AND SERVICE HERE!
    ProfileRepository profileRepository = new ProfileRepository(db);
    ProfileService profileService = new ProfileService(profileRepository);

    // 2. Pass the newly created profileService into the constructor
    VitaminChatService service = new VitaminChatService(
        vertx, 
        config, 
        new FoodRepository(db), 
        ragService, 
        queryUnderstandingService, 
        profileService // <-- NOW THIS WILL WORK!
    );

    vertx.eventBus().consumer(EventAddresses.CHAT_ASK, msg -> {
      JsonObject body = (JsonObject) msg.body();

      // 1. Extract the userId we attached in the Router
      String userId = body.getString("user_id");
      
      // 2. Pass both userId and body to the updated ask() method
      service.ask(userId, body)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(400, err.getMessage()));
    });
  }
}