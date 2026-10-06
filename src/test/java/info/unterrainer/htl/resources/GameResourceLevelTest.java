package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.Level;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

@QuarkusTest
class GameResourceLevelTest {

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;
    @Inject
    ObjectMapper mapper;

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private Cancellable subscription;

    @BeforeEach
    void setUp() {
        events.clear();
        subscription = eventBus.eventStream().subscribe().with(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
    }

    @Test
    void newPlayerJoinsAndGetsLevelWithOwnBee() {
        String playerId = newPlayerId();

        JsonPath level = join(playerId);

        assertThat(level.getString("aspect")).isNotBlank();
        assertThat(level.getString("yourBeeId")).isEqualTo(playerId);
        assertThat(level.getList("bees.findAll { it.id == '" + playerId + "' }")).hasSize(1);
        assertThat(level.getList("flowers")).hasSizeBetween(6, 11);
    }

    @Test
    void rejoiningPlayerKeepsExactlyOneBeeWithSameColour() {
        String playerId = newPlayerId();
        String colour = join(playerId).getString(beePath(playerId) + ".color");

        JsonPath level = join(playerId);

        assertThat(level.getList("bees.findAll { it.id == '" + playerId + "' }")).hasSize(1);
        assertThat(level.getString(beePath(playerId) + ".color")).isEqualTo(colour);
    }

    @Test
    void levelForPlayerIsSnapshotNotLiveState() {
        String playerId = newPlayerId();
        Level snapshot = levelService.getLevelForPlayer(playerId);
        Flower liveFlower = levelService.getLevel().getFlowers().getFirst();
        double liveFill = liveFlower.getFill();

        snapshot.getFlowers().getFirst().setFill(liveFill + 0.5);
        snapshot.getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow()
                .setTargetX(42);

        assertThat(liveFlower.getFill()).isEqualTo(liveFill);
        assertThat(levelService.getLevel().getBees())
                .filteredOn(b -> b.getId().equals(playerId))
                .singleElement()
                .satisfies(b -> assertThat(b.getTargetX()).isNotEqualTo(42));
    }

    @Test
    void knownPlayerSetsTargetAndLevelUpdateIsPublished() throws Exception {
        String playerId = newPlayerId();
        join(playerId);
        events.clear();

        JsonPath body = setTarget(playerId, "{\"x\": 0.25, \"y\": 0.75}");

        assertThat(body.getMap("")).isEqualTo(Map.of("status", "ok"));
        JsonPath level = join(playerId);
        assertThat(level.getDouble(beePath(playerId) + ".targetX")).isEqualTo(0.25);
        assertThat(level.getDouble(beePath(playerId) + ".targetY")).isEqualTo(0.75);

        List<JsonNode> updates = levelUpdates();
        assertThat(updates).isNotEmpty();
        JsonNode bee = findBee(updates.getLast(), playerId);
        assertThat(bee.get("targetX").asDouble()).isEqualTo(0.25);
        assertThat(bee.get("targetY").asDouble()).isEqualTo(0.75);
    }

    @Test
    void unknownPlayerSettingTargetCreatesBeeWithThatTarget() {
        String playerId = newPlayerId();

        setTarget(playerId, "{\"x\": 0.5, \"y\": 0.5}");

        assertThat(levelService.getLevel().getBees())
                .filteredOn(b -> b.getId().equals(playerId))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.getTargetX()).isEqualTo(0.5);
                    assertThat(b.getTargetY()).isEqualTo(0.5);
                });
    }

    @Test
    void missingCoordinateIsTreatedAsZero() {
        String playerId = newPlayerId();
        join(playerId);

        setTarget(playerId, "{\"x\": 0.3}");

        JsonPath level = join(playerId);
        assertThat(level.getDouble(beePath(playerId) + ".targetX")).isEqualTo(0.3);
        assertThat(level.getDouble(beePath(playerId) + ".targetY")).isZero();
    }

    @Test
    void restartRegeneratesFlowersKeepsBeesAndPublishesOneEvent() {
        String playerId = newPlayerId();
        join(playerId);
        List<Flower> previousFlowers = levelService.getLevel().getFlowers();
        events.clear();

        JsonPath body = given()
                .contentType(ContentType.JSON)
                .when().post("/api/admin/restart")
                .then().statusCode(200)
                .extract().jsonPath();

        assertThat(body.getString("status")).isEqualTo("ok");
        assertThat(body.getString("message")).isEqualTo("Level restarted");
        assertThat(events)
                .filteredOn(e -> e instanceof Map<?, ?> m && "levelRestarted".equals(m.get("type")))
                .containsExactly(Map.of("type", "levelRestarted"));
        Level level = levelService.getLevel();
        assertThat(level.getFlowers()).isNotSameAs(previousFlowers).hasSizeBetween(6, 11);
        assertThat(level.getBees()).anyMatch(b -> b.getId().equals(playerId));
    }

    private static String newPlayerId() {
        return "level-test-" + UUID.randomUUID();
    }

    private static String beePath(String playerId) {
        return "bees.find { it.id == '" + playerId + "' }";
    }

    private JsonPath join(String playerId) {
        return given()
                .when().get("/api/level/{playerId}", playerId)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    private JsonPath setTarget(String playerId, String json) {
        return given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/player/{id}/target", playerId)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    private List<JsonNode> levelUpdates() throws Exception {
        List<JsonNode> levels = new ArrayList<>();
        for (Object e : events)
            if (e instanceof Map<?, ?> m && "level-update".equals(m.get("type")))
                levels.add(mapper.readTree((String) m.get("level")));
        return levels;
    }

    private static JsonNode findBee(JsonNode level, String playerId) {
        for (JsonNode bee : level.get("bees"))
            if (playerId.equals(bee.get("id").asText()))
                return bee;
        throw new AssertionError("Bee " + playerId + " not in level " + level);
    }
}
