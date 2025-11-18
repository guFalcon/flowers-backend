package info.unterrainer.htl.resources;

import info.unterrainer.htl.dtos.Level;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    LevelService service;
    @Inject
    EventBusService bus;

    @GET
    @Path("/level/{playerId}")
    public Level getLevel(@PathParam("playerId") String playerId) {
        if (playerId == null || playerId.isBlank())
            playerId = UUID.randomUUID().toString();
        return service.getLevelForPlayer(playerId);
    }


    @POST
    @Path("/player/{id}/target")
    public Response setTarget(@PathParam("id") String playerId, Map<String, Double> payload) {
        double targetX = payload.getOrDefault("x", 0.0);
        double targetY = payload.getOrDefault("y", 0.0);
        service.setTarget(playerId, targetX, targetY);
        return Response.ok(Map.of("status", "ok")).build();
    }

    @POST
    @Path("/harvest/{id}")
    public Response harvest(@PathParam("id") String flowerId) {
        double honey = service.harvest(flowerId);
        if (honey == 0)
            bus.publish(Map.of("type","harvest","flowerId", flowerId));
        return Response.ok(Map.of("flowerId", flowerId, "honey", honey)).build();
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @org.jboss.resteasy.reactive.RestSseElementType(MediaType.APPLICATION_JSON)
    public Multi<Object> events() {
        return bus.eventStream();
    }

    @POST
    @Path("/admin/restart")
    public Response restartLevel() {
        service.restartLevel();
        bus.publish(Map.of("type", "levelRestarted")); // <- Neu: SSE-Event
        return Response.ok(Map.of("status", "ok", "message", "Level restarted")).build();
    }
}
