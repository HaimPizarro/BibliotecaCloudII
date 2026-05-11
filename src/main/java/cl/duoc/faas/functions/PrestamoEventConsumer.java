package cl.duoc.faas.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.sql.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class PrestamoEventConsumer {

    @FunctionName("ConsumirEventoPrestamo")
    public void run(
        @EventGridTrigger(name = "evento") String contenidoEvento,
        final ExecutionContext context
    ) {
        try {
            Gson gson = new Gson();
            JsonObject event = gson.fromJson(contenidoEvento, JsonObject.class);
            
            // Valida que sea el evento correcto
            if (!"Biblioteca.NuevoPrestamo".equals(event.get("eventType").getAsString())) { return; }

            JsonObject data = event.getAsJsonObject("data");
            String idLibro = data.get("idLibro").getAsString();
            
            context.getLogger().info("¡EVENTO RECIBIDO! Restando stock del libro: " + idLibro);

            String dbUrl = System.getenv("ORACLE_DB_URL");
            String dbUser = System.getenv("ORACLE_DB_USER");
            String dbPassword = System.getenv("ORACLE_DB_PASSWORD");

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                String sqlUpdate = "UPDATE LIBROS SET DISPONIBILIDAD = DISPONIBILIDAD - 1 WHERE ID_LIBRO = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                    pstmt.setString(1, idLibro);
                    int filas = pstmt.executeUpdate();
                    if(filas > 0) {
                        context.getLogger().info("=== STOCK RESTADO EXITOSAMENTE EN ORACLE ===");
                    }
                }
            }
        } catch (Exception e) {
            context.getLogger().severe("Error al restar stock: " + e.getMessage());
        }
    }
}