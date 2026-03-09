INSERT INTO food_items (name, meal_type, calories, protein_g, carbs_g, fat_g, sugar_g, sodium_mg, cholesterol_mg, vitamins)
VALUES
('Oatmeal', 'breakfast', 150, 5, 27, 3, 1, 2, 0, '{"vitamin_b1":"12%","folate":"6%"}'),
('Greek Yogurt', 'breakfast', 120, 17, 6, 4, 5, 55, 10, '{"vitamin_b12":"20%","riboflavin":"15%"}'),
('Boiled Egg', 'breakfast', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Grilled Chicken Salad', 'lunch', 360, 32, 18, 14, 4, 320, 70, '{"vitamin_a":"22%","vitamin_k":"28%"}'),
('Lentil Bowl', 'lunch', 420, 22, 55, 8, 4, 210, 0, '{"folate":"45%","iron":"30%"}'),
('Brown Rice and Salmon', 'dinner', 520, 33, 41, 19, 1, 290, 55, '{"vitamin_d":"50%","vitamin_b6":"30%"}'),
('Spinach Stir Fry', 'dinner', 250, 10, 18, 14, 2, 180, 0, '{"vitamin_a":"56%","vitamin_k":"180%","folate":"35%"}'),
('Apple Slices', 'snack', 95, 0.5, 25, 0.3, 19, 2, 0, '{"vitamin_c":"14%"}'),
('Almonds', 'snack', 164, 6, 6, 14, 1.2, 1, 0, '{"vitamin_e":"37%","magnesium":"20%"}'),
('Soda Drink', 'snack', 140, 0, 39, 0, 39, 45, 0, '{"vitamin_c":"0%"}')
ON CONFLICT DO NOTHING;
