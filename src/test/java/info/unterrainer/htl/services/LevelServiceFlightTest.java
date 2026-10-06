package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Level;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceFlightTest {

    private static final long T0 = 1_000_000;

    @Inject
    LevelService levelService;

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

        levelService.setTarget(playerId, 0.6, 0.5, T0);

        assertPosition(positionAt(playerId, T0 + 1_000), 0.4, 0.5);
        assertPosition(positionAt(playerId, T0 + 2_000), 0.6, 0.5);
        assertPosition(positionAt(playerId, T0 + 3_000), 0.6, 0.5);
    }

    @Test
    void shortFlightTakesAtLeastTwoHundredMilliseconds() {
        String playerId = standingAt(0.2, 0.5);

        levelService.setTarget(playerId, 0.2, 0.5, T0);

        assertThat(liveBee(playerId).getFlightEnd()).isEqualTo(T0 + 200);
    }

    @Test
    void redirectMidFlightStartsAtTheCurrentPosition() {
        String playerId = standingAt(0.2, 0.5);
        levelService.setTarget(playerId, 0.6, 0.5, T0);

        levelService.setTarget(playerId, 0.4, 0.9, T0 + 1_000);

        Bee bee = liveBee(playerId);
        assertThat(bee.getFromX()).isCloseTo(0.4, within(1e-9));
        assertThat(bee.getFromY()).isCloseTo(0.5, within(1e-9));
        assertThat(bee.getFlightStart()).isEqualTo(T0 + 1_000);
        assertThat(bee.getFlightEnd()).isEqualTo(T0 + 3_000);
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
    }

    @Test
    void restartResetsHoneyAndKeepsTheFlight() {
        String playerId = standingAt(0.2, 0.5);
        levelService.setTarget(playerId, 0.6, 0.5, T0);
        liveBee(playerId).setHoney(400);

        levelService.restartLevel();

        Bee bee = beeIn(levelService.getLevelForPlayer(playerId, T0 + 1_000), playerId);
        assertThat(bee.getHoney()).isZero();
        assertThat(bee.getX()).isCloseTo(0.4, within(1e-9));
        assertThat(bee.getTargetX()).isEqualTo(0.6);
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
