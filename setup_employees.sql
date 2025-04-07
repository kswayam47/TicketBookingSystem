-- Create employees table if it doesn't exist
CREATE TABLE IF NOT EXISTS employees (
    EmployeeID INT AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(100) NOT NULL,
    Position VARCHAR(50) NOT NULL,
    ContactNumber VARCHAR(15),
    Email VARCHAR(100),
    JoinDate DATE,
    Status ENUM('Active', 'Inactive') DEFAULT 'Active'
);

-- Clear any existing entries to avoid duplicate test data
DELETE FROM employees;

-- Reset auto-increment
ALTER TABLE employees AUTO_INCREMENT = 1;

-- Insert sample employees
INSERT INTO employees (Name, Position, ContactNumber, Email, JoinDate, Status) VALUES
('Raj Kumar', 'Snack Counter Staff', '9876543210', 'raj@cinemax.com', '2023-01-15', 'Active'),
('Priya Singh', 'Snack Counter Staff', '9876543211', 'priya@cinemax.com', '2023-02-20', 'Active'),
('Anil Sharma', 'Snack Counter Manager', '9876543212', 'anil@cinemax.com', '2022-11-10', 'Active'),
('Meera Patel', 'Snack Counter Staff', '9876543213', 'meera@cinemax.com', '2023-03-05', 'Active'),
('Vikram Joshi', 'Snack Counter Staff', '9876543214', 'vikram@cinemax.com', '2023-04-12', 'Active');

-- Display all employees
SELECT * FROM employees; 