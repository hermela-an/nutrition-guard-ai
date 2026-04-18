-- Move all NutritionGuard tables from schema `ng` to the default `public` schema.
-- Safe to run multiple times; never deletes rows.
--
-- This migration is intentionally numbered `000_` so it runs before `001_init.sql` and
-- prevents accidentally creating empty duplicates in `public` when data already exists in `ng`.

DO $$
DECLARE
  r RECORD;
BEGIN
  -- Nothing to do if `ng` schema doesn't exist.
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'ng') THEN
    RAISE NOTICE 'Schema "ng" not found. Skipping migration 000_move_ng_schema_to_public.';
    RETURN;
  END IF;

  -- Ensure public schema exists (it should in almost all Postgres installs).
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'public') THEN
    EXECUTE 'CREATE SCHEMA public';
  END IF;

  -- Guard against name collisions in `public` for tables we intend to move.
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'ng'
      AND c.relkind IN ('r', 'p', 'f') -- tables: ordinary, partitioned, foreign
  LOOP
    IF EXISTS (
      SELECT 1
      FROM pg_class c2
      JOIN pg_namespace n2 ON n2.oid = c2.relnamespace
      WHERE n2.nspname = 'public'
        AND c2.relname = r.relname
        AND c2.relkind IN ('r', 'p', 'f')
    ) THEN
      RAISE EXCEPTION 'Cannot move ng.% to public: a table with the same name already exists in public.', r.relname;
    END IF;
  END LOOP;

  -- Move tables first (indexes/constraints move with the table).
  FOR r IN
    SELECT c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'ng'
      AND c.relkind IN ('r', 'p', 'f')
    ORDER BY c.relname
  LOOP
    EXECUTE format('ALTER TABLE ng.%I SET SCHEMA public', r.relname);
  END LOOP;

  -- Move sequences (e.g., SERIAL/BIGSERIAL sequences do not automatically move with tables).
  FOR r IN
    SELECT c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'ng'
      AND c.relkind = 'S'
    ORDER BY c.relname
  LOOP
    IF EXISTS (
      SELECT 1
      FROM pg_class c2
      JOIN pg_namespace n2 ON n2.oid = c2.relnamespace
      WHERE n2.nspname = 'public'
        AND c2.relname = r.relname
        AND c2.relkind = 'S'
    ) THEN
      RAISE EXCEPTION 'Cannot move sequence ng.% to public: a sequence with the same name already exists in public.', r.relname;
    END IF;
    EXECUTE format('ALTER SEQUENCE ng.%I SET SCHEMA public', r.relname);
  END LOOP;

  -- Drop the schema if it is now empty (optional cleanup).
  IF NOT EXISTS (
    SELECT 1
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'ng'
  ) THEN
    EXECUTE 'DROP SCHEMA ng';
  END IF;
END
$$;

