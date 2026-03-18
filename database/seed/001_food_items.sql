INSERT INTO food_items (food_name, meal_type, portion_amount, portion_unit, calories, protein_g, carbs_g, fat_g, sugar_g, sodium_mg, cholesterol_mg, vitamins)
VALUES
('Oatmeal', 'breakfast', 150, 'g', 150, 5, 27, 3, 1, 2, 0, '{"vitamin_b1":"12%","folate":"6%"}'),
('Yogurt', 'breakfast', 150, 'g', 120, 17, 6, 4, 5, 55, 10, '{"vitamin_b12":"20%","riboflavin":"15%"}'),
('Egg', 'breakfast', 1, 'pcs', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Chicken Salad', 'lunch', 300, 'g', 360, 32, 18, 14, 4, 320, 70, '{"vitamin_a":"22%","vitamin_k":"28%"}'),
('Lentil Bowl', 'lunch', 350, 'g', 420, 22, 55, 8, 4, 210, 0, '{"folate":"45%","iron":"30%"}'),
('Rice and Salmon', 'dinner', 350, 'g', 520, 33, 41, 19, 1, 290, 55, '{"vitamin_d":"50%","vitamin_b6":"30%"}'),
('Spinach', 'dinner', 200, 'g', 250, 10, 18, 14, 2, 180, 0, '{"vitamin_a":"56%","vitamin_k":"180%","folate":"35%"}'),
('Apple', 'snack', 1, 'pcs', 95, 0.5, 25, 0.3, 19, 2, 0, '{"vitamin_c":"14%"}'),
('Almonds', 'snack', 30, 'g', 164, 6, 6, 14, 1.2, 1, 0, '{"vitamin_e":"37%","magnesium":"20%"}'),
('Soda', 'snack', 355, 'ml', 140, 0, 39, 0, 39, 45, 0, '{"vitamin_c":"0%"}'),

-- Ethiopian staples and common foods
('Injera', 'breakfast', 1, 'pcs', 180, 6, 36, 2, 1, 8, 0, '{"iron":"15%","calcium":"6%"}'),
('Injera', 'lunch', 1, 'pcs', 180, 6, 36, 2, 1, 8, 0, '{"iron":"15%","calcium":"6%"}'),
('Shiro', 'lunch', 300, 'g', 320, 16, 34, 12, 3, 480, 0, '{"folate":"30%","iron":"20%"}'),
('Misir Wat', 'lunch', 300, 'g', 280, 14, 36, 8, 3, 420, 0, '{"folate":"35%","iron":"25%"}'),
('Doro Wat', 'dinner', 300, 'g', 420, 30, 8, 28, 3, 620, 140, '{"vitamin_b6":"20%","vitamin_b12":"15%"}'),
('Tibs', 'dinner', 250, 'g', 450, 32, 6, 32, 0, 520, 110, '{"iron":"25%","vitamin_b12":"40%"}'),
('Gomen', 'dinner', 200, 'g', 160, 6, 12, 9, 2, 260, 0, '{"vitamin_a":"120%","vitamin_k":"200%"}'),
('Atkilt', 'lunch', 300, 'g', 220, 4, 34, 8, 6, 320, 0, '{"vitamin_c":"40%","vitamin_a":"30%"}'),
('Kik Alicha', 'lunch', 300, 'g', 300, 16, 38, 9, 3, 420, 0, '{"folate":"25%","iron":"15%"}'),
('Firfir', 'breakfast', 300, 'g', 260, 7, 40, 8, 3, 380, 0, '{"iron":"12%","vitamin_b1":"10%"}'),
('Ful', 'breakfast', 300, 'g', 280, 16, 36, 8, 2, 420, 0, '{"folate":"35%","iron":"20%"}'),
('Ayib', 'breakfast', 100, 'g', 120, 10, 4, 6, 2, 90, 20, '{"calcium":"20%","vitamin_b12":"10%"}'),
('Yogurt', 'breakfast', 150, 'g', 150, 8, 12, 7, 12, 75, 25, '{"calcium":"25%","vitamin_b12":"15%"}'),
('Egg', 'breakfast', 1, 'pcs', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Egg Scramble', 'breakfast', 200, 'g', 190, 12, 6, 12, 3, 220, 185, '{"vitamin_a":"15%","vitamin_c":"10%"}'),
('Fish', 'dinner', 200, 'g', 260, 28, 0, 14, 0, 220, 70, '{"vitamin_d":"40%","omega_3":"20%"}'),
('Kitfo', 'dinner', 250, 'g', 380, 28, 2, 28, 0, 380, 95, '{"iron":"20%","vitamin_b12":"35%"}'),
('Shiro and Gomen', 'lunch', 350, 'g', 360, 18, 34, 14, 4, 520, 0, '{"folate":"30%","vitamin_a":"60%"}'),
('Misir Wat and Injera', 'lunch', 350, 'g', 420, 18, 58, 12, 4, 520, 0, '{"folate":"35%","iron":"25%"}'),
('Doro Wat and Injera', 'dinner', 350, 'g', 520, 34, 40, 24, 4, 720, 160, '{"vitamin_b6":"20%","vitamin_b12":"15%"}'),

-- Common proteins
('Chicken', 'lunch', 200, 'g', 320, 35, 0, 16, 0, 180, 95, '{"niacin":"40%","vitamin_b6":"30%"}'),
('Egg', 'snack', 1, 'pcs', 78, 6, 1, 5, 0.6, 62, 186, '{"vitamin_d":"6%","vitamin_b12":"10%"}'),
('Chickpea Salad', 'lunch', 300, 'g', 300, 12, 40, 9, 6, 240, 0, '{"folate":"35%","iron":"15%"}'),
('Lentil Salad', 'lunch', 300, 'g', 280, 12, 36, 8, 4, 220, 0, '{"folate":"30%","iron":"20%"}'),
('Milk', 'breakfast', 1, 'cup', 150, 8, 12, 8, 12, 105, 24, '{"calcium":"25%","vitamin_b12":"15%"}'),

-- Common fruits
('Banana', 'snack', 1, 'pcs', 105, 1.3, 27, 0.4, 14, 1, 0, '{"vitamin_b6":"20%","potassium":"12%"}'),
('Avocado', 'snack', 1, 'pcs', 160, 2, 9, 15, 0.7, 7, 0, '{"vitamin_e":"10%","folate":"20%"}'),
('Mango', 'snack', 1, 'pcs', 99, 1.4, 25, 0.6, 23, 2, 0, '{"vitamin_c":"60%","vitamin_a":"20%"}'),
('Orange', 'snack', 1, 'pcs', 62, 1.2, 15, 0.2, 12, 0, 0, '{"vitamin_c":"90%"}'),
('Papaya', 'snack', 1, 'pcs', 62, 0.7, 15, 0.4, 10, 3, 0, '{"vitamin_c":"95%","vitamin_a":"30%"}'),
('Pineapple', 'snack', 1, 'pcs', 82, 0.9, 21, 0.2, 16, 2, 0, '{"vitamin_c":"80%"}'),

-- Common vegetables
('Cabbage', 'dinner', 200, 'g', 180, 4, 18, 9, 5, 240, 0, '{"vitamin_c":"50%","vitamin_k":"40%"}'),
('Carrots', 'lunch', 150, 'g', 120, 2, 18, 5, 6, 160, 0, '{"vitamin_a":"120%"}'),
('Potatoes', 'dinner', 300, 'g', 280, 6, 46, 6, 3, 320, 0, '{"vitamin_c":"30%","potassium":"20%"}'),
('Tomatoes', 'lunch', 150, 'g', 110, 2, 14, 5, 6, 140, 0, '{"vitamin_c":"25%","vitamin_a":"15%"}'),

-- Simple international foods commonly available
('Rice', 'lunch', 150, 'g', 205, 4, 45, 0.4, 0.1, 1, 0, '{"vitamin_b1":"8%"}'),
('Bread', 'breakfast', 1, 'pcs', 110, 5, 20, 1.5, 3, 180, 0, '{"vitamin_b1":"10%"}'),
('Chicken Salad', 'dinner', 300, 'g', 360, 32, 18, 14, 4, 320, 70, '{"vitamin_a":"22%","vitamin_k":"28%"}')
ON CONFLICT DO NOTHING;
