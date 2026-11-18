package bg.hristomanov.education.quarkus.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import bg.hristomanov.education.quarkus.runtime.GreetingMessage;

/** Извикването към генерирания CDI bean е директно по интерфейс. */
@Path("/generated-greeting")
public class GreetingResource {
    @Inject
    GreetingMessage generated;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String greeting() {
        return generated.message();
    }
}
