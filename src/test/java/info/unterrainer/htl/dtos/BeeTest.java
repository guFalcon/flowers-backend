package info.unterrainer.htl.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class BeeTest {

    @Test
    void oneKeyframePathStandsStill() {
        Bee bee = withPath(new PathKeyframe(1_000, 0.3, 0.7));

        assertPosition(bee.positionAt(0), 0.3, 0.7);
        assertPosition(bee.positionAt(1_000), 0.3, 0.7);
        assertPosition(bee.positionAt(10_000), 0.3, 0.7);
        assertThat(bee.arrivalTime()).isEqualTo(1_000);
    }

    @Test
    void twoKeyframesAreInterpolatedLinearly() {
        Bee bee = withPath(new PathKeyframe(1_000, 0.2, 0.5), new PathKeyframe(3_000, 0.6, 0.9));

        assertPosition(bee.positionAt(500), 0.2, 0.5);
        assertPosition(bee.positionAt(1_000), 0.2, 0.5);
        assertPosition(bee.positionAt(2_000), 0.4, 0.7);
        assertPosition(bee.positionAt(3_000), 0.6, 0.9);
        assertPosition(bee.positionAt(10_000), 0.6, 0.9);
        assertThat(bee.arrivalTime()).isEqualTo(3_000);
    }

    @Test
    void multiSegmentPathIsInterpolatedPerSegment() {
        Bee bee = withPath(
                new PathKeyframe(0, 0.2, 0.5),
                new PathKeyframe(1_000, 0.3, 0.5),
                new PathKeyframe(5_000, 0.5, 0.5),
                new PathKeyframe(6_000, 0.6, 0.5));

        assertPosition(bee.positionAt(500), 0.25, 0.5);
        assertPosition(bee.positionAt(1_000), 0.3, 0.5);
        assertPosition(bee.positionAt(3_000), 0.4, 0.5);
        assertPosition(bee.positionAt(5_500), 0.55, 0.5);
        assertPosition(bee.positionAt(6_000), 0.6, 0.5);
        assertThat(bee.arrivalTime()).isEqualTo(6_000);
    }

    @Test
    void pathIsImmutable() {
        Bee bee = withPath(new PathKeyframe(0, 0.2, 0.5));

        assertThat(bee.getPath()).isUnmodifiable();
    }

    private static Bee withPath(PathKeyframe... keyframes) {
        Bee bee = Bee.builder().build();
        bee.setPath(List.of(keyframes));
        return bee;
    }

    private static void assertPosition(Bee.Position position, double x, double y) {
        assertThat(position.x()).isCloseTo(x, within(1e-9));
        assertThat(position.y()).isCloseTo(y, within(1e-9));
    }
}
