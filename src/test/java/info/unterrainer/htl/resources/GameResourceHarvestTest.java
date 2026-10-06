package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

@QuarkusTest
class GameResourceHarvestTest {

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private Cancellable subscription;

    @BeforeEach
    void setUp() {
        levelService.restartLevel();
        events.clear();
        subscription = eventBus.eventStream().subscribe().with(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
    }

    @Test
    void harvestingFilledFlowerYieldsHoneyEmptiesItAndPublishesOneEvent() {
        Flower flower = firstFlower();
        flower.setFill(0.5);

        JsonPath body = harvest(flower.getId());

        assertThat(body.getString("flowerId")).isEqualTo(flower.getId());
        assertThat(body.getDouble("honey")).isGreaterThan(0);
        assertThat(flower.getFill()).isZero();
        assertThat(harvestEvents()).containsExactly(
                Map.of("type", "harvest", "flowerId", flower.getId(), "fill", 0));
    }

    @Test
    void harvestingAlmostEmptyFlowerYieldsNothingAndPublishesNoEvent() {
        Flower flower = firstFlower();
        flower.setFill(0.05);

        JsonPath body = harvest(flower.getId());

        assertThat(body.getDouble("honey")).isZero();
        assertThat(flower.getFill()).isEqualTo(0.05);
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void harvestingUnknownFlowerYieldsNothingAndPublishesNoEvent() {
        JsonPath body = harvest("no-such-flower");

        assertThat(body.getString("flowerId")).isEqualTo("no-such-flower");
        assertThat(body.getDouble("honey")).isZero();
        assertThat(harvestEvents()).isEmpty();
    }

    @Test
    void flowerFillStaysUnchangedBecauseSchedulerIsDisabled() throws InterruptedException {
        Flower flower = firstFlower();
        flower.setFill(0.5);

        // fillFlowers would run every second if the scheduler were active
        Thread.sleep(1_500);

        assertThat(flower.getFill()).isEqualTo(0.5);
    }

    private Flower firstFlower() {
        return levelService.getLevel().getFlowers().getFirst();
    }

    private JsonPath harvest(String flowerId) {
        return given()
                .contentType(ContentType.JSON)
                .when().post("/api/harvest/{id}", flowerId)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    private List<Object> harvestEvents() {
        return events.stream()
                .filter(e -> e instanceof Map<?, ?> m && "harvest".equals(m.get("type")))
                .toList();
    }
}
