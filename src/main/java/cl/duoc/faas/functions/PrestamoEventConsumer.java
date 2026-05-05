package cl.duoc.faas.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

public class PrestamoEventConsumer {

   //  Actúa como consumidor en la arquitectura y se activa automaticamente a travez del event grid.
    @FunctionName("ConsumirEventoPrestamo")
    public void run(
        @EventGridTrigger(name = "evento") String contenidoEvento,
        final ExecutionContext context
    ) {
        context.getLogger().info("Evento recibido");
        
        context.getLogger().info("Detalles del préstamo: " + contenidoEvento);

    }
}