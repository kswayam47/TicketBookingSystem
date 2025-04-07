-- Clear existing snacks if any
DELETE FROM snackorders;
DELETE FROM snackscounter;

-- Reset auto-increment
ALTER TABLE snackscounter AUTO_INCREMENT = 1;

-- Insert sample snacks with varied quantities
INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES 
('Popcorn (Small)', 120.00, 100),
('Popcorn (Medium)', 180.00, 80),
('Popcorn (Large)', 220.00, 60),
('Nachos with Cheese', 180.00, 45),
('Nachos with Salsa', 160.00, 40),
('Soft Drink (Small)', 80.00, 150),
('Soft Drink (Medium)', 120.00, 100),
('Soft Drink (Large)', 150.00, 75),
('Bottled Water', 60.00, 200),
('Candy', 90.00, 120),
('Chocolate Bar', 100.00, 80),
('Ice Cream', 130.00, 30),
('Hot Dog', 160.00, 25),
('Sandwich', 180.00, 20),
('Coffee', 110.00, 50),
('Combo Meal 1 (Popcorn + Drink)', 250.00, 40),
('Combo Meal 2 (Nachos + Drink)', 280.00, 35),
('Combo Meal 3 (Hot Dog + Drink)', 290.00, 15);

-- Add some low-stock items
INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES 
('Premium Chocolate', 180.00, 8),
('Gourmet Popcorn', 250.00, 5),
('Special Nachos Combo', 320.00, 3);

-- Display all snacks
SELECT * FROM snackscounter; 