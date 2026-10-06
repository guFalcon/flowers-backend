package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.htl.BeeNames;
import info.unterrainer.htl.ColorUtils;
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
        assertThat(level.getList("flowers.color", String.class))
                .isSubsetOf(ColorUtils.getNamedColors());
    }

    @Test
    void newBeeStandsStillWithoutHoney() {
        String playerId = newPlayerId();

        JsonPath level = join(playerId);

        String bee = beePath(playerId);
        assertThat(level.getLong(bee + ".honey")).isZero();
        assertThat(level.getDouble(bee + ".targetX")).isEqualTo(level.getDouble(bee + ".x"));
        assertThat(level.getDouble(bee + ".targetY")).isEqualTo(level.getDouble(bee + ".y"));
        // Flight bookkeeping stays internal
        assertThat(level.getMap(bee)).doesNotContainKeys("fromX", "fromY", "flightStart", "flightEnd");
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

        assertThat(body.getString("status")).isEqualTo("ok");
        List<Map<String, Object>> path = body.getList("path");
        assertThat(path).isNotEmpty();
        assertThat(body.getDouble("path[-1].x")).isEqualTo(0.25);
        assertThat(body.getDouble("path[-1].y")).isEqualTo(0.75);
        JsonPath level = join(playerId);
        assertThat(level.getDouble(beePath(playerId) + ".targetX")).isEqualTo(0.25);
        assertThat(level.getDouble(beePath(playerId) + ".targetY")).isEqualTo(0.75);

        List<JsonNode> updates = levelUpdates();
        assertThat(updates).isNotEmpty();
        JsonNode bee = findBee(updates.getLast(), playerId);
        assertThat(bee.get("targetX").asDouble()).isEqualTo(0.25);
        assertThat(bee.get("targetY").asDouble()).isEqualTo(0.75);
        assertThat(bee.get("path").get(bee.get("path").size() - 1).get("t").asLong())
                .isEqualTo(body.getLong("path[-1].t"));
    }

    @Test
    void levelCarriesServerTimeCloudsWindAndPaths() {
        String playerId = newPlayerId();
        long before = System.currentTimeMillis();

        JsonPath level = join(playerId);

        long after = System.currentTimeMillis();
        long serverTime = level.getLong("serverTime");
        assertThat(serverTime).isBetween(before, after);
        assertThat(level.getList("clouds")).hasSizeBetween(4, 6);
        assertThat(level.getList("clouds.size", Double.class)).containsOnly(0.10);
        assertThat(level.getList("clouds.speed", Double.class)).allSatisfy(v -> assertThat(v).isBetween(0.02, 0.05));
        assertThat(level.getList("clouds.drift", Double.class))
                .allSatisfy(v -> assertThat(Math.abs(v)).isLessThanOrEqualTo(Math.toRadians(20)));
        assertThat(level.getList("clouds.t", Long.class)).allSatisfy(t -> assertThat(t).isLessThanOrEqualTo(serverTime));
        assertThat(level.getLong("wind[0].t")).isLessThanOrEqualTo(serverTime);
        assertThat(level.getLong("wind[-1].t")).isGreaterThanOrEqualTo(serverTime + 60_000);
        assertThat(level.getList("bees")).allSatisfy(b -> assertThat(((Map<?, ?>) b).get("path"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isNotEmpty());
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

        JsonPath body = restart();

        assertThat(body.getString("status")).isEqualTo("ok");
        assertThat(body.getString("message")).isEqualTo("Level restarted");
        assertThat(events)
                .filteredOn(e -> e instanceof Map<?, ?> m && "levelRestarted".equals(m.get("type")))
                .containsExactly(Map.of("type", "levelRestarted"));
        Level level = levelService.getLevel();
        assertThat(level.getFlowers()).isNotSameAs(previousFlowers).hasSizeBetween(6, 11);
        assertThat(level.getBees()).anyMatch(b -> b.getId().equals(playerId));
    }

    @Test
    void restartRegeneratesClouds() {
        String playerId = newPlayerId();
        JsonPath before = join(playerId);

        restart();

        JsonPath after = join(playerId);
        assertThat(after.getList("clouds")).hasSizeBetween(4, 6);
        // Speeds are drawn from a continuous range, so a new set never repeats the old one
        assertThat(after.getList("clouds.speed", Double.class))
                .doesNotContainAnyElementsOf(before.getList("clouds.speed", Double.class));
    }

    @Test
    void newBeeGetsANameFromTheList() {
        String playerId = newPlayerId();

        String name = join(playerId).getString(beePath(playerId) + ".name");

        // Other tests leave bees behind; once they use up the list, a suffix is appended
        assertThat(name).isNotBlank();
        assertThat(BeeNames.NAMES).contains(name.replaceFirst(" \\d+$", ""));
    }

    @Test
    void nameSurvivesRejoinAndRestart() {
        String playerId = newPlayerId();
        String name = join(playerId).getString(beePath(playerId) + ".name");

        assertThat(join(playerId).getString(beePath(playerId) + ".name")).isEqualTo(name);
        restart();
        assertThat(join(playerId).getString(beePath(playerId) + ".name")).isEqualTo(name);
    }

    @Test
    void tenJoinsYieldTenDistinctNames() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String playerId = newPlayerId();
            names.add(join(playerId).getString(beePath(playerId) + ".name"));
        }

        assertThat(names).hasSize(10);
    }

    @Test
    void nameWithUmlautsRoundTripsThroughJson() {
        String playerId = newPlayerId();
        join(playerId);
        levelService.getLevel().getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow()
                .setName("Gänseblümchen");

        assertThat(join(playerId).getString(beePath(playerId) + ".name")).isEqualTo("Gänseblümchen");
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

    private static JsonPath restart() {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Admin-Token", "test-admin-token")
                .when().post("/api/admin/restart")
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

    private List<JsonNode> levelUpdates() {
        List<JsonNode> levels = new ArrayList<>();
        for (Object e : events)
            if (e instanceof Map<?, ?> m && "level-update".equals(m.get("type")))
                levels.add(mapper.valueToTree(m.get("level")));
        return levels;
    }

    private static JsonNode findBee(JsonNode level, String playerId) {
        for (JsonNode bee : level.get("bees"))
            if (playerId.equals(bee.get("id").asText()))
                return bee;
        throw new AssertionError("Bee " + playerId + " not in level " + level);
    }
}
