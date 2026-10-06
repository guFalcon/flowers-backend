package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.HarvestResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceHarvestTest {

    private static final long T0 = 1_000_000;
    private static final double SIZE = 0.1;
    // Harvest radius of a flower of SIZE: 0.175 × 0.1 play-area heights
    private static final double RADIUS = 0.0175;

    @Inject
    LevelService levelService;

    private Flower flower;
    private Flower otherFlower;

    @BeforeEach
    void setUp() {
        levelService.restartLevel();
        List<Flower> flowers = levelService.getLevel().getFlowers();
        // Move every flower out of the play area, then place the two used by the tests
        flowers.forEach(f -> place(f, -10, -10, 0));
        flower = flowers.get(0);
        otherFlower = flowers.get(1);
        place(flower, 0.5, 0.5, 0.5);
    }

    @Test
    void harvestingAFilledFlowerAfterArrivingYieldsHoney() {
        String playerId = arrivedAt(0.5, 0.5);
        bee(playerId).setHoney(100);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(flower.getId(), 13, 113));
        assertThat(flower.getFill()).isZero();
        assertThat(bee(playerId).getHoney()).isEqualTo(113);
    }

    @Test
    void aFullFlowerYieldsFiftyMicrolitres() {
        flower.setFill(1.0);
        String playerId = arrivedAt(0.5, 0.5);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(flower.getId(), 50, 50));
    }

    @Test
    void theSmallestHarvestableFillYieldsOneMicrolitre() {
        flower.setFill(0.101);
        String playerId = arrivedAt(0.5, 0.5);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(flower.getId(), 1, 1));
        assertThat(flower.getFill()).isZero();
    }

    @Test
    void harvestingWhileStillInFlightYieldsNothing() {
        String playerId = arrivedAt(0.1, 0.5);
        // 0.4 units → 2 s flight, harvest after 1 s
        levelService.setTarget(playerId, 0.5, 0.5, T0);

        HarvestResult result = levelService.harvest(playerId, T0 + 1_000).orElseThrow();

        assertThat(result.gained()).isZero();
        assertThat(result.total()).isZero();
        assertThat(flower.getFill()).isEqualTo(0.5);
    }

    @Test
    void harvestingTwoHundredMillisecondsEarlySucceeds() {
        String playerId = arrivedAt(0.1, 0.5);
        levelService.setTarget(playerId, 0.5, 0.5, T0);

        HarvestResult result = levelService.harvest(playerId, T0 + 1_800).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(flower.getId(), 13, 13));
        assertThat(flower.getFill()).isZero();
    }

    @Test
    void harvestingSixHundredMillisecondsEarlyYieldsNothing() {
        String playerId = arrivedAt(0.1, 0.5);
        levelService.setTarget(playerId, 0.5, 0.5, T0);

        HarvestResult result = levelService.harvest(playerId, T0 + 1_400).orElseThrow();

        assertThat(result.gained()).isZero();
        assertThat(flower.getFill()).isEqualTo(0.5);
    }

    @Test
    void beeOutsideEveryHarvestRadiusYieldsNothing() {
        // Just outside the radius vertically; horizontally the radius covers 16/9 as much width
        String playerId = arrivedAt(0.5, 0.5 + RADIUS * 1.01);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(null, 0, 0));
        assertThat(flower.getFill()).isEqualTo(0.5);
    }

    @Test
    void horizontalDistanceIsMeasuredInPlayAreaHeights() {
        // 0.03 widths = 0.016875 heights, inside the radius of 0.0175 heights
        String playerId = arrivedAt(0.53, 0.5);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result.flowerId()).isEqualTo(flower.getId());
        assertThat(result.gained()).isEqualTo(13);
    }

    @Test
    void harvestingAnAlmostEmptyFlowerYieldsNothing() {
        flower.setFill(0.1);
        String playerId = arrivedAt(0.5, 0.5);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result).isEqualTo(new HarvestResult(flower.getId(), 0, 0));
        assertThat(flower.getFill()).isEqualTo(0.1);
    }

    @Test
    void theClosestOfTwoFlowersIsHarvested() {
        place(otherFlower, 0.5, 0.51, 0.8);
        String playerId = arrivedAt(0.5, 0.508);

        HarvestResult result = levelService.harvest(playerId, T0).orElseThrow();

        assertThat(result.flowerId()).isEqualTo(otherFlower.getId());
        assertThat(result.gained()).isEqualTo(32);
        assertThat(flower.getFill()).isEqualTo(0.5);
        assertThat(otherFlower.getFill()).isZero();
    }

    @Test
    void unknownBeeIsNotHarvestedAndNotCreated() {
        String playerId = "harvest-" + UUID.randomUUID();

        assertThat(levelService.harvest(playerId, T0)).isEmpty();
        assertThat(levelService.getLevel().getBees()).noneMatch(b -> b.getId().equals(playerId));
    }

    @Test
    void harvestMarksTheBeeAsActive() {
        String playerId = arrivedAt(0.5, 0.5);

        levelService.harvest(playerId, T0 + 30_000);

        assertThat(bee(playerId).getLastActive()).isEqualTo(T0 + 30_000);
    }

    // A bee that has arrived at (x, y) before T0
    private String arrivedAt(double x, double y) {
        String playerId = "harvest-" + UUID.randomUUID();
        levelService.setTarget(playerId, x, y, T0 - 10_000);
        return playerId;
    }

    private Bee bee(String playerId) {
        return levelService.getLevel().getBees().stream()
                .filter(b -> b.getId().equals(playerId))
                .findFirst().orElseThrow();
    }

    private static void place(Flower f, double x, double y, double fill) {
        f.setX(x);
        f.setY(y);
        f.setSize(SIZE);
        f.setFill(fill);
    }
}
