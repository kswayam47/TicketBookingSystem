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

public class SimpleServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Serve static files
        server.createContext("/", new StaticFileHandler());
        
        // API endpoints
        server.createContext("/api/movies", new MoviesHandler());
        server.createContext("/api/book", new BookingHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("Server started on port 8080");
        System.out.println("Open http://localhost:8080 in your browser");
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
        
        private String escapeJson(String input) {
            if (input == null) {
                return "";
            }
            return input.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\b", "\\b")
                       .replace("\f", "\\f")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t");
        }
    }
    
    static class BookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            try {
                // Read request body
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }
                
                // Parse JSON manually to avoid dependency
                String json = requestBody.toString();
                String name = extractJsonValue(json, "name");
                int age = Integer.parseInt(extractJsonValue(json, "age"));
                String gender = extractJsonValue(json, "gender");
                int movieId = Integer.parseInt(extractJsonValue(json, "movieId"));
                
                // Insert into database
                Connection conn = DatabaseConnection.getConnection();
                
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
                
                // Then create reservation
                PreparedStatement reservationStmt = conn.prepareStatement(
                    "INSERT INTO reservation (DateTime, Mode, CustomerID, MovieID) VALUES (NOW(), 'Online', ?, ?)"
                );
                reservationStmt.setInt(1, customerId);
                reservationStmt.setInt(2, movieId);
                reservationStmt.executeUpdate();
                
                String success = "{\"success\": true, \"message\": \"Booking completed successfully!\"}";
                byte[] response = success.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                
                rs.close();
                customerStmt.close();
                reservationStmt.close();
                conn.close();
                
            } catch (Exception e) {
                String error = "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                byte[] response = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }
        
        private String extractJsonValue(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey) + searchKey.length();
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);
            if (end == -1) { // For numbers
                end = json.indexOf(",", start);
                if (end == -1) {
                    end = json.indexOf("}", start);
                }
                return json.substring(start, end).trim();
            }
            return json.substring(start, end);
        }
    }
}
