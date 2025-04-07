-- Add more variety to the existing Indian snacks
INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES 
('Butter Popcorn', 150.00, 85),
('Caramel Popcorn', 180.00, 70),
('Cheese Popcorn', 190.00, 65),
('Vada Pav', 80.00, 40),
('Pav Bhaji', 120.00, 30),
('Pepsi (Small)', 60.00, 150),
('Pepsi (Medium)', 90.00, 100),
('Pepsi (Large)', 120.00, 80),
('Mineral Water', 40.00, 200),
('Chocolate Brownie', 100.00, 25),
('Ice Cream Sundae', 150.00, 20),
('Paneer Tikka', 180.00, 15),
('Dahi Puri', 100.00, 45),
('Pani Puri', 90.00, 50),
('Masala Chai', 60.00, 70),
('Combo 1 (Popcorn + Pepsi)', 190.00, 50),
('Combo 2 (Samosa + Chai)', 100.00, 40),
('Combo 3 (Vada Pav + Pepsi)', 130.00, 35);

-- Add some low-stock items for testing
INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES 
('Special Pav Bhaji', 150.00, 8),
('Premium Falooda', 200.00, 5),
('Chocolate Mousse', 180.00, 3);

-- Display all snacks
SELECT * FROM snackscounter; 