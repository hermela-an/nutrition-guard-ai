-- Adds role-based access support and admin-managed food metadata.
-- Safe to run multiple times.

-- Users: add role column
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';

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

-- Foods: add base traffic light
ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS traffic_light TEXT NOT NULL DEFAULT 'SAFE';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_traffic_light_valid'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_traffic_light_valid
      CHECK (traffic_light IN ('SAFE', 'CAUTION', 'AVOID'));
  END IF;
END
$$;
