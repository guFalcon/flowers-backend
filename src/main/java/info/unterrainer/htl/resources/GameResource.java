package info.unterrainer.htl.resources;

import info.unterrainer.htl.dtos.HarvestResult;
import info.unterrainer.htl.dtos.Level;
import info.unterrainer.htl.dtos.PathKeyframe;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        List<PathKeyframe> path = service.setTarget(playerId, targetX, targetY);
        return Response.ok(Map.of("status", "ok", "path", path)).build();
    }

    @POST
    @Path("/player/{id}/harvest")
    public Response harvest(@PathParam("id") String playerId) {
        Optional<HarvestResult> result = service.harvest(playerId);
        if (result.isEmpty())
            return Response.status(Response.Status.NOT_FOUND).build();
        HarvestResult harvest = result.get();
        if (harvest.gained() > 0)
            bus.publish(Map.of("type", "harvest", "flowerId", harvest.flowerId(), "fill", 0,
                    "beeId", playerId, "honey", harvest.total()));
        return Response.ok(harvest).build();
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @org.jboss.resteasy.reactive.RestSseElementType(MediaType.APPLICATION_JSON)
    public Multi<Object> events() {
        return bus.eventStream();
    }

    // Admin endpoints are guarded by AdminTokenFilter
    @POST
    @Path("/admin/restart")
    public Response restartLevel() {
        service.restartLevel();
        bus.publish(Map.of("type", "levelRestarted")); // SSE event: tell clients to reload the level
        return Response.ok(Map.of("status", "ok", "message", "Level restarted")).build();
    }
}
