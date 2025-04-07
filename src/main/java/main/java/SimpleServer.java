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

public class SimpleServer {
    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ticketbookingsystem";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Root@123";

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
                
                stmt = conn.prepareStatement("SELECT * FROM movie");
                System.out.println("Executing query: SELECT * FROM movie");
                rs = stmt.executeQuery();
                System.out.println("Query executed successfully!");
                
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");
                boolean first = true;
                
                while (rs.next()) {
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{")
                        .append("\"id\":").append(rs.getInt("MovieID")).append(",")
                        .append("\"title\":\"").append(escapeJson(rs.getString("Title"))).append("\",")
                        .append("\"genre\":\"").append(escapeJson(rs.getString("Genre"))).append("\",")
                        .append("\"duration\":").append(rs.getInt("Duration")).append(",")
                        .append("\"releaseDate\":\"").append(rs.getString("ReleaseDate")).append("\"")
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
                
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);
                
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
                
                // Get movie details
                PreparedStatement movieStmt = conn.prepareStatement(
                    "SELECT Title FROM movie WHERE MovieID = ?"
                );
                movieStmt.setInt(1, movieId);
                ResultSet movieRs = movieStmt.executeQuery();
                String movieTitle = "";
                if (movieRs.next()) {
                    movieTitle = movieRs.getString("Title");
                }
                
                // Generate tickets
                StringBuilder ticketsJson = new StringBuilder();
                ticketsJson.append("[");
                boolean first = true;
                
                System.out.println("Generating " + numSeats + " tickets...");
                
                for (int i = 0; i < numSeats; i++) {
                    // Find an available seat by checking if it's already booked
                    boolean seatFound = false;
                    int rowNo = 0, seatNo = 0, screenNo = 0;
                    double price = 200.00; // Default price
                    
                    // Try to find an available seat
                    for (int row = 1; row <= 5 && !seatFound; row++) {
                        for (int seat = 1; seat <= 20 && !seatFound; seat++) {
                            // Check if this seat is already booked
                            PreparedStatement checkSeatStmt = conn.prepareStatement(
                                "SELECT COUNT(*) FROM tickets WHERE RowNo = ? AND SeatNo = ? AND ScreenNo = ?"
                            );
                            checkSeatStmt.setInt(1, row);
                            checkSeatStmt.setInt(2, seat);
                            checkSeatStmt.setInt(3, 1); // Screen 1
                            ResultSet checkRs = checkSeatStmt.executeQuery();
                            
                            if (checkRs.next() && checkRs.getInt(1) == 0) {
                                // Seat is available
                                rowNo = row;
                                seatNo = seat;
                                screenNo = 1;
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
                        "INSERT INTO tickets (SeatNo, RowNo, ScreenNo, ReservationID, Price) VALUES (?, ?, ?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS
                    );
                    
                    ticketStmt.setInt(1, seatNo);
                    ticketStmt.setInt(2, rowNo);
                    ticketStmt.setInt(3, screenNo);
                    ticketStmt.setInt(4, reservationId);
                    ticketStmt.setDouble(5, price);
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
                
                System.out.println("Tickets JSON: " + ticketsJson.toString());
                
                conn.commit();
                
                // Build success response with ticket details
                String successResponse = String.format(
                    "{\"success\":true,\"message\":\"Booking completed successfully!\",\"reservationId\":%d,\"ticket\":{\"reservationId\":%d,\"movieTitle\":\"%s\",\"tickets\":%s,\"dateTime\":\"%s\"}}",
                    reservationId,
                    reservationId,
                    SimpleServer.escapeJson(movieTitle),
                    ticketsJson.toString(),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM snackscounter WHERE Quantity > 0");
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
                        .append("\"itemName\":\"").append(SimpleServer.escapeJson(rs.getString("ItemName"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("Price")).append(",")
                        .append("\"quantity\":").append(rs.getInt("Quantity"))
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
                    "SELECT EmployeeID FROM employees ORDER BY RAND() LIMIT 1"
                );
                ResultSet empRs = empStmt.executeQuery();
                System.out.println("Employee query executed.");
                if (!empRs.next()) {
                    System.out.println("No employees found in database.");
                    throw new SQLException("No employee available");
                }
                int employeeId = empRs.getInt("EmployeeID");
                System.out.println("Found employee ID: " + employeeId);
                
                // Process each snack order
                String[] orders = ordersJson.split("\\},\\{");
                for (String order : orders) {
                    int snackId = Integer.parseInt(SimpleServer.extractJsonValue(order, "snackId"));
                    int quantity = Integer.parseInt(SimpleServer.extractJsonValue(order, "quantity"));
                    
                    // Check stock availability
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT Quantity FROM snackscounter WHERE SnackID = ?"
                    );
                    checkStmt.setInt(1, snackId);
                    ResultSet checkRs = checkStmt.executeQuery();
                    
                    if (!checkRs.next() || checkRs.getInt("Quantity") < quantity) {
                        throw new SQLException("Insufficient stock for snack ID: " + snackId);
                    }
                    
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
                }
                
                conn.commit();
                SimpleServer.sendJsonResponse(exchange, "{\"success\": true, \"message\": \"Snacks ordered successfully\"}", 200);
                
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
                
                conn.commit();
                SimpleServer.sendJsonResponse(exchange, "{\"success\": true, \"message\": \"Booking confirmed successfully\"}", 200);
                
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
}
