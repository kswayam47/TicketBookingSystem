# Movie Ticket Booking System

A web-based movie ticket booking system with user authentication, ticket booking, and snack ordering functionality.

## Features

- User authentication (login/signup)
- Movie listing and details
- Ticket booking with seat selection
- Snack ordering
- User profile management
- Responsive design

## Prerequisites

- Java 11 or higher
- MySQL Server
- Web browser

## Database Setup

1. Create a MySQL database named `ticket_booking`
2. Run the following SQL commands to create the required tables:

```sql
CREATE TABLE customer (
    CustomerID INT PRIMARY KEY AUTO_INCREMENT,
    Name VARCHAR(100) NOT NULL,
    Age INT NOT NULL,
    Gender VARCHAR(10) NOT NULL,
    Email VARCHAR(100) UNIQUE NOT NULL,
    Password VARCHAR(100) NOT NULL
);

CREATE TABLE movies (
    MovieID INT PRIMARY KEY AUTO_INCREMENT,
    Title VARCHAR(100) NOT NULL,
    Genre VARCHAR(50),
    Duration INT,
    ReleaseDate DATE
);

CREATE TABLE tickets (
    TicketID INT PRIMARY KEY AUTO_INCREMENT,
    ReservationID INT NOT NULL,
    RowNo INT NOT NULL,
    SeatNo INT NOT NULL,
    ScreenNo INT NOT NULL,
    Price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (ReservationID) REFERENCES reservations(ReservationID)
);

CREATE TABLE reservations (
    ReservationID INT PRIMARY KEY AUTO_INCREMENT,
    CustomerID INT NOT NULL,
    MovieID INT NOT NULL,
    DateTime DATETIME NOT NULL,
    Status VARCHAR(20) NOT NULL,
    FOREIGN KEY (CustomerID) REFERENCES customer(CustomerID),
    FOREIGN KEY (MovieID) REFERENCES movies(MovieID)
);

CREATE TABLE snacks (
    SnackID INT PRIMARY KEY AUTO_INCREMENT,
    ItemName VARCHAR(100) NOT NULL,
    Price DECIMAL(10,2) NOT NULL
);

CREATE TABLE snack_orders (
    OrderID INT PRIMARY KEY AUTO_INCREMENT,
    ReservationID INT NOT NULL,
    SnackID INT NOT NULL,
    Quantity INT NOT NULL,
    FOREIGN KEY (ReservationID) REFERENCES reservations(ReservationID),
    FOREIGN KEY (SnackID) REFERENCES snacks(SnackID)
);
```

## Configuration

1. Update the database connection settings in `SimpleServer.java`:
   - DB_URL
   - DB_USER
   - DB_PASSWORD

## Building and Running

1. Clone the repository
2. Navigate to the project directory
3. Compile the Java files:
   ```bash
   javac -cp ".:mysql-connector-java-8.0.33.jar" src/main/java/main/java/SimpleServer.java
   ```
4. Run the server:
   ```bash
   java -cp ".:mysql-connector-java-8.0.33.jar" main.java.SimpleServer
   ```
5. Open your browser and visit `http://localhost:8080`

## Project Structure

```
TicketBookingSystem/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── SimpleServer.java
│   │   └── webapp/
│   │       ├── index.html
│   │       ├── login.html
│   │       ├── signup.html
│   │       ├── script.js
│   │       ├── login.js
│   │       └── signup.js
└── README.md
```

## API Endpoints

- `POST /api/login` - User login
- `POST /api/signup` - User registration
- `GET /api/movies` - Get all movies
- `POST /api/book` - Book tickets
- `GET /api/snacks` - Get all snacks
- `POST /api/snacks/order` - Order snacks
- `POST /api/booking/confirm` - Confirm booking
- `POST /api/booking/cancel` - Cancel booking

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request 