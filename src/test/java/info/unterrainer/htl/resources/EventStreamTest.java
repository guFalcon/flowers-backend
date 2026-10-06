package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.services.LevelService;
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
            try {
                // The server registers the subscriber asynchronously; harvest until the event shows up
                long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
                while (!harvestLine.isDone() && System.currentTimeMillis() < deadline) {
                    flower.setFill(0.5);
                    harvest(flower.getId());
                    Thread.sleep(100);
                }

                assertThat(harvestLine.get()).hasValueSatisfying(line -> assertThat(line).contains("\"fill\":0"));
            } finally {
                response.cancel(true);
                if (response.isDone() && !response.isCompletedExceptionally())
                    response.get().body().close();
                client.shutdownNow();
            }
        }
    }

    private void harvest(String flowerId) {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/harvest/{id}", flowerId)
                .then().statusCode(200);
    }
}
