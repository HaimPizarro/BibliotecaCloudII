package cl.duoc.faas.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class UsuariosFunction {

    @FunctionName("UsuariosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "usuarios"
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
                    
                    String sqlInsert = "INSERT INTO USUARIOS (ID_USUARIO, NOMBRE_COMPLETO, CORREO, TELEFONO) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                        pstmt.setString(1, jsonPost.get("idUsuario").getAsString());
                        pstmt.setString(2, jsonPost.get("nombreCompleto").getAsString());
                        pstmt.setString(3, jsonPost.get("correoElectronico").getAsString());
                        pstmt.setString(4, jsonPost.get("telefono").getAsString());
                        pstmt.executeUpdate();
                    }
                    return request.createResponseBuilder(HttpStatus.CREATED).body("Usuario creado exitosamente").build();

                case GET:
                    String sqlSelect = "SELECT U.ID_USUARIO, U.NOMBRE_COMPLETO, U.CORREO, U.TELEFONO, COUNT(P.ID_PRESTAMO) AS TOTAL_PRESTAMOS " +
                                       "FROM USUARIOS U " +
                                       "LEFT JOIN PRESTAMOS P ON U.ID_USUARIO = P.ID_USUARIO " +
                                       "GROUP BY U.ID_USUARIO, U.NOMBRE_COMPLETO, U.CORREO, U.TELEFONO";
                    
                    java.util.List<JsonObject> listaUsuarios = new java.util.ArrayList<>();
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect);
                         ResultSet rs = pstmt.executeQuery()) {
                        
                        while (rs.next()) {
                            JsonObject userJson = new JsonObject();
                            userJson.addProperty("idUsuario", rs.getString("ID_USUARIO"));
                            userJson.addProperty("nombreCompleto", rs.getString("NOMBRE_COMPLETO"));
                            userJson.addProperty("correoElectronico", rs.getString("CORREO"));
                            userJson.addProperty("telefono", rs.getString("TELEFONO"));
                            userJson.addProperty("librosPrestados", rs.getInt("TOTAL_PRESTAMOS")); 
                            listaUsuarios.add(userJson);
                        }
                    }
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(gson.toJson(listaUsuarios))
                            .build();

                case PUT:
                    String putBody = request.getBody().orElse("{}");
                    JsonObject jsonPut = gson.fromJson(putBody, JsonObject.class);
                    
                    String sqlUpdate = "UPDATE USUARIOS SET NOMBRE_COMPLETO = ?, CORREO = ?, TELEFONO = ? WHERE ID_USUARIO = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                        pstmt.setString(1, jsonPut.get("nombreCompleto").getAsString());
                        pstmt.setString(2, jsonPut.get("correoElectronico").getAsString());
                        pstmt.setString(3, jsonPut.get("telefono").getAsString());
                        pstmt.setString(4, jsonPut.get("idUsuario").getAsString()); // Usamos el ID para buscar a quién actualizar
                        
                        int filasModificadas = pstmt.executeUpdate();
                        if (filasModificadas > 0) {
                            return request.createResponseBuilder(HttpStatus.OK).body("Usuario actualizado exitosamente").build();
                        } else {
                            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body("Usuario no encontrado").build();
                        }
                    }

                case DELETE:
                    String idUsuarioDel = request.getQueryParameters().get("idUsuario");
                    
                    if (idUsuarioDel == null) {
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Falta el parámetro idUsuario en la URL").build();
                    }
                    
                    String sqlDelete = "DELETE FROM USUARIOS WHERE ID_USUARIO = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                        pstmt.setString(1, idUsuarioDel);
                        int filasBorradas = pstmt.executeUpdate();
                        
                        if (filasBorradas > 0) {
                            try {
                                String topicEndpoint = "https://topic-biblioteca-eventos.brazilsouth-1.eventgrid.azure.net/api/events"; 
                                String topicKey = "9x1vmO3b6pvkJ0IhU5bsm8aikrmMSpu95QkV0gw1o8ulV8ibUGGUJQQJ99CEACZoyfiXJ3w3AAABAZEGg4UV";      

                                String datosUsuario = "{\"idUsuario\": \"" + idUsuarioDel + "\"}";

                                String jsonEventGrid = "[{" +
                                        "\"id\": \"" + UUID.randomUUID().toString() + "\"," +
                                        "\"eventType\": \"Biblioteca.UsuarioEliminado\"," +
                                        "\"subject\": \"Usuarios/Eliminacion\"," +
                                        "\"eventTime\": \"" + Instant.now().toString() + "\"," +
                                        "\"data\": " + datosUsuario + "," +
                                        "\"dataVersion\": \"1.0\"" +
                                        "}]";

                                HttpClient client = HttpClient.newHttpClient();
                                HttpRequest reqEvent = HttpRequest.newBuilder()
                                        .uri(URI.create(topicEndpoint))
                                        .header("Content-Type", "application/json")
                                        .header("aeg-sas-key", topicKey)
                                        .POST(HttpRequest.BodyPublishers.ofString(jsonEventGrid))
                                        .build();

                                client.send(reqEvent, HttpResponse.BodyHandlers.ofString());
                                context.getLogger().info("=== EVENTO DE USUARIO ELIMINADO ENVIADO A EVENT GRID ===");

                            } catch (Exception e) {
                                context.getLogger().warning("Error al enviar evento de usuario: " + e.getMessage());
                            }

                            return request.createResponseBuilder(HttpStatus.OK).body("Usuario eliminado exitosamente").build();
                        } else {
                            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body("Usuario no encontrado").build();
                        }
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