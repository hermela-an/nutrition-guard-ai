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
('Soda Drink', 'snack', 140, 0, 39, 0, 39, 45, 0, '{"vitamin_c":"0%"}'),

-- Ethiopian staples and common foods
('Injera (teff)', 'breakfast', 180, 6, 36, 2, 1, 8, 0, '{"iron":"15%","calcium":"6%"}'),
('Injera (teff)', 'lunch', 180, 6, 36, 2, 1, 8, 0, '{"iron":"15%","calcium":"6%"}'),
('Shiro (chickpea stew)', 'lunch', 320, 16, 34, 12, 3, 480, 0, '{"folate":"30%","iron":"20%"}'),
('Misir Wat (lentil stew)', 'lunch', 280, 14, 36, 8, 3, 420, 0, '{"folate":"35%","iron":"25%"}'),
('Doro Wat (chicken stew)', 'dinner', 420, 30, 8, 28, 3, 620, 140, '{"vitamin_b6":"20%","vitamin_b12":"15%"}'),
('Tibs (sauteed beef)', 'dinner', 450, 32, 6, 32, 0, 520, 110, '{"iron":"25%","vitamin_b12":"40%"}'),
('Gomen (collard greens)', 'dinner', 160, 6, 12, 9, 2, 260, 0, '{"vitamin_a":"120%","vitamin_k":"200%"}'),
('Atkilt (cabbage, potato, carrot)', 'lunch', 220, 4, 34, 8, 6, 320, 0, '{"vitamin_c":"40%","vitamin_a":"30%"}'),
('Kik Alicha (split pea stew)', 'lunch', 300, 16, 38, 9, 3, 420, 0, '{"folate":"25%","iron":"15%"}'),
('Firfir (injera with sauce)', 'breakfast', 260, 7, 40, 8, 3, 380, 0, '{"iron":"12%","vitamin_b1":"10%"}'),
('Ful (fava bean stew)', 'breakfast', 280, 16, 36, 8, 2, 420, 0, '{"folate":"35%","iron":"20%"}'),
('Ayib (fresh cheese)', 'breakfast', 120, 10, 4, 6, 2, 90, 20, '{"calcium":"20%","vitamin_b12":"10%"}'),
('Plain Yogurt', 'breakfast', 150, 8, 12, 7, 12, 75, 25, '{"calcium":"25%","vitamin_b12":"15%"}'),
('Boiled Egg', 'breakfast', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Egg and Tomato Scramble', 'breakfast', 190, 12, 6, 12, 3, 220, 185, '{"vitamin_a":"15%","vitamin_c":"10%"}'),
('Grilled Fish', 'dinner', 260, 28, 0, 14, 0, 220, 70, '{"vitamin_d":"40%","omega_3":"20%"}'),
('Kitfo (lean, cooked)', 'dinner', 380, 28, 2, 28, 0, 380, 95, '{"iron":"20%","vitamin_b12":"35%"}'),
('Shiro with Gomen', 'lunch', 360, 18, 34, 14, 4, 520, 0, '{"folate":"30%","vitamin_a":"60%"}'),
('Misir Wat with Injera', 'lunch', 420, 18, 58, 12, 4, 520, 0, '{"folate":"35%","iron":"25%"}'),
('Doro Wat with Injera', 'dinner', 520, 34, 40, 24, 4, 720, 160, '{"vitamin_b6":"20%","vitamin_b12":"15%"}'),

-- Common proteins
('Grilled Chicken', 'lunch', 320, 35, 0, 16, 0, 180, 95, '{"niacin":"40%","vitamin_b6":"30%"}'),
('Boiled Egg', 'snack', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Chickpea Salad', 'lunch', 300, 12, 40, 9, 6, 240, 0, '{"folate":"35%","iron":"15%"}'),
('Lentil Salad', 'lunch', 280, 12, 36, 8, 4, 220, 0, '{"folate":"30%","iron":"20%"}'),
('Milk (1 cup)', 'breakfast', 150, 8, 12, 8, 12, 105, 24, '{"calcium":"25%","vitamin_b12":"15%"}'),

-- Common fruits
('Banana', 'snack', 105, 1.3, 27, 0.4, 14, 1, 0, '{"vitamin_b6":"20%","potassium":"12%"}'),
('Avocado', 'snack', 160, 2, 9, 15, 0.7, 7, 0, '{"vitamin_e":"10%","folate":"20%"}'),
('Mango', 'snack', 99, 1.4, 25, 0.6, 23, 2, 0, '{"vitamin_c":"60%","vitamin_a":"20%"}'),
('Orange', 'snack', 62, 1.2, 15, 0.2, 12, 0, 0, '{"vitamin_c":"90%"}'),
('Papaya', 'snack', 62, 0.7, 15, 0.4, 10, 3, 0, '{"vitamin_c":"95%","vitamin_a":"30%"}'),
('Pineapple', 'snack', 82, 0.9, 21, 0.2, 16, 2, 0, '{"vitamin_c":"80%"}'),

-- Common vegetables
('Cabbage Stir Fry', 'dinner', 180, 4, 18, 9, 5, 240, 0, '{"vitamin_c":"50%","vitamin_k":"40%"}'),
('Carrot Salad', 'lunch', 120, 2, 18, 5, 6, 160, 0, '{"vitamin_a":"120%"}'),
('Potato Stew', 'dinner', 280, 6, 46, 6, 3, 320, 0, '{"vitamin_c":"30%","potassium":"20%"}'),
('Tomato Salad', 'lunch', 110, 2, 14, 5, 6, 140, 0, '{"vitamin_c":"25%","vitamin_a":"15%"}'),

-- Simple international foods commonly available
('Rice (cooked)', 'lunch', 205, 4, 45, 0.4, 0.1, 1, 0, '{"vitamin_b1":"8%"}'),
('Whole Wheat Bread', 'breakfast', 110, 5, 20, 1.5, 3, 180, 0, '{"vitamin_b1":"10%"}'),
('Grilled Chicken Salad', 'dinner', 360, 32, 18, 14, 4, 320, 70, '{"vitamin_a":"22%","vitamin_k":"28%"}')
ON CONFLICT DO NOTHING;
