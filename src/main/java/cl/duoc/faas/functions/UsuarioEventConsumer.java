package cl.duoc.faas.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.sql.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class UsuarioEventConsumer {

    @FunctionName("ConsumirEventoUsuario")
    public void run(
        @EventGridTrigger(name = "evento") String contenidoEvento,
        final ExecutionContext context
    ) {
        try {
            Gson gson = new Gson();
            JsonObject event = gson.fromJson(contenidoEvento, JsonObject.class);
            
            if (!"Biblioteca.UsuarioEliminado".equals(event.get("eventType").getAsString())) { return; }

            String idUsuario = event.getAsJsonObject("data").get("idUsuario").getAsString();
            context.getLogger().info("¡EVENTO RECIBIDO! Borrando préstamos del usuario: " + idUsuario);

            String dbUrl = System.getenv("ORACLE_DB_URL");
            String dbUser = System.getenv("ORACLE_DB_USER");
            String dbPassword = System.getenv("ORACLE_DB_PASSWORD");

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                String sqlDelete = "DELETE FROM PRESTAMOS WHERE ID_USUARIO = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                    pstmt.setString(1, idUsuario);
                    int filas = pstmt.executeUpdate();
                    context.getLogger().info("=== SE ELIMINARON " + filas + " PRÉSTAMOS ASOCIADOS ===");
                }
            }
        } catch (Exception e) {
            context.getLogger().severe("Error al borrar préstamos en cascada: " + e.getMessage());
        }
    }
}