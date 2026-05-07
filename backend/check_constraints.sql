SELECT n.nspname AS schema, t.relname AS table, c.conname AS constraint, pg_get_constraintdef(c.oid) AS definition 
FROM pg_constraint c 
JOIN pg_class t ON c.conrelid = t.oid 
JOIN pg_namespace n ON t.relnamespace = n.oid 
WHERE t.relname = 'food_items';
