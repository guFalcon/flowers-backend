package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import info.unterrainer.htl.services.LevelServiceTestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

/**
 * Wiring of the harvest endpoint; the harvest rules themselves are covered by LevelServiceHarvestTest.
 */
@QuarkusTest
class GameResourceHarvestTest {

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private Cancellable subscription;
    private Flower flower;

    @BeforeEach
    void setUp() {
        levelService.restartLevel();
        List<Flower> flowers = levelService.getLevel().getFlowers();
        // Only the first flower is in the play area, so nothing else is under a bee
        flowers.forEach(f -> f.setX(-10));
        flower = flowers.getFirst();
        flower.setX(0.5);
        flower.setY(0.5);
        events.clear();
        subscription = eventBus.eventStream().subscribe().with(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
    }

    @Test
    void harvestingAfterArrivingYieldsHoneyEmptiesFlowerAndPublishesOneEvent() {
        flower.setFill(0.5);
        String playerId = arrivedAt(0.5, 0.5);

        JsonPath body = harvest(playerId).then().statusCode(200).extract().jsonPath();

        assertThat(body.getMap("")).isEqualTo(Map.of("flowerId", flower.getId(), "gained", 250, "total", 250));
        assertThat(flower.getFill()).isZero();
        assertThat(harvestEvents()).containsExactly(
                Map.of("type", "harvest", "flowerId", flower.getId(), "fill", 0, "beeId", playerId, "honey", 250L));
        JsonPath level = given().when().get("/api/level/{playerId}", playerId)
                .then().statusCode(200).extract().jsonPath();
        assertThat(level.getLong("bees.find { it.id == '" + playerId + "' }.honey")).isEqualTo(250);
    }

    @Test
    void harvestingWhileInFlightYieldsNothingAndPublishesNoEvent() {
        flower.setFill(0.5);
        String playerId = arrivedAt(0.1, 0.5);
        // 0.4 units → 2 s flight, far from over when the harvest arrives
        given().contentType(ContentType.JSON).body(Map.of("x", 0.5, "y", 0.5))
                .when().post("/api/player/{id}/target", playerId)
                .then().statusCode(200);

        JsonPath body = harvest(playerId).then().statusCode(200).extract().jsonPath();

        assertThat(body.getLong("gained")).isZero();
        assertThat(body.getLong("total")).isZero();
        assertThat(flower.getFill()).isEqualTo(0.5);
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void harvestingAnAlmostEmptyFlowerYieldsNothingAndPublishesNoEvent() {
        flower.setFill(0.05);
        String playerId = arrivedAt(0.5, 0.5);

        JsonPath body = harvest(playerId).then().statusCode(200).extract().jsonPath();

        assertThat(body.getString("flowerId")).isEqualTo(flower.getId());
        assertThat(body.getLong("gained")).isZero();
        assertThat(flower.getFill()).isEqualTo(0.05);
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void beeOffEveryFlowerGetsNullFlowerId() {
        String playerId = arrivedAt(0.1, 0.9);

        JsonPath body = harvest(playerId).then().statusCode(200).extract().jsonPath();

        assertThat(body.getMap("")).containsEntry("flowerId", null).containsEntry("gained", 0);
    }

    @Test
    void unknownPlayerGets404AndNoBeeIsCreated() {
        String playerId = "ghost-" + UUID.randomUUID();

        harvest(playerId).then().statusCode(404);

        assertThat(levelService.getLevel().getBees()).noneMatch(b -> b.getId().equals(playerId));
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void oldHarvestEndpointIsGone() {
        flower.setFill(0.5);

        given().contentType(ContentType.JSON)
                .when().post("/api/harvest/{id}", flower.getId())
                .then().statusCode(404);

        assertThat(flower.getFill()).isEqualTo(0.5);
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void flowerFillStaysUnchangedBecauseSchedulerIsDisabled() throws InterruptedException {
        flower.setFill(0.5);

        // fillFlowers would run every second if the scheduler were active
        Thread.sleep(1_500);

        assertThat(flower.getFill()).isEqualTo(0.5);
    }

    private String arrivedAt(double x, double y) {
        String playerId = "harvest-test-" + UUID.randomUUID();
        LevelServiceTestSupport.placeArrived(levelService, playerId, x, y);
        return playerId;
    }

    private static io.restassured.response.Response harvest(String playerId) {
        return given()
                .contentType(ContentType.JSON)
                .when().post("/api/player/{id}/harvest", playerId);
    }

    private List<Object> harvestEvents() {
        return events.stream()
                .filter(e -> e instanceof Map<?, ?> m && "harvest".equals(m.get("type")))
                .toList();
    }
}
