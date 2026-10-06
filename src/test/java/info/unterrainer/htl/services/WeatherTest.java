package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Cloud;
import info.unterrainer.htl.dtos.WindKeyframe;

class WeatherTest {

    private static final long T0 = 1_000_000;
    private final Random random = new Random();

    @RepeatedTest(20)
    void generatedCloudsAreInRange() {
        List<Cloud> clouds = Weather.generate(T0, random).copyClouds();

        assertThat(clouds).hasSizeBetween(4, 6);
        assertThat(clouds).allSatisfy(c -> {
            assertThat(c.getSize()).isEqualTo(0.10);
            assertThat(c.getSpeed()).isBetween(0.02, 0.05);
            assertThat(c.getDrift()).isBetween(-Math.toRadians(20), Math.toRadians(20));
            assertThat(c.getT()).isEqualTo(T0);
        });
    }

    @Test
    void straightDrift() {
        Cloud cloud = cloud(0.5, 0.5, 0.04);
        Weather weather = constantWind(cloud, Math.PI / 2);

        assertPosition(weather.cloudPositionAt(cloud, T0 + 10_000), 0.5, 0.9);
    }

    @Test
    void horizontalDriftIsConvertedToWidths() {
        Cloud cloud = cloud(0.2, 0.5, 0.045);
        Weather weather = constantWind(cloud, 0);

        // 0.45 heights = 0.8 widths
        assertPosition(weather.cloudPositionAt(cloud, T0 + 10_000), 1.0, 0.5);
    }

    @Test
    void cloudWrapsAroundAtTheBottom() {
        Cloud cloud = cloud(0.5, 1.0, 0.04);
        Weather weather = constantWind(cloud, Math.PI / 2);

        // 1.0 + 0.04 = 1.04 is still inside the wrap range
        assertPosition(weather.cloudPositionAt(cloud, T0 + 1_000), 0.5, 1.04);
        // 1.0 + 0.06 = 1.06 has passed 1.05 by 0.01 and continues from -0.05
        assertPosition(weather.cloudPositionAt(cloud, T0 + 1_500), 0.5, -0.04);
    }

    @Test
    void cloudFollowsTheWindPieceByPiece() {
        Cloud cloud = cloud(0.5, 0.5, 0.04);
        Weather weather = new Weather(List.of(cloud),
                List.of(new WindKeyframe(T0, Math.PI / 2), new WindKeyframe(T0 + 5_000, -Math.PI / 2)),
                Long.MAX_VALUE);

        assertPosition(weather.cloudPositionAt(cloud, T0 + 5_000), 0.5, 0.7);
        assertPosition(weather.cloudPositionAt(cloud, T0 + 7_500), 0.5, 0.6);
    }

    @Test
    void inCloudMeasuresInPlayAreaHeights() {
        Cloud cloud = cloud(0.4, 0.5, 0);
        Weather weather = constantWind(cloud, 0);

        assertThat(weather.inCloud(0.4, 0.549, T0)).isTrue();
        assertThat(weather.inCloud(0.4, 0.551, T0)).isFalse();
        // Radius 0.05 heights = 0.0889 widths
        assertThat(weather.inCloud(0.488, 0.5, T0)).isTrue();
        assertThat(weather.inCloud(0.49, 0.5, T0)).isFalse();
    }

    @RepeatedTest(20)
    void turnsAreGradualAndSpacedApart() {
        Weather weather = Weather.generate(T0, random);
        // Extend the schedule far ahead by moving the time forward a few times
        List<WindKeyframe> all = new ArrayList<>(weather.copyWind());
        for (long now = T0 + 60_000; now <= T0 + 600_000; now += 60_000) {
            weather.ensureHorizon(now, random);
            for (WindKeyframe k : weather.copyWind())
                if (k.t() > all.getLast().t())
                    all.add(k);
        }

        List<List<WindKeyframe>> turns = turns(all);
        assertThat(turns.size()).isGreaterThan(10);
        for (int i = 0; i < turns.size(); i++) {
            List<WindKeyframe> turn = turns.get(i);
            long duration = turn.getLast().t() - turn.getFirst().t();
            assertThat(duration).isBetween(5_000L, 8_000L);
            assertThat(Math.abs(turn.getLast().angle() - turn.getFirst().angle()))
                    .isLessThanOrEqualTo(Math.toRadians(60) + 1e-9);
            double step = turn.get(1).angle() - turn.get(0).angle();
            for (int j = 1; j < turn.size(); j++) {
                assertThat(turn.get(j).t() - turn.get(j - 1).t()).isLessThanOrEqualTo(500);
                assertThat(turn.get(j).angle() - turn.get(j - 1).angle()).isCloseTo(step, within(1e-9));
            }
            if (i > 0)
                assertThat(turn.getFirst().t() - turns.get(i - 1).getFirst().t()).isBetween(15_000L, 30_000L);
        }
    }

    @RepeatedTest(20)
    void scheduleReachesSixtySecondsAhead() {
        Weather weather = Weather.generate(T0, random);

        for (long now = T0; now <= T0 + 300_000; now += 3_000) {
            weather.ensureHorizon(now, random);
            List<WindKeyframe> wind = weather.copyWind();
            assertThat(wind.getFirst().t()).isLessThanOrEqualTo(now);
            assertThat(wind.getLast().t()).isGreaterThanOrEqualTo(now + 60_000);
            assertThat(weather.copyClouds()).allSatisfy(c -> assertThat(c.getT()).isEqualTo(wind.getFirst().t()));
        }
    }

    @RepeatedTest(20)
    void reanchoringLeavesFuturePositionsUnchanged() {
        Weather weather = Weather.generate(T0, random);
        List<Cloud> before = weather.copyClouds();
        List<Bee.Position> expected = before.stream().map(c -> weather.cloudPositionAt(c, T0 + 50_000)).toList();

        weather.ensureHorizon(T0 + 20_000, random);

        List<Cloud> after = weather.copyClouds();
        assertThat(after).allSatisfy(c -> assertThat(c.getT()).isEqualTo(T0 + 20_000));
        for (int i = 0; i < after.size(); i++) {
            Bee.Position p = weather.cloudPositionAt(after.get(i), T0 + 50_000);
            assertPosition(p, expected.get(i).x(), expected.get(i).y());
        }
    }

    @Test
    void noneHasNoClouds() {
        Weather weather = Weather.none();
        weather.ensureHorizon(T0, random);

        assertThat(weather.copyClouds()).isEmpty();
        assertThat(weather.inCloud(0.5, 0.5, T0)).isFalse();
    }

    @Test
    void regeneratedCloudsKeepTheWind() {
        Weather weather = Weather.generate(T0, random);
        List<WindKeyframe> wind = weather.copyWind();

        weather.regenerateClouds(T0, random);

        assertThat(weather.copyWind()).isEqualTo(wind);
        assertThat(weather.copyClouds()).hasSizeBetween(4, 6);
    }

    // Consecutive keyframes at most 0.5 s apart belong to one turn
    private static List<List<WindKeyframe>> turns(List<WindKeyframe> wind) {
        List<List<WindKeyframe>> turns = new ArrayList<>();
        List<WindKeyframe> turn = null;
        for (int i = 1; i < wind.size(); i++) {
            if (wind.get(i).t() - wind.get(i - 1).t() <= 500) {
                if (turn == null) {
                    turn = new ArrayList<>();
                    turn.add(wind.get(i - 1));
                }
                turn.add(wind.get(i));
            } else if (turn != null) {
                turns.add(turn);
                turn = null;
            }
        }
        // The last turn may be cut off by the horizon; ignore it
        return turns;
    }

    private static Cloud cloud(double x, double y, double speed) {
        return Cloud.builder().id("c").x(x).y(y).t(T0).size(0.10).speed(speed).drift(0).build();
    }

    private static Weather constantWind(Cloud cloud, double angle) {
        return new Weather(List.of(cloud), List.of(new WindKeyframe(T0, angle)), Long.MAX_VALUE);
    }

    private static void assertPosition(Bee.Position position, double x, double y) {
        assertThat(position.x()).isCloseTo(x, within(1e-9));
        assertThat(position.y()).isCloseTo(y, within(1e-9));
    }
}
