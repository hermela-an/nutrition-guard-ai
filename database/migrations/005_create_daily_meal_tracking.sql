CREATE TABLE IF NOT EXISTS daily_meal_recommendations (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_date DATE NOT NULL,
  meal_type TEXT NOT NULL,
  food_item_id INT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, plan_date, meal_type, food_item_id)
);

CREATE INDEX IF NOT EXISTS idx_daily_meal_recommendations_user_date
  ON daily_meal_recommendations (user_id, plan_date);

CREATE TABLE IF NOT EXISTS daily_meal_intake (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  intake_date DATE NOT NULL,
  meal_type TEXT NOT NULL,
  food_item_id INT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
  eaten BOOLEAN NOT NULL DEFAULT FALSE,
  eaten_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, intake_date, meal_type, food_item_id)
);

CREATE INDEX IF NOT EXISTS idx_daily_meal_intake_user_date
  ON daily_meal_intake (user_id, intake_date);

