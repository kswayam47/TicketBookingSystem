-- Add remaining low-stock items for testing
INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES 
('Premium Falooda', 200.00, 5),
('Chocolate Mousse', 180.00, 3);
 
-- Display all snacks
SELECT * FROM snackscounter; 