package main.java;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import util.DatabaseConnection;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class SimpleServer {
    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ticketbookingsystem";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Root@123";

    // Add these constants after the existing DB constants
    private static final String GET_SHOW_TIMINGS = "SELECT s.ShowID, s.ShowTime, s.ShowDate, s.ScreenNo, s.AvailableSeats, m.Title as MovieName " +
                                                 "FROM show_timings s JOIN movie m ON s.MovieID = m.MovieID " +
                                                 "WHERE s.MovieID = ?";
    private static final String GET_SEATS = "SELECT SeatNumber FROM reservations WHERE ShowID = ?";
    private static final String UPDATE_AVAILABLE_SEATS = "UPDATE show_timings SET AvailableSeats = AvailableSeats - ? WHERE ShowID = ?";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Serve static files
        server.createContext("/", new StaticFileHandler());
        
        // API endpoints
        server.createContext("/api/movies", new MoviesHandler());
        server.createContext("/api/book", new BookingHandler());
        server.createContext("/api/snacks", new SnacksHandler());
        server.createContext("/api/snacks/order", new SnackOrderHandler());
        server.createContext("/api/booking/confirm", new BookingConfirmHandler());
        server.createContext("/api/booking/cancel", new BookingCancelHandler());
        
        // Authentication endpoints
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/signup", new SignupHandler());
        
        // New endpoints
        server.createContext("/api/showtimings", new ShowTimingsHandler());
        server.createContext("/api/seats", new SeatsHandler());
        
        // Add this in the main method after other endpoint creation
        server.createContext("/api/admin/snacks", new SnackInventoryHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("Server started on port " + PORT);
        System.out.println("Open http://localhost:" + PORT + " in your browser");
    }
    
    static class StaticFileHandler implements HttpHandler {
        private final Map<String, String> mimeTypes = new HashMap<>();
        
        StaticFileHandler() {
            mimeTypes.put(".html", "text/html");
            mimeTypes.put(".css", "text/css");
            mimeTypes.put(".js", "application/javascript");
            mimeTypes.put(".json", "application/json");
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            path = path.equals("/") ? "/index.html" : path;
            
            final String finalPath = path;
            String contentType = mimeTypes.entrySet().stream()
                .filter(e -> finalPath.endsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("text/plain");
                
            byte[] response;
            try {
                response = Files.readAllBytes(Paths.get("src/main/webapp" + finalPath));
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, response.length);
            } catch (IOException e) {
                response = "404 Not Found".getBytes();
                exchange.sendResponseHeaders(404, response.length);
            }
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    static class MoviesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;
            
            try {
                System.out.println("Attempting to connect to database...");
                conn = DatabaseConnection.getConnection();
                System.out.println("Connected successfully!");
                
                // First get the most popular movieId based on ticket sales
                int trendingMovieId = 0;
                PreparedStatement trendingStmt = conn.prepareStatement(
                    "SELECT m.MovieID FROM movie m " +
                    "JOIN show_timings s ON m.MovieID = s.MovieID " +
                    "JOIN tickets t ON s.ShowID = t.ShowID " +
                    "GROUP BY m.MovieID " +
                    "ORDER BY COUNT(t.TicketID) DESC LIMIT 1"
                );
                ResultSet trendingRs = trendingStmt.executeQuery();
                if (trendingRs.next()) {
                    trendingMovieId = trendingRs.getInt("MovieID");
                }
                trendingRs.close();
                trendingStmt.close();
                
                stmt = conn.prepareStatement("SELECT * FROM movie");
                System.out.println("Executing query: SELECT * FROM movie");
                rs = stmt.executeQuery();
                System.out.println("Query executed successfully!");
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");
                boolean first = true;
                
                while (rs.next()) {
                    int movieId = rs.getInt("MovieID");
                    boolean isTrending = (movieId == trendingMovieId);
                    
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{")
                        .append("\"id\":").append(movieId).append(",")
                        .append("\"title\":\"").append(escapeJson(rs.getString("Title"))).append("\",")
                        .append("\"genre\":\"").append(escapeJson(rs.getString("Genre"))).append("\",")
                        .append("\"duration\":").append(rs.getInt("Duration")).append(",")
                        .append("\"releaseDate\":\"").append(rs.getString("ReleaseDate")).append("\",")
                        .append("\"trending\":").append(isTrending)
                        .append("}");
                    first = false;
                }
                jsonBuilder.append("]");
                
                byte[] response = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                
            } catch (Exception e) {
                System.err.println("Error in MoviesHandler: " + e.getMessage());
                e.printStackTrace();
                String error = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                byte[] response = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (stmt != null) stmt.close();
                    if (conn != null) conn.close();
                } catch (SQLException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                }
            }
        }
    }
    
    static class BookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            Connection conn = null;
            try {
                // Read request body
                StringBuilder requestBody = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        requestBody.append(line);
                    }
                }
                String json = requestBody.toString();
                System.out.println("Received JSON: " + json); // Debug log
                
                // Parse JSON manually to avoid dependency
                String name = SimpleServer.extractJsonValue(json, "name");
                int age = Integer.parseInt(SimpleServer.extractJsonValue(json, "age"));
                String gender = SimpleServer.extractJsonValue(json, "gender");
                int movieId = Integer.parseInt(SimpleServer.extractJsonValue(json, "movieId"));
                int numSeats = Integer.parseInt(SimpleServer.extractJsonValue(json, "seats"));
                
                // Parse showId from the JSON - this is the specific show timing the user selected
                int showId = Integer.parseInt(SimpleServer.extractJsonValue(json, "showId"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // First, check if there are enough seats available for this show
                PreparedStatement checkSeatsStmt = conn.prepareStatement(
                    "SELECT AvailableSeats FROM show_timings WHERE ShowID = ?"
                );
                checkSeatsStmt.setInt(1, showId);
                ResultSet seatsRs = checkSeatsStmt.executeQuery();
                
                if (!seatsRs.next()) {
                    throw new SQLException("Show timing not found");
                }
                
                int availableSeats = seatsRs.getInt("AvailableSeats");
                if (availableSeats < numSeats) {
                    throw new SQLException("Not enough seats available. Only " + availableSeats + " seats left.");
                }
                
                // First insert customer
                PreparedStatement customerStmt = conn.prepareStatement(
                    "INSERT INTO customer (Name, Age, Gender) VALUES (?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                );
                customerStmt.setString(1, name);
                customerStmt.setInt(2, age);
                customerStmt.setString(3, gender);
                customerStmt.executeUpdate();
                
                ResultSet rs = customerStmt.getGeneratedKeys();
                int customerId = 0;
                if (rs.next()) {
                    customerId = rs.getInt(1);
                }
                
                // Create reservation
                PreparedStatement reservationStmt = conn.prepareStatement(
                    "INSERT INTO reservation (DateTime, Mode, CustomerID, MovieID) VALUES (NOW(), 'Online', ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                );
                reservationStmt.setInt(1, customerId);
                reservationStmt.setInt(2, movieId);
                reservationStmt.executeUpdate();
                
                rs = reservationStmt.getGeneratedKeys();
                int reservationId = 0;
                if (rs.next()) {
                    reservationId = rs.getInt(1);
                }
                
                // Get movie details and show timing details
                PreparedStatement showDetailsStmt = conn.prepareStatement(
                    "SELECT m.Title, s.ScreenNo, s.ShowTime, s.ShowDate " +
                    "FROM show_timings s JOIN movie m ON s.MovieID = m.MovieID " +
                    "WHERE s.ShowID = ?"
                );
                showDetailsStmt.setInt(1, showId);
                ResultSet showDetailsRs = showDetailsStmt.executeQuery();
                
                String movieTitle = "";
                int screenNo = 0;
                String showTime = "";
                String showDate = "";
                
                if (showDetailsRs.next()) {
                    movieTitle = showDetailsRs.getString("Title");
                    screenNo = showDetailsRs.getInt("ScreenNo");
                    showTime = showDetailsRs.getString("ShowTime");
                    showDate = showDetailsRs.getString("ShowDate");
                } else {
                    throw new SQLException("Show details not found");
                }
                
                // Generate tickets
                StringBuilder ticketsJson = new StringBuilder();
                ticketsJson.append("[");
                boolean first = true;
                
                System.out.println("Generating " + numSeats + " tickets...");
                
                for (int i = 0; i < numSeats; i++) {
                    // Find an available seat by checking if it's already booked
                    boolean seatFound = false;
                    int rowNo = 0, seatNo = 0;
                    double price = 200.00; // Default price
                    
                    // Try to find an available seat
                    for (int row = 1; row <= 5 && !seatFound; row++) {
                        for (int seat = 1; seat <= 20 && !seatFound; seat++) {
                            // Check if this seat is already booked for this show
                            PreparedStatement checkSeatStmt = conn.prepareStatement(
                                "SELECT COUNT(*) FROM tickets " +
                                "WHERE RowNo = ? AND SeatNo = ? AND ScreenNo = ? AND ShowID = ?"
                            );
                            checkSeatStmt.setInt(1, row);
                            checkSeatStmt.setInt(2, seat);
                            checkSeatStmt.setInt(3, screenNo);
                            checkSeatStmt.setInt(4, showId);
                            ResultSet checkRs = checkSeatStmt.executeQuery();
                            
                            if (checkRs.next() && checkRs.getInt(1) == 0) {
                                // Seat is available
                                rowNo = row;
                                seatNo = seat;
                                seatFound = true;
                            }
                        }
                    }
                    
                    if (!seatFound) {
                        System.err.println("No available seats found!");
                        throw new SQLException("No available seats found");
                    }
                    
                    System.out.println("Found available seat: Row=" + rowNo + ", Seat=" + seatNo + ", Screen=" + screenNo);
                    
                    // Create a new ticket
                    PreparedStatement ticketStmt = conn.prepareStatement(
                        "INSERT INTO tickets (SeatNo, RowNo, ScreenNo, ReservationID, Price, ShowID) VALUES (?, ?, ?, ?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS
                    );
                    
                    ticketStmt.setInt(1, seatNo);
                    ticketStmt.setInt(2, rowNo);
                    ticketStmt.setInt(3, screenNo);
                    ticketStmt.setInt(4, reservationId);
                    ticketStmt.setDouble(5, price);
                    ticketStmt.setInt(6, showId);
                    ticketStmt.executeUpdate();
                    
                    // Get the ticket ID
                    ResultSet ticketRs = ticketStmt.getGeneratedKeys();
                    int ticketId = 0;
                    if (ticketRs.next()) {
                        ticketId = ticketRs.getInt(1);
                    }
                    
                    // Get the actual price
                    PreparedStatement priceStmt = conn.prepareStatement(
                        "SELECT price FROM tickets WHERE TicketID = ?"
                    );
                    priceStmt.setInt(1, ticketId);
                    ResultSet priceRs = priceStmt.executeQuery();
                    if (priceRs.next()) {
                        price = priceRs.getDouble("price");
                    }
                    
                    System.out.println("Ticket created: Row=" + rowNo + ", Seat=" + seatNo + ", Screen=" + screenNo + ", Price=" + price);
                    
                    if (!first) {
                        ticketsJson.append(",");
                    }
                    ticketsJson.append("{")
                        .append("\"rowNo\":").append(rowNo).append(",")
                        .append("\"seatNo\":").append(seatNo).append(",")
                        .append("\"screenNo\":").append(screenNo).append(",")
                        .append("\"price\":").append(price)
                        .append("}");
                    first = false;
                }
                ticketsJson.append("]");
                
                // Update available seats in the show_timings table
                PreparedStatement updateSeatsStmt = conn.prepareStatement(
                    "UPDATE show_timings SET AvailableSeats = AvailableSeats - ? WHERE ShowID = ?"
                );
                updateSeatsStmt.setInt(1, numSeats);
                updateSeatsStmt.setInt(2, showId);
                updateSeatsStmt.executeUpdate();
                
                System.out.println("Tickets JSON: " + ticketsJson.toString());
                System.out.println("Updated available seats for ShowID " + showId + ": reduced by " + numSeats);
                
                conn.commit();
                
                // Build success response with ticket details
                String successResponse = String.format(
                    "{\"success\":true,\"message\":\"Booking completed successfully!\",\"reservationId\":%d,\"ticket\":{\"reservationId\":%d,\"movieTitle\":\"%s\",\"tickets\":%s,\"dateTime\":\"%s\",\"showTime\":\"%s\",\"showDate\":\"%s\",\"screenNo\":%d}}",
                    reservationId,
                    reservationId,
                    SimpleServer.escapeJson(movieTitle),
                    ticketsJson.toString(),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    showTime,
                    showDate,
                    screenNo
                );
                
                System.out.println("Final response: " + successResponse);
                
                SimpleServer.sendJsonResponse(exchange, successResponse, 200);
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                SimpleServer.sendJsonResponse(exchange, "{\"error\": \"" + SimpleServer.escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    static class SnacksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            try {
                Connection conn = DatabaseConnection.getConnection();
                
                // First identify the top 3 most ordered snacks
                Set<Integer> trendingSnackIds = new HashSet<>();
                PreparedStatement trendingStmt = conn.prepareStatement(
                    "SELECT s.SnackID FROM snackscounter s " +
                    "JOIN snackorders so ON s.SnackID = so.SnackID " +
                    "GROUP BY s.SnackID " +
                    "ORDER BY SUM(so.Quantity) DESC LIMIT 3"
                );
                ResultSet trendingRs = trendingStmt.executeQuery();
                while (trendingRs.next()) {
                    trendingSnackIds.add(trendingRs.getInt("SnackID"));
                }
                trendingRs.close();
                trendingStmt.close();
                
                // Then get all available snacks
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM snackscounter WHERE Quantity > 0");
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");
                boolean first = true;
                
                while (rs.next()) {
                    int snackId = rs.getInt("SnackID");
                    int quantity = rs.getInt("Quantity");
                    boolean lowStock = quantity < 10; // Flag items with less than 10 in stock as low
                    boolean isTrending = trendingSnackIds.contains(snackId);
                    
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{")
                        .append("\"id\":").append(snackId).append(",")
                        .append("\"itemName\":\"").append(SimpleServer.escapeJson(rs.getString("ItemName"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("Price")).append(",")
                        .append("\"quantity\":").append(quantity).append(",")
                        .append("\"lowStock\":").append(lowStock).append(",")
                        .append("\"trending\":").append(isTrending)
                        .append("}");
                    first = false;
                }
                jsonBuilder.append("]");
                
                SimpleServer.sendJsonResponse(exchange, jsonBuilder.toString(), 200);
                
                rs.close();
                stmt.close();
                conn.close();
                
            } catch (Exception e) {
                e.printStackTrace();
                SimpleServer.sendJsonResponse(exchange, "{\"error\": \"" + SimpleServer.escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
    }
    
    static class SnackOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            Connection conn = null;
            try {
                String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int reservationId = Integer.parseInt(SimpleServer.extractJsonValue(json, "reservationId"));
                String ordersJson = json.substring(json.indexOf("\"orders\":[") + 9, json.lastIndexOf("]"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // Get any available employee for snack service
                System.out.println("Looking for available employee...");
                PreparedStatement empStmt = conn.prepareStatement(
                    "SELECT EmployeeID, Name FROM employees ORDER BY RAND() LIMIT 1"
                );
                ResultSet empRs = empStmt.executeQuery();
                System.out.println("Employee query executed.");
                if (!empRs.next()) {
                    System.out.println("No employees found in database.");
                    throw new SQLException("No employee available");
                }
                int employeeId = empRs.getInt("EmployeeID");
                String employeeName = empRs.getString("Name");
                System.out.println("Found employee ID: " + employeeId + ", Name: " + employeeName);
                
                // Process each snack order
                StringBuilder orderSummary = new StringBuilder();
                orderSummary.append("[");
                boolean firstOrder = true;

                String[] orders = ordersJson.split("\\},\\{");
                for (String order : orders) {
                    int snackId = Integer.parseInt(SimpleServer.extractJsonValue(order, "snackId"));
                    int quantity = Integer.parseInt(SimpleServer.extractJsonValue(order, "quantity"));
                    
                    // Check stock availability
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT ItemName, Quantity, Price FROM snackscounter WHERE SnackID = ?"
                    );
                    checkStmt.setInt(1, snackId);
                    ResultSet checkRs = checkStmt.executeQuery();
                    
                    if (!checkRs.next() || checkRs.getInt("Quantity") < quantity) {
                        throw new SQLException("Insufficient stock for snack ID: " + snackId + 
                                              " (Requested: " + quantity + ", Available: " + 
                                              (checkRs.next() ? checkRs.getInt("Quantity") : 0) + ")");
                    }
                    
                    String itemName = checkRs.getString("ItemName");
                    double price = checkRs.getDouble("Price");
                    int availableQuantity = checkRs.getInt("Quantity");
                    int remainingQuantity = availableQuantity - quantity;
                    
                    // Update stock
                    PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE snackscounter SET Quantity = Quantity - ? WHERE SnackID = ?"
                    );
                    updateStmt.setInt(1, quantity);
                    updateStmt.setInt(2, snackId);
                    updateStmt.executeUpdate();
                    
                    // Create order
                    PreparedStatement orderStmt = conn.prepareStatement(
                        "INSERT INTO snackorders (ReservationID, SnackID, Quantity, EmployeeID) VALUES (?, ?, ?, ?)"
                    );
                    orderStmt.setInt(1, reservationId);
                    orderStmt.setInt(2, snackId);
                    orderStmt.setInt(3, quantity);
                    orderStmt.setInt(4, employeeId);
                    orderStmt.executeUpdate();
                    
                    // Add order details to response
                    if (!firstOrder) {
                        orderSummary.append(",");
                    }
                    orderSummary.append("{")
                        .append("\"snackId\":").append(snackId).append(",")
                        .append("\"itemName\":\"").append(SimpleServer.escapeJson(itemName)).append("\",")
                        .append("\"quantity\":").append(quantity).append(",")
                        .append("\"price\":").append(price).append(",")
                        .append("\"total\":").append(price * quantity).append(",")
                        .append("\"remainingStock\":").append(remainingQuantity).append(",")
                        .append("\"lowStock\":").append(remainingQuantity < 10)
                        .append("}");
                    firstOrder = false;
                }
                orderSummary.append("]");

                conn.commit();
                SimpleServer.sendJsonResponse(exchange, 
                    "{\"success\": true, \"message\": \"Snacks ordered successfully\", \"orders\": " + 
                    orderSummary.toString() + ", \"employeeName\": \"" + SimpleServer.escapeJson(employeeName) + "\"}", 200);
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                SimpleServer.sendJsonResponse(exchange, "{\"error\": \"" + SimpleServer.escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    static class BookingConfirmHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            Connection conn = null;
            try {
                String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int reservationId = Integer.parseInt(SimpleServer.extractJsonValue(json, "reservationId"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // Confirm the booking by marking it as confirmed in the database
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE reservation SET Status = 'Confirmed' WHERE ReservationID = ?"
                );
                stmt.setInt(1, reservationId);
                stmt.executeUpdate();
                
                // Get complete ticket information
                PreparedStatement ticketStmt = conn.prepareStatement(
                    "SELECT DISTINCT t.*, m.Title as MovieTitle, st.ShowTime, st.ShowDate, st.ScreenNo, " +
                    "e.Name as EmployeeName " +
                    "FROM tickets t " +
                    "JOIN show_timings st ON t.ShowID = st.ShowID " +
                    "JOIN movie m ON st.MovieID = m.MovieID " +
                    "LEFT JOIN (SELECT DISTINCT ReservationID, EmployeeID FROM snackorders) so ON t.ReservationID = so.ReservationID " +
                    "LEFT JOIN employees e ON so.EmployeeID = e.EmployeeID " +
                    "WHERE t.ReservationID = ?"
                );
                ticketStmt.setInt(1, reservationId);
                ResultSet rs = ticketStmt.executeQuery();
                
                StringBuilder ticketsJson = new StringBuilder();
                ticketsJson.append("[");
                boolean first = true;
                String movieTitle = "";
                String showTime = "";
                String showDate = "";
                int screenNo = 0;
                String employeeName = null;
                
                while (rs.next()) {
                    if (!first) {
                        ticketsJson.append(",");
                    }
                    movieTitle = rs.getString("MovieTitle");
                    showTime = rs.getString("ShowTime");
                    showDate = rs.getString("ShowDate");
                    screenNo = rs.getInt("ScreenNo");
                    if (employeeName == null) {
                        employeeName = rs.getString("EmployeeName");
                    }
                    
                    ticketsJson.append("{")
                        .append("\"rowNo\":").append(rs.getInt("RowNo")).append(",")
                        .append("\"seatNo\":").append(rs.getInt("SeatNo")).append(",")
                        .append("\"screenNo\":").append(rs.getInt("ScreenNo")).append(",")
                        .append("\"price\":").append(rs.getDouble("Price"))
                        .append("}");
                    first = false;
                }
                ticketsJson.append("]");
                
                // Get snack orders if any
                PreparedStatement snackStmt = conn.prepareStatement(
                    "SELECT s.ItemName, so.Quantity, s.Price " +
                    "FROM snackorders so " +
                    "JOIN snackscounter s ON so.SnackID = s.SnackID " +
                    "WHERE so.ReservationID = ?"
                );
                snackStmt.setInt(1, reservationId);
                ResultSet snackRs = snackStmt.executeQuery();
                
                StringBuilder snacksJson = new StringBuilder();
                snacksJson.append("[");
                first = true;
                
                while (snackRs.next()) {
                    if (!first) {
                        snacksJson.append(",");
                    }
                    snacksJson.append("{")
                        .append("\"itemName\":\"").append(escapeJson(snackRs.getString("ItemName"))).append("\",")
                        .append("\"quantity\":").append(snackRs.getInt("Quantity")).append(",")
                        .append("\"price\":").append(snackRs.getDouble("Price"))
                        .append("}");
                    first = false;
                }
                snacksJson.append("]");
                
                conn.commit();
                
                String response = String.format(
                    "{\"success\":true," +
                    "\"message\":\"Booking confirmed successfully\"," +
                    "\"ticket\":{" +
                    "\"reservationId\":%d," +
                    "\"movieTitle\":\"%s\"," +
                    "\"showTime\":\"%s\"," +
                    "\"showDate\":\"%s\"," +
                    "\"screenNo\":%d," +
                    "\"status\":\"Confirmed\"," +
                    "\"tickets\":%s," +
                    "\"snacks\":%s," +
                    "\"employeeName\":\"%s\"" +
                    "}}",
                    reservationId,
                    escapeJson(movieTitle),
                    escapeJson(showTime),
                    escapeJson(showDate),
                    screenNo,
                    ticketsJson.toString(),
                    snacksJson.toString(),
                    employeeName != null ? escapeJson(employeeName) : ""
                );
                
                SimpleServer.sendJsonResponse(exchange, response, 200);
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                SimpleServer.sendJsonResponse(exchange, "{\"error\": \"" + SimpleServer.escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    static class BookingCancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            Connection conn = null;
            try {
                String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int reservationId = Integer.parseInt(SimpleServer.extractJsonValue(json, "reservationId"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // Get reservation details to find the show timing
                PreparedStatement reservationStmt = conn.prepareStatement(
                    "SELECT COUNT(t.TicketID) as TicketCount, t.ShowID " +
                    "FROM tickets t " +
                    "WHERE t.ReservationID = ? " +
                    "GROUP BY t.ShowID"
                );
                reservationStmt.setInt(1, reservationId);
                ResultSet reservationRs = reservationStmt.executeQuery();
                
                if (reservationRs.next()) {
                    int ticketCount = reservationRs.getInt("TicketCount");
                    int showId = reservationRs.getInt("ShowID");
                    
                    // Update available seats in the show_timings table
                    PreparedStatement updateSeatsStmt = conn.prepareStatement(
                        "UPDATE show_timings SET AvailableSeats = AvailableSeats + ? " +
                        "WHERE ShowID = ?"
                    );
                    updateSeatsStmt.setInt(1, ticketCount);
                    updateSeatsStmt.setInt(2, showId);
                    int updatedRows = updateSeatsStmt.executeUpdate();
                    
                    System.out.println("Updated show_timings with ID " + showId + ": " +
                                      "restored " + ticketCount + " seats, affected " + updatedRows + " rows");
                } else {
                    System.out.println("Warning: No tickets found for reservation ID " + reservationId);
                }
                
                // First, restore snack quantities
                PreparedStatement snackStmt = conn.prepareStatement(
                    "UPDATE snackscounter sc " +
                    "INNER JOIN snackorders so ON sc.SnackID = so.SnackID " +
                    "SET sc.Quantity = sc.Quantity + so.Quantity " +
                    "WHERE so.ReservationID = ?"
                );
                snackStmt.setInt(1, reservationId);
                snackStmt.executeUpdate();
                
                // Delete snack orders
                PreparedStatement deleteSnackStmt = conn.prepareStatement(
                    "DELETE FROM snackorders WHERE ReservationID = ?"
                );
                deleteSnackStmt.setInt(1, reservationId);
                deleteSnackStmt.executeUpdate();
                
                // Delete tickets
                PreparedStatement deleteTicketStmt = conn.prepareStatement(
                    "DELETE FROM tickets WHERE ReservationID = ?"
                );
                deleteTicketStmt.setInt(1, reservationId);
                deleteTicketStmt.executeUpdate();
                
                // Delete reservation
                PreparedStatement deleteReservationStmt = conn.prepareStatement(
                    "DELETE FROM reservation WHERE ReservationID = ?"
                );
                deleteReservationStmt.setInt(1, reservationId);
                deleteReservationStmt.executeUpdate();
                
                conn.commit();
                SimpleServer.sendJsonResponse(exchange, "{\"success\": true, \"message\": \"Booking cancelled successfully\"}", 200);
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                SimpleServer.sendJsonResponse(exchange, "{\"error\": \"" + SimpleServer.escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "Method not allowed", 405);
                return;
            }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                
                // Parse JSON manually
                String email = extractJsonValue(requestBody, "email");
                String password = extractJsonValue(requestBody, "password");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT * FROM customer WHERE Email = ? AND Password = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, email);
                    stmt.setString(2, password);
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        // Create JSON response manually
                        String response = "{\"success\":true,\"user\":{" +
                            "\"id\":" + rs.getInt("CustomerID") + "," +
                            "\"name\":\"" + escapeJson(rs.getString("Name")) + "\"," +
                            "\"age\":" + rs.getInt("Age") + "," +
                            "\"gender\":\"" + escapeJson(rs.getString("Gender")) + "\"," +
                            "\"email\":\"" + escapeJson(rs.getString("Email")) + "\"" +
                            "}}";
                        sendJsonResponse(exchange, response);
                    } else {
                        String response = "{\"success\":false,\"error\":\"Invalid email or password\"}";
                        sendJsonResponse(exchange, response, 401);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"success\":false,\"error\":\"Error during login: " + escapeJson(e.getMessage()) + "\"}";
                sendJsonResponse(exchange, response, 500);
            }
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, "Method not allowed", 405);
                return;
            }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                
                // Parse JSON manually
                String name = extractJsonValue(requestBody, "name");
                String email = extractJsonValue(requestBody, "email");
                String password = extractJsonValue(requestBody, "password");
                int age = Integer.parseInt(extractJsonValue(requestBody, "age"));
                String gender = extractJsonValue(requestBody, "gender");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    // Check if email already exists
                    String checkSql = "SELECT COUNT(*) FROM customer WHERE Email = ?";
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    checkStmt.setString(1, email);
                    ResultSet checkRs = checkStmt.executeQuery();
                    checkRs.next();
                    
                    if (checkRs.getInt(1) > 0) {
                        String response = "{\"success\":false,\"error\":\"Email already exists\"}";
                        sendJsonResponse(exchange, response, 400);
                        return;
                    }
                    
                    // Insert new user
                    String insertSql = "INSERT INTO customer (Name, Age, Gender, Email, Password) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement insertStmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
                    insertStmt.setString(1, name);
                    insertStmt.setInt(2, age);
                    insertStmt.setString(3, gender);
                    insertStmt.setString(4, email);
                    insertStmt.setString(5, password);
                    
                    int affectedRows = insertStmt.executeUpdate();
                    
                    if (affectedRows > 0) {
                        ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int userId = generatedKeys.getInt(1);
                            
                            // Create JSON response manually
                            String response = "{\"success\":true,\"user\":{" +
                                "\"id\":" + userId + "," +
                                "\"name\":\"" + escapeJson(name) + "\"," +
                                "\"age\":" + age + "," +
                                "\"gender\":\"" + escapeJson(gender) + "\"," +
                                "\"email\":\"" + escapeJson(email) + "\"" +
                                "}}";
                            sendJsonResponse(exchange, response);
                        } else {
                            String response = "{\"success\":false,\"error\":\"Failed to create user\"}";
                            sendJsonResponse(exchange, response, 500);
                        }
                    } else {
                        String response = "{\"success\":false,\"error\":\"Failed to create user\"}";
                        sendJsonResponse(exchange, response, 500);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"success\":false,\"error\":\"Error during signup: " + escapeJson(e.getMessage()) + "\"}";
                sendJsonResponse(exchange, response, 500);
            }
        }
    }
    
    static class ShowTimingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendJsonResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("movieId=")) {
                sendJsonResponse(exchange, "{\"error\": \"Invalid request\"}", 400);
                return;
            }

            int movieId;
            try {
                movieId = Integer.parseInt(query.substring(8));
                System.out.println("Fetching show timings for movie ID: " + movieId);
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, "{\"error\": \"Invalid movie ID\"}", 400);
                return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(GET_SHOW_TIMINGS)) {
                
                stmt.setInt(1, movieId);
                System.out.println("Executing query: " + GET_SHOW_TIMINGS + " with movieId=" + movieId);
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\"showTimings\": [");
                boolean first = true;
                
                while (rs.next()) {
                    int showId = rs.getInt("ShowID");
                    String showTime = rs.getString("ShowTime");
                    String showDate = rs.getString("ShowDate");
                    int screenNo = rs.getInt("ScreenNo");
                    int availableSeats = rs.getInt("AvailableSeats");
                    String movieName = rs.getString("MovieName");
                    
                    System.out.println("Found show: ID=" + showId + ", Movie=" + movieName + 
                                     ", Time=" + showTime + ", Date=" + showDate + 
                                     ", Screen=" + screenNo + ", Available Seats=" + availableSeats);
                    
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{")
                              .append("\"showId\":").append(showId).append(",")
                              .append("\"showTime\":\"").append(showTime).append("\",")
                              .append("\"showDate\":\"").append(showDate).append("\",")
                              .append("\"screenNo\":").append(screenNo).append(",")
                              .append("\"availableSeats\":").append(availableSeats).append(",")
                              .append("\"movieName\":\"").append(escapeJsonString(movieName)).append("\"")
                              .append("}");
                    first = false;
                }
                jsonBuilder.append("]}");
                
                String response = jsonBuilder.toString();
                System.out.println("Sending response: " + response);
                sendJsonResponse(exchange, response, 200);
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("Database error in ShowTimingsHandler: " + e.getMessage());
                sendJsonResponse(exchange, "{\"error\": \"Database error: " + escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
    }

    static class SeatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendJsonResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("showId=")) {
                sendJsonResponse(exchange, "{\"error\": \"Invalid request\"}", 400);
                return;
            }

            int showId;
            try {
                showId = Integer.parseInt(query.substring(7));
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, "{\"error\": \"Invalid show ID\"}", 400);
                return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(GET_SEATS)) {
                
                stmt.setInt(1, showId);
                ResultSet rs = stmt.executeQuery();
                
                Set<Integer> bookedSeats = new HashSet<>();
                while (rs.next()) {
                    bookedSeats.add(rs.getInt("SeatNumber"));
                }
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\"seats\": {");
                
                // Generate 8 rows with 15 seats each
                for (int row = 1; row <= 8; row++) {
                    if (row > 1) jsonBuilder.append(",");
                    jsonBuilder.append("\"row").append(row).append("\": [");
                    
                    for (int seat = 1; seat <= 15; seat++) {
                        if (seat > 1) jsonBuilder.append(",");
                        int seatNumber = (row - 1) * 15 + seat;
                        jsonBuilder.append("{")
                                  .append("\"number\":").append(seatNumber).append(",")
                                  .append("\"isBooked\":").append(bookedSeats.contains(seatNumber))
                                  .append("}");
                    }
                    jsonBuilder.append("]");
                }
                jsonBuilder.append("}}");
                
                sendJsonResponse(exchange, jsonBuilder.toString(), 200);
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, "{\"error\": \"Database error\"}", 500);
            }
        }
    }
    
    // Utility methods for JSON handling
    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    private static String extractJsonValue(String json, String key) {
        System.out.println("Extracting key: " + key + " from JSON: " + json); // Debug log
        
        String keyPattern = "\"" + key + "\"\\s*:\\s*";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) {
            // Try without spaces
            keyPattern = "\"" + key + "\":";
            keyIndex = json.indexOf(keyPattern);
            if (keyIndex == -1) {
                throw new IllegalArgumentException("Key not found: " + key);
            }
        }
        
        int valueStart = json.indexOf(':', keyIndex) + 1;
        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) {
            throw new IllegalArgumentException("Invalid JSON: unexpected end of input");
        }
        
        // Handle string values
        if (json.charAt(valueStart) == '"') {
            valueStart++; // Skip opening quote
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            
            for (int i = valueStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    value.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return value.toString();
                } else {
                    value.append(c);
                }
            }
            throw new IllegalArgumentException("Invalid JSON: unterminated string");
        }
        
        // Handle numeric values and booleans
        int valueEnd = valueStart;
        while (valueEnd < json.length() && !isJsonDelimiter(json.charAt(valueEnd))) {
            valueEnd++;
        }
        
        String value = json.substring(valueStart, valueEnd).trim();
        System.out.println("Extracted value: " + value); // Debug log
        return value;
    }

    private static boolean isJsonDelimiter(char c) {
        return c == ',' || c == '}' || c == ']' || Character.isWhitespace(c);
    }

    private static void sendJsonResponse(HttpExchange exchange, String response) throws IOException {
        sendJsonResponse(exchange, response, 200);
    }

    private static void sendJsonResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void sendResponse(HttpExchange exchange, String message, int statusCode) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        sendJsonResponse(exchange, response, statusCode);
    }

    // Add this utility method before the main method
    private static String escapeJsonString(String input) {
        if (input == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // Then add this class near other handlers
    static class SnackInventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Check request method
            String method = exchange.getRequestMethod();
            
            if ("OPTIONS".equals(method)) {
                handleCors(exchange);
                return;
            }
            
            if ("GET".equals(method)) {
                handleGetSnacks(exchange);
            } else if ("POST".equals(method)) {
                handleUpdateSnack(exchange);
            } else if ("PUT".equals(method)) {
                handleAddSnack(exchange);
            } else {
                sendJsonResponse(exchange, "{\"error\": \"Method not allowed\"}", 405);
            }
        }
        
        private void handleGetSnacks(HttpExchange exchange) throws IOException {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM snackscounter")) {
                
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");
                boolean first = true;
                
                while (rs.next()) {
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{")
                        .append("\"id\":").append(rs.getInt("SnackID")).append(",")
                        .append("\"itemName\":\"").append(escapeJson(rs.getString("ItemName"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("Price")).append(",")
                        .append("\"quantity\":").append(rs.getInt("Quantity"))
                        .append("}");
                    first = false;
                }
                jsonBuilder.append("]");
                
                sendJsonResponse(exchange, jsonBuilder.toString(), 200);
                
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
        
        private void handleUpdateSnack(HttpExchange exchange) throws IOException {
            Connection conn = null;
            try {
                // Read request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int snackId = Integer.parseInt(extractJsonValue(requestBody, "id"));
                String itemName = extractJsonValue(requestBody, "itemName");
                double price = Double.parseDouble(extractJsonValue(requestBody, "price"));
                int quantity = Integer.parseInt(extractJsonValue(requestBody, "quantity"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // Update the snack
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE snackscounter SET ItemName = ?, Price = ?, Quantity = ? WHERE SnackID = ?"
                );
                stmt.setString(1, itemName);
                stmt.setDouble(2, price);
                stmt.setInt(3, quantity);
                stmt.setInt(4, snackId);
                
                int updated = stmt.executeUpdate();
                
                if (updated > 0) {
                    conn.commit();
                    sendJsonResponse(exchange, "{\"success\": true, \"message\": \"Snack updated successfully\"}", 200);
                } else {
                    conn.rollback();
                    sendJsonResponse(exchange, "{\"error\": \"No snack found with ID " + snackId + "\"}", 404);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                sendJsonResponse(exchange, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        private void handleAddSnack(HttpExchange exchange) throws IOException {
            Connection conn = null;
            try {
                // Read request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String itemName = extractJsonValue(requestBody, "itemName");
                double price = Double.parseDouble(extractJsonValue(requestBody, "price"));
                int quantity = Integer.parseInt(extractJsonValue(requestBody, "quantity"));
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
                // Insert the new snack
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO snackscounter (ItemName, Price, Quantity) VALUES (?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                );
                stmt.setString(1, itemName);
                stmt.setDouble(2, price);
                stmt.setInt(3, quantity);
                
                int added = stmt.executeUpdate();
                
                if (added > 0) {
                    ResultSet rs = stmt.getGeneratedKeys();
                    int newSnackId = -1;
                    if (rs.next()) {
                        newSnackId = rs.getInt(1);
                    }
                    
                    conn.commit();
                    sendJsonResponse(exchange, 
                        "{\"success\": true, \"message\": \"Snack added successfully\", \"id\": " + newSnackId + "}", 
                        201);
                } else {
                    conn.rollback();
                    sendJsonResponse(exchange, "{\"error\": \"Failed to add snack\"}", 500);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
                sendJsonResponse(exchange, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}", 500);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
