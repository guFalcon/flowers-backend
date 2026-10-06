package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.services.LevelService;
import info.unterrainer.htl.services.LevelServiceTestSupport;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class EventStreamTest {

    private static final long TIMEOUT_MILLIS = 5_000;

    @TestHTTPResource("/api/events")
    URI eventsUri;

    @Inject
    LevelService levelService;

    @Test
    void harvestReachesHttpSubscriber() throws Exception {
        levelService.restartLevel();
        Flower flower = levelService.getLevel().getFlowers().getFirst();
        String playerId = UUID.randomUUID().toString();
        LevelServiceTestSupport.placeArrived(levelService, playerId, flower.getX(), flower.getY());

        HttpRequest request = HttpRequest.newBuilder(eventsUri)
                .header("Accept", "text/event-stream")
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            CompletableFuture<HttpResponse<Stream<String>>> response = client.sendAsync(request,
                    HttpResponse.BodyHandlers.ofLines());
            CompletableFuture<Optional<String>> harvestLine = response
                    .thenApplyAsync(r -> r.body()
                            .filter(line -> line.startsWith("data:"))
                            .filter(line -> line.contains("\"type\":\"harvest\""))
                            .filter(line -> line.contains("\"flowerId\":\"" + flower.getId() + "\""))
                            .findFirst())
                    .orTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Set<Long> totals = ConcurrentHashMap.newKeySet();
            try {
                // The server registers the subscriber asynchronously; harvest until the event shows up
                long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
                while (!harvestLine.isDone() && System.currentTimeMillis() < deadline) {
                    flower.setFill(0.5);
                    totals.add(harvest(playerId));
                    Thread.sleep(100);
                }

                // Several harvests may run before the subscriber is registered; the event must carry the total
                // of one of their responses
                assertThat(harvestLine.get()).hasValueSatisfying(line -> assertThat(line)
                        .contains("\"fill\":0")
                        .contains("\"beeId\":\"" + playerId + "\""));
                long eventHoney = Long.parseLong(harvestLine.get().orElseThrow().replaceAll(".*\"honey\":(\\d+).*", "$1"));
                assertThat(eventHoney).isPositive().isIn(totals);
            } finally {
                response.cancel(true);
                if (response.isDone() && !response.isCompletedExceptionally())
                    response.get().body().close();
                client.shutdownNow();
            }
        }
    }

    @Test
    void levelUpdateCarriesLevelAsObject() throws Exception {
        String playerId = UUID.randomUUID().toString();
        // Join before subscribing, so the only level-update naming this bee is the one after the target
        levelService.registerBee(playerId);
        Flower flower = levelService.getLevel().getFlowers().getFirst();

        HttpRequest request = HttpRequest.newBuilder(eventsUri)
                .header("Accept", "text/event-stream")
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            CompletableFuture<HttpResponse<Stream<String>>> response = client.sendAsync(request,
                    HttpResponse.BodyHandlers.ofLines());
            CompletableFuture<Optional<String>> levelLine = response
                    .thenApplyAsync(r -> r.body()
                            .filter(line -> line.startsWith("data:"))
                            .filter(line -> line.contains("\"type\":\"level-update\""))
                            .filter(line -> line.contains(playerId))
                            .findFirst())
                    .orTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            try {
                // The server registers the subscriber asynchronously; set the target until the event shows up
                long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
                while (!levelLine.isDone() && System.currentTimeMillis() < deadline) {
                    setTarget(playerId, 0.25, 0.75);
                    Thread.sleep(100);
                }

                assertThat(levelLine.get()).hasValueSatisfying(line -> assertThat(line)
                        .contains("\"level\":{")
                        .contains("\"flowers\":[")
                        .contains("\"id\":\"" + flower.getId() + "\"")
                        .contains("\"bees\":[")
                        .contains("\"id\":\"" + playerId + "\"")
                        .contains("\"targetX\":0.25")
                        .contains("\"targetY\":0.75")
                        .contains("\"serverTime\":")
                        .contains("\"clouds\":[{")
                        .contains("\"wind\":[{")
                        .contains("\"path\":[{"));
            } finally {
                response.cancel(true);
                if (response.isDone() && !response.isCompletedExceptionally())
                    response.get().body().close();
                client.shutdownNow();
            }
        }
    }

    private void setTarget(String playerId, double x, double y) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("x", x, "y", y))
                .when().post("/api/player/{id}/target", playerId)
                .then().statusCode(200);
    }

    private long harvest(String playerId) {
        return given()
                .contentType(ContentType.JSON)
                .when().post("/api/player/{id}/harvest", playerId)
                .then().statusCode(200)
                .extract().jsonPath().getLong("total");
    }
}
