package cl.duoc.faas.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

public class PrestamoEventConsumer {

   //  Actúa como consumidora en la arquitectura y se activa automaticamente a travez de l event grid.
    @FunctionName("ConsumirEventoPrestamo")
    public void run(
        @EventGridTrigger(name = "evento") String contenidoEvento,
        final ExecutionContext context
    ) {
        context.getLogger().info("================================================");
        context.getLogger().info("¡NUEVO EVENTO RECIBIDO DESDE EVENT GRID!");
        context.getLogger().info("================================================");
        
        context.getLogger().info("Detalles del préstamo: " + contenidoEvento);
        
        context.getLogger().info("Acción en segundo plano: Simulando el envío de correo de confirmación al usuario...");
        context.getLogger().info("================================================");
    }
}