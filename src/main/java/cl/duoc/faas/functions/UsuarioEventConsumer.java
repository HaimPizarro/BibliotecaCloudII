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
            context.getLogger().info("¡EVENTO RECIBIDO! Devolviendo libros y borrando préstamos del usuario: " + idUsuario);

            String dbUrl = System.getenv("ORACLE_DB_URL");
            String dbUser = System.getenv("ORACLE_DB_USER");
            String dbPassword = System.getenv("ORACLE_DB_PASSWORD");

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                
                String sqlSelect = "SELECT ID_LIBRO FROM PRESTAMOS WHERE ID_USUARIO = ?";
                try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
                    pstmtSelect.setString(1, idUsuario);
                    
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        
                        String sqlUpdateStock = "UPDATE LIBROS SET DISPONIBILIDAD = DISPONIBILIDAD + 1 WHERE ID_LIBRO = ?";
                        try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdateStock)) {
                            
                            int totalDevueltos = 0;
                            while (rs.next()) {
                                String idLibro = rs.getString("ID_LIBRO");
                                
                                pstmtUpdate.setString(1, idLibro);
                                pstmtUpdate.executeUpdate(); // Suma +1 al libro en esta vuelta
                                
                                totalDevueltos++;
                            }
                            context.getLogger().info("=== SE DEVOLVIERON " + totalDevueltos + " LIBROS AL STOCK INDIVIDUALMENTE ===");
                        }
                    }
                }

                String sqlDelete = "DELETE FROM PRESTAMOS WHERE ID_USUARIO = ?";
                try (PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {
                    pstmtDelete.setString(1, idUsuario);
                    int filas = pstmtDelete.executeUpdate();
                    context.getLogger().info("=== SE ELIMINARON " + filas + " PRÉSTAMOS ASOCIADOS ===");
                }
            }
        } catch (Exception e) {
            context.getLogger().severe("Error al procesar eliminación en cascada: " + e.getMessage());
        }
    }
}