SELECT tgname, tgenabled, pg_get_triggerdef(oid) 
FROM pg_trigger 
WHERE tgrelid = 'public.food_items'::regclass;
