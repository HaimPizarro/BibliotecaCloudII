package cl.duoc.faas.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.Optional;

//imports para Event Grid
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

public class PrestamosFunction {

    @FunctionName("PrestamosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "prestamos"
            )
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String dbUrl = System.getenv("ORACLE_DB_URL");
        String dbUser = System.getenv("ORACLE_DB_USER");
        String dbPassword = System.getenv("ORACLE_DB_PASSWORD");

        HttpMethod method = request.getHttpMethod();
        Gson gson = new Gson();

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            
            switch (method) {
                case POST:
                    String postBody = request.getBody().orElse("{}");
                    JsonObject jsonPost = gson.fromJson(postBody, JsonObject.class);
                    
                    String sqlInsert = "INSERT INTO PRESTAMOS (ID_PRESTAMO, ID_USUARIO, ID_LIBRO, FECHA_PRESTAMO, ESTADO) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                        pstmt.setInt(1, jsonPost.get("idPrestamo").getAsInt());
                        pstmt.setString(2, jsonPost.get("idUsuario").getAsString());
                        pstmt.setString(3, jsonPost.get("idLibro").getAsString());
                        pstmt.setString(4, jsonPost.get("fechaPrestamo").getAsString());
                        pstmt.setString(5, jsonPost.get("estadoPrestamo").getAsString());
                        pstmt.executeUpdate();
                        

                        // Integracion event grid
                        try {
                            String topicEndpoint = "https://topic-biblioteca-eventos.brazilsouth-1.eventgrid.azure.net/api/events"; 
                            String topicKey = "9x1vmO3b6pvkJ0IhU5bsm8aikrmMSpu95QkV0gw1o8ulV8ibUGGUJQQJ99CEACZoyfiXJ3w3AAABAZEGg4UV";      

                            String datosPrestamo = "{\"idPrestamo\": " + jsonPost.get("idPrestamo").getAsInt() + 
                                                   ", \"idUsuario\": \"" + jsonPost.get("idUsuario").getAsString() + "\"}";

                            String jsonEventGrid = "[{" +
                                    "\"id\": \"" + UUID.randomUUID().toString() + "\"," +
                                    "\"eventType\": \"Biblioteca.NuevoPrestamo\"," +
                                    "\"subject\": \"Prestamos/Creacion\"," +
                                    "\"eventTime\": \"" + Instant.now().toString() + "\"," +
                                    "\"data\": " + datosPrestamo + "," +
                                    "\"dataVersion\": \"1.0\"" +
                                    "}]";

                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest reqEvent = HttpRequest.newBuilder()
                                    .uri(URI.create(topicEndpoint))
                                    .header("Content-Type", "application/json")
                                    .header("aeg-sas-key", topicKey)
                                    .POST(HttpRequest.BodyPublishers.ofString(jsonEventGrid))
                                    .build();

                            //respuesta del event grid
                            HttpResponse<String> response = client.send(reqEvent, HttpResponse.BodyHandlers.ofString());
                            
                            context.getLogger().warning("Status: " + response.statusCode() + " ===");
                            context.getLogger().warning("Respuesta: " + response.body() + " ===");

                        } catch (Exception e) {
                            context.getLogger().severe("Error al enviar evento: " + e.getMessage());
                        }
                        // fin
                    }
                    return request.createResponseBuilder(HttpStatus.CREATED).body("Préstamo registrado exitosamente").build();

                case GET:
                    String sqlSelect = "SELECT ID_PRESTAMO, ID_USUARIO, ID_LIBRO, FECHA_PRESTAMO, ESTADO FROM PRESTAMOS";
                    java.util.List<JsonObject> listaPrestamos = new java.util.ArrayList<>();
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect);
                         ResultSet rs = pstmt.executeQuery()) {
                        
                        while (rs.next()) {
                            JsonObject prestamoJson = new JsonObject();
                            prestamoJson.addProperty("idPrestamo", rs.getInt("ID_PRESTAMO"));
                            prestamoJson.addProperty("idUsuario", rs.getString("ID_USUARIO"));
                            prestamoJson.addProperty("idLibro", rs.getString("ID_LIBRO"));
                            prestamoJson.addProperty("fechaPrestamo", rs.getString("FECHA_PRESTAMO"));
                            prestamoJson.addProperty("estadoPrestamo", rs.getString("ESTADO"));
                            listaPrestamos.add(prestamoJson);
                        }
                    }
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(gson.toJson(listaPrestamos))
                            .build();

                case PUT:
                    String putBody = request.getBody().orElse("{}");
                    JsonObject jsonPut = gson.fromJson(putBody, JsonObject.class);
                    
                    String sqlUpdate = "UPDATE PRESTAMOS SET ESTADO = ? WHERE ID_PRESTAMO = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                        pstmt.setString(1, jsonPut.get("estadoPrestamo").getAsString());
                        pstmt.setInt(2, jsonPut.get("idPrestamo").getAsInt());
                        
                        int filasModificadas = pstmt.executeUpdate();
                        if (filasModificadas > 0) {
                            return request.createResponseBuilder(HttpStatus.OK).body("Estado del préstamo actualizado exitosamente").build();
                        } else {
                            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body("Préstamo no encontrado").build();
                        }
                    }

                case DELETE:
                    String idPrestamoDel = request.getQueryParameters().get("idPrestamo");
                    
                    if (idPrestamoDel == null) {
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Falta el parámetro idPrestamo en la URL").build();
                    }
                    
                    String sqlDelete = "DELETE FROM PRESTAMOS WHERE ID_PRESTAMO = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                        pstmt.setInt(1, Integer.parseInt(idPrestamoDel));
                        int filasBorradas = pstmt.executeUpdate();
                        
                        if (filasBorradas > 0) {
                            return request.createResponseBuilder(HttpStatus.OK).body("Préstamo eliminado exitosamente").build();
                        } else {
                            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body("Préstamo no encontrado").build();
                        }
                    } catch (NumberFormatException e) {
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("El ID del préstamo debe ser un número válido").build();
                    }

                default:
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Método no permitido").build();
            }

        } catch (SQLException e) {
            context.getLogger().severe("Error de Base de Datos: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("Error BD: " + e.getMessage()).build();
        }
    }
}