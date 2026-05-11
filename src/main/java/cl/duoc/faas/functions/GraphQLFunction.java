package cl.duoc.faas.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.Optional;

public class GraphQLFunction {

    @FunctionName("GraphQLFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "graphql"
            )
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("=== Petición GraphQL procesada en FaaS ===");
        
        String jsonResponse = "{\"data\": {\"mensaje\": \"GraphQL ejecutado en Azure FaaS\"}}";

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonResponse)
                .build();
    }
}