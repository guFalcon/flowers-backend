package info.unterrainer.htl.services;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Level;
import info.unterrainer.htl.dtos.PathKeyframe;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceCleanupTest {

    private static final long FIVE_MINUTES = 5 * 60_000;
    private static final long FOUR_MINUTES = 4 * 60_000;

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;

    @Test
    void beesInactiveForMoreThanFiveMinutesAreRemovedAndLevelIsPublished() throws InterruptedException {
        String p1 = "cleanup-" + UUID.randomUUID();
        String p2 = "cleanup-" + UUID.randomUUID();

        levelService.registerBee(p1);
        Thread.sleep(5);
        long t0 = System.currentTimeMillis();
        Thread.sleep(5);
        levelService.registerBee(p2);

        List<Object> events = new CopyOnWriteArrayList<>();
        Cancellable subscription = eventBus.eventStream().subscribe().with(events::add);
        try {
            // p1 was last active before t0 (> 5 min ago from "now"), p2 after t0 (< 5 min ago)
            levelService.cleanupInactiveBees(t0 + FIVE_MINUTES + 1);
        } finally {
            subscription.cancel();
        }

        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId)
                .doesNotContain(p1)
                .contains(p2);
        assertThat(events)
                .filteredOn(e -> e instanceof Map<?, ?> m && "level-update".equals(m.get("type")))
                .hasSize(1);
    }

    @Test
    void beeIdleForFourMinutesStays() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        levelService.registerBee(playerId, now - FOUR_MINUTES);

        levelService.cleanupInactiveBees(now);

        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId).contains(playerId);
    }

    @Test
    void rejoiningKeepsAnIdleBeeAlive() {
        String playerId = "cleanup-" + UUID.randomUUID();
        levelService.registerBee(playerId);
        long now = System.currentTimeMillis();
        liveBee(playerId).setLastActive(now - FOUR_MINUTES);

        given()
                .when().get("/api/level/{playerId}", playerId)
                .then().statusCode(200);
        levelService.cleanupInactiveBees(now + 2 * 60_000);

        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId).contains(playerId);
    }

    @Test
    void retiredBeeRejoinsWithNameColourHoneyAndPosition() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        Bee original = retireAt(playerId, 0.3, 0.7, 250, t0);

        Level level = levelService.getLevelForPlayer(playerId, t0 + FIVE_MINUTES + 2);

        List<Bee> own = level.getBees().stream().filter(b -> b.getId().equals(playerId)).toList();
        assertThat(own).hasSize(1);
        Bee bee = own.getFirst();
        assertThat(bee.getName()).isEqualTo(original.getName());
        assertThat(bee.getColor()).isEqualTo(original.getColor());
        assertThat(bee.getHoney()).isEqualTo(250);
        assertThat(bee.getX()).isEqualTo(0.3);
        assertThat(bee.getY()).isEqualTo(0.7);
        assertThat(bee.getTargetX()).isEqualTo(0.3);
        assertThat(bee.getTargetY()).isEqualTo(0.7);
        assertThat(bee.getPath()).hasSize(1);
    }

    @Test
    void retiredBeeComingBackPublishesALevelUpdate() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        retireAt(playerId, 0.3, 0.7, 0, t0);

        List<Object> events = new CopyOnWriteArrayList<>();
        Cancellable subscription = eventBus.eventStream().subscribe().with(events::add);
        try {
            levelService.registerBee(playerId, t0 + FIVE_MINUTES + 2);
        } finally {
            subscription.cancel();
        }

        assertThat(events)
                .filteredOn(e -> e instanceof Map<?, ?> m && "level-update".equals(m.get("type")))
                .hasSize(1);
    }

    @Test
    void retiredPlayerSetsATarget() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        retireAt(playerId, 0.2, 0.5, 300, t0);

        List<PathKeyframe> path = levelService.setTarget(playerId, 0.6, 0.5, t0 + FIVE_MINUTES + 2);

        assertThat(path.getFirst().x()).isEqualTo(0.2);
        assertThat(path.getFirst().y()).isEqualTo(0.5);
        assertThat(path.getLast().x()).isEqualTo(0.6);
        assertThat(path.getLast().y()).isEqualTo(0.5);
        assertThat(liveBee(playerId).getHoney()).isEqualTo(300);
    }

    @Test
    void returningBeeKeepsItsName() {
        String playerId = "cleanup-" + UUID.randomUUID();
        String name = "Test-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        levelService.registerBee(playerId, t0);
        liveBee(playerId).setName(name);
        levelService.cleanupInactiveBees(t0 + FIVE_MINUTES + 1);

        levelService.registerBee(playerId, t0 + FIVE_MINUTES + 2);

        assertThat(liveBee(playerId).getName()).isEqualTo(name);
    }

    @Test
    void returningBeesNameWasTaken() {
        String p1 = "cleanup-" + UUID.randomUUID();
        String p2 = "cleanup-" + UUID.randomUUID();
        String name = "Test-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        levelService.registerBee(p1, t0);
        liveBee(p1).setName(name);
        levelService.cleanupInactiveBees(t0 + FIVE_MINUTES + 1);
        levelService.registerBee(p2, t0 + FIVE_MINUTES + 2);
        liveBee(p2).setName(name);

        levelService.registerBee(p1, t0 + FIVE_MINUTES + 3);

        String newName = liveBee(p1).getName();
        Set<String> otherNames = levelService.getLevel().getBees().stream()
                .filter(b -> !b.getId().equals(p1))
                .map(Bee::getName)
                .collect(Collectors.toSet());
        assertThat(newName).isNotEqualTo(name).isNotBlank();
        assertThat(otherNames).doesNotContain(newName);
    }

    @Test
    void returnedBeeTimesOutAgain() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        retireAt(playerId, 0.3, 0.7, 120, t0);
        long back = t0 + FIVE_MINUTES + 2;
        levelService.registerBee(playerId, back);
        liveBee(playerId).setHoney(180);

        levelService.cleanupInactiveBees(back + FIVE_MINUTES + 1);
        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId).doesNotContain(playerId);

        levelService.registerBee(playerId, back + FIVE_MINUTES + 2);
        assertThat(liveBee(playerId).getHoney()).isEqualTo(180);
    }

    @Test
    void restartAlsoResetsRetiredBees() {
        String playerId = "cleanup-" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        retireAt(playerId, 0.3, 0.7, 400, t0);

        levelService.restartLevel();
        levelService.registerBee(playerId, t0 + FIVE_MINUTES + 2);

        assertThat(liveBee(playerId).getHoney()).isEqualTo(0);
    }

    /**
     * Lets the bee arrive at (x, y) with the given honey, then retires it by a cleanup at
     * {@code t0 + 5 min + 1 ms}. Returns the bee as it was before it was retired.
     */
    private Bee retireAt(String playerId, double x, double y, long honey, long t0) {
        // The flight started long ago, so the bee is standing at (x, y) at t0
        levelService.setTarget(playerId, x, y, t0 - 30_000);
        Bee bee = liveBee(playerId);
        bee.setHoney(honey);
        bee.setLastActive(t0);
        Bee before = bee.toBuilder().build();
        levelService.cleanupInactiveBees(t0 + FIVE_MINUTES + 1);
        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId).doesNotContain(playerId);
        return before;
    }

    private Bee liveBee(String playerId) {
        return levelService.getLevel().getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow();
    }
}
