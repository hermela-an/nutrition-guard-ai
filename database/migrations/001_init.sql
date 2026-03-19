CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS profiles (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  age INT NOT NULL CHECK (age > 0),
  gender TEXT NOT NULL,
  height_cm DOUBLE PRECISION NOT NULL CHECK (height_cm > 0),
  weight_kg DOUBLE PRECISION NOT NULL CHECK (weight_kg > 0),
  activity_level TEXT NOT NULL,
  goal TEXT NOT NULL,
  health_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_profiles_health_metadata ON profiles USING GIN (health_metadata);

CREATE TABLE IF NOT EXISTS food_items (
  id SERIAL PRIMARY KEY,
  food_name TEXT NOT NULL,
  meal_type TEXT NOT NULL,
  portion_amount DOUBLE PRECISION NOT NULL,
  portion_unit TEXT NOT NULL,
  calories INT NOT NULL CHECK (calories >= 0),
  protein_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  carbs_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  fat_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  sugar_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  sodium_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
  cholesterol_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
  vitamins JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_food_items_food_name ON food_items (LOWER(food_name));
CREATE INDEX IF NOT EXISTS idx_food_items_meal_type ON food_items (meal_type);

CREATE TABLE IF NOT EXISTS daily_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  log_date DATE NOT NULL,
  weight_kg DOUBLE PRECISION NOT NULL,
  consumed JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_daily_logs_user_date ON daily_logs (user_id, log_date);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  message TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications (user_id, is_read, created_at DESC);

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
CREATE INDEX IF NOT EXISTS idx_profiles_health_metadata ON profiles USING GIN (health_metadata);
