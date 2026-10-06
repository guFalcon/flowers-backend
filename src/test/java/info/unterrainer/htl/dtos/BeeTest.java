package info.unterrainer.htl.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BeeTest {

    private final Bee bee = Bee.builder()
            .fromX(0.2)
            .fromY(0.5)
            .targetX(0.6)
            .targetY(0.9)
            .flightStart(1_000)
            .flightEnd(3_000)
            .build();

    @Test
    void positionAtFlightStartIsTheStart() {
        assertPosition(bee.positionAt(1_000), 0.2, 0.5);
    }

    @Test
    void positionHalfWayIsInterpolatedLinearly() {
        assertPosition(bee.positionAt(2_000), 0.4, 0.7);
    }

    @Test
    void positionAfterArrivalIsTheTarget() {
        assertPosition(bee.positionAt(3_000), 0.6, 0.9);
        assertPosition(bee.positionAt(10_000), 0.6, 0.9);
    }

    private static void assertPosition(Bee.Position position, double x, double y) {
        assertThat(position.x()).isCloseTo(x, within(1e-9));
        assertThat(position.y()).isCloseTo(y, within(1e-9));
    }
}
