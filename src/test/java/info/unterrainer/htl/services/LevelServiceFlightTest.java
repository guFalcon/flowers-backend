package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Cloud;
import info.unterrainer.htl.dtos.Level;
import info.unterrainer.htl.dtos.PathKeyframe;
import info.unterrainer.htl.dtos.WindKeyframe;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceFlightTest {

    private static final long T0 = 1_000_000;

    @Inject
    LevelService levelService;

    @BeforeEach
    void setUp() {
        // Random clouds would make the flight times random
        levelService.useWeather(Weather.none());
    }

    @AfterEach
    void tearDown() {
        levelService.useWeather(Weather.generate(System.currentTimeMillis(), new Random()));
    }

    @Test
    void newBeeStandsStillWithoutHoney() {
        String playerId = newPlayerId();

        Bee bee = beeIn(levelService.getLevelForPlayer(playerId, T0), playerId);

        assertThat(bee.getHoney()).isZero();
        assertThat(bee.getTargetX()).isEqualTo(bee.getX());
        assertThat(bee.getTargetY()).isEqualTo(bee.getY());
        Bee later = beeIn(levelService.getLevelForPlayer(playerId, T0 + 5_000), playerId);
        assertThat(later.getX()).isEqualTo(bee.getX());
        assertThat(later.getY()).isEqualTo(bee.getY());
    }

    @Test
    void flightTakesFiveSecondsPerUnitOfDistance() {
        String playerId = standingAt(0.2, 0.5);

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, T0);

        assertPosition(positionAt(playerId, T0 + 1_000), 0.4, 0.5);
        assertPosition(positionAt(playerId, T0 + 2_000), 0.6, 0.5);
        assertPosition(positionAt(playerId, T0 + 3_000), 0.6, 0.5);
        assertThat(path).containsExactly(new PathKeyframe(T0, 0.2, 0.5), new PathKeyframe(T0 + 2_000, 0.6, 0.5));
        assertThat(liveBee(playerId).getPath()).isEqualTo(path);
    }

    @Test
    void shortFlightTakesAtLeastTwoHundredMilliseconds() {
        String playerId = standingAt(0.2, 0.5);

        levelService.setTarget(playerId, 0.2, 0.5, T0);

        assertThat(liveBee(playerId).getPath()).hasSize(2);
        assertThat(liveBee(playerId).arrivalTime()).isEqualTo(T0 + 200);
    }

    @Test
    void redirectMidFlightStartsAtTheCurrentPosition() {
        String playerId = standingAt(0.2, 0.5);
        levelService.setTarget(playerId, 0.6, 0.5, T0);

        levelService.setTarget(playerId, 0.4, 0.9, T0 + 1_000);

        List<PathKeyframe> path = liveBee(playerId).getPath();
        assertThat(path).hasSize(2);
        assertThat(path.getFirst().t()).isEqualTo(T0 + 1_000);
        assertThat(path.getFirst().x()).isCloseTo(0.4, within(1e-9));
        assertThat(path.getFirst().y()).isCloseTo(0.5, within(1e-9));
        assertThat(path.getLast()).isEqualTo(new PathKeyframe(T0 + 3_000, 0.4, 0.9));
    }

    @Test
    void levelShowsTheBeeInFlight() {
        String playerId = standingAt(0.2, 0.5);
        levelService.setTarget(playerId, 0.6, 0.5, T0);

        Bee bee = beeIn(levelService.getLevelForPlayer(playerId, T0 + 1_000), playerId);

        assertThat(bee.getX()).isCloseTo(0.4, within(1e-9));
        assertThat(bee.getY()).isCloseTo(0.5, within(1e-9));
        assertThat(bee.getTargetX()).isEqualTo(0.6);
        assertThat(bee.getTargetY()).isEqualTo(0.5);
        assertThat(bee.getPath()).containsExactly(new PathKeyframe(T0, 0.2, 0.5), new PathKeyframe(T0 + 2_000, 0.6, 0.5));
    }

    @Test
    void restartResetsHoneyAndKeepsTheFlight() {
        String playerId = standingAt(0.2, 0.5);
        levelService.setTarget(playerId, 0.6, 0.5, T0);
        liveBee(playerId).setHoney(400);

        levelService.restartLevel();
        levelService.useWeather(Weather.none());

        Bee bee = beeIn(levelService.getLevelForPlayer(playerId, T0 + 1_000), playerId);
        assertThat(bee.getHoney()).isZero();
        assertThat(bee.getX()).isCloseTo(0.4, within(1e-9));
        assertThat(bee.getTargetX()).isEqualTo(0.6);
    }

    @Test
    void flightThroughARestingCloudIsSlowedInside() {
        String playerId = standingAt(0.2, 0.5);
        levelService.useWeather(constantWind(0, restingCloud(0.4, 0.5)));

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, T0);

        // Outside 0.222 units at 0.2/s (1.11 s), inside 0.178 units at 0.07/s (2.54 s)
        assertThat(path).hasSize(4);
        assertThat(path.getFirst()).isEqualTo(new PathKeyframe(T0, 0.2, 0.5));
        assertThat(path.get(1).x()).isCloseTo(0.311, within(0.001));
        assertThat(path.get(2).x()).isCloseTo(0.489, within(0.001));
        assertThat(path.getLast().x()).isEqualTo(0.6);
        assertThat(path.getLast().t() - T0).isCloseTo(3_650L, within(100L));
        assertThat(liveBee(playerId).arrivalTime()).isEqualTo(path.getLast().t());
    }

    @Test
    void flightPastACloudIsNotSlowed() {
        String playerId = standingAt(0.2, 0.5);
        levelService.useWeather(constantWind(0, restingCloud(0.4, 0.7)));

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, T0);

        assertThat(path).containsExactly(new PathKeyframe(T0, 0.2, 0.5), new PathKeyframe(T0 + 2_000, 0.6, 0.5));
    }

    @Test
    void driftingCloudCatchesTheBee() {
        String playerId = standingAt(0.2, 0.5);
        // Drifts down at 0.05 heights per second; at rest it would stay 0.1 heights above the line
        Cloud cloud = Cloud.builder().id("c").x(0.5).y(0.4).t(T0).size(0.10).speed(0.05).drift(0).build();
        levelService.useWeather(constantWind(Math.PI / 2, cloud));

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, T0);

        assertThat(path).hasSize(4);
        assertThat(path.getLast().t() - T0).isGreaterThan(2_100);
        // At the entry and the exit the bee is on the edge of the moving cloud
        for (PathKeyframe k : List.of(path.get(1), path.get(2))) {
            double cloudY = 0.4 + 0.05 * (k.t() - T0) / 1000.0;
            double distance = Math.hypot((k.x() - 0.5) * 9.0 / 16.0, k.y() - cloudY);
            assertThat(distance).isCloseTo(0.05, within(0.001));
        }
    }

    @Test
    void overlappingCloudsDoNotSlowFurther() {
        String playerId = standingAt(0.2, 0.5);
        levelService.useWeather(constantWind(0, restingCloud(0.4, 0.5), restingCloud(0.42, 0.5)));

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, T0);

        // Inside from x 0.311 to 0.509 (0.198 units at 0.07/s = 2.83 s), outside 0.202 units (1.01 s)
        assertThat(path).hasSize(4);
        assertThat(path.getLast().t() - T0).isCloseTo(3_837L, within(100L));
    }

    private static Cloud restingCloud(double x, double y) {
        return Cloud.builder().id("c-" + x).x(x).y(y).t(T0).size(0.10).speed(0).drift(0).build();
    }

    // No wind turn within the first 15 s after T0
    private static Weather constantWind(double angle, Cloud... clouds) {
        return new Weather(List.of(clouds), List.of(new WindKeyframe(T0, angle)), T0 + 15_000);
    }

    // A bee that has arrived at (x, y) before T0
    private String standingAt(double x, double y) {
        String playerId = newPlayerId();
        levelService.setTarget(playerId, x, y, T0 - 10_000);
        return playerId;
    }

    private Bee.Position positionAt(String playerId, long now) {
        Bee bee = beeIn(levelService.getLevelForPlayer(playerId, now), playerId);
        return new Bee.Position(bee.getX(), bee.getY());
    }

    private Bee liveBee(String playerId) {
        return levelService.getLevel().getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow();
    }

    private static Bee beeIn(Level level, String playerId) {
        return level.getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow();
    }

    private static void assertPosition(Bee.Position position, double x, double y) {
        assertThat(position.x()).isCloseTo(x, within(1e-9));
        assertThat(position.y()).isCloseTo(y, within(1e-9));
    }

    private static String newPlayerId() {
        return "flight-" + UUID.randomUUID();
    }
}
