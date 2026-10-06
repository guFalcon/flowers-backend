package info.unterrainer.htl.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Cloud;
import info.unterrainer.htl.dtos.WindKeyframe;

/**
 * The clouds and the wind schedule that drives them. Every cloud position is computed from the cloud's
 * anchor (its position at {@code t}) and the wind keyframes, exactly as the frontend does in
 * {@code clouds.js}, so server and clients see the clouds at the same place.
 * <p>
 * Not thread-safe: only used under the lock of {@link LevelService}.
 */
public class Weather {
    // Play-area width per height (aspect 9:16), converts horizontal distances to heights
    static final double WIDTH_PER_HEIGHT = 9.0 / 16.0;

    static final int MIN_CLOUDS = 4;
    static final int MAX_CLOUDS = 6;
    // Cloud diameter and drift speed in play-area heights (per second)
    static final double CLOUD_SIZE = 0.10;
    static final double MIN_CLOUD_SPEED = 0.02;
    static final double MAX_CLOUD_SPEED = 0.05;
    static final double MAX_DRIFT = Math.toRadians(20);

    // A turn starts every 15–30 s, takes 5–8 s in steps of at most 0.5 s and turns by at most ±60°
    static final long MIN_TURN_GAP_MILLIS = 15_000;
    static final long MAX_TURN_GAP_MILLIS = 30_000;
    static final long MIN_TURN_MILLIS = 5_000;
    static final long MAX_TURN_MILLIS = 8_000;
    static final long MAX_TURN_STEP_MILLIS = 500;
    static final double MAX_TURN = Math.toRadians(60);

    // The schedule is extended to 90 s ahead whenever it reaches less than 75 s ahead
    static final long HORIZON_REFRESH_MILLIS = 75_000;
    static final long HORIZON_TARGET_MILLIS = 90_000;

    private List<Cloud> clouds;
    private final List<WindKeyframe> wind;
    // Start of the next turn to append to the schedule
    private long nextTurnStart;

    Weather(List<Cloud> clouds, List<WindKeyframe> wind, long nextTurnStart) {
        if (wind.isEmpty())
            throw new IllegalArgumentException("The wind schedule needs at least one keyframe");
        this.clouds = new ArrayList<>(clouds);
        this.wind = new ArrayList<>(wind);
        this.nextTurnStart = nextTurnStart;
    }

    /**
     * Random clouds and a random wind schedule reaching at least 90 s past {@code now}.
     */
    public static Weather generate(long now, Random random) {
        Weather weather = new Weather(randomClouds(now, random),
                List.of(new WindKeyframe(now, random.nextDouble() * 2 * Math.PI)),
                now + randomTurnGap(random));
        weather.ensureHorizon(now, random);
        return weather;
    }

    /**
     * No clouds at all, so flights are never slowed. Meant for tests.
     */
    public static Weather none() {
        return new Weather(List.of(), List.of(new WindKeyframe(0, 0)), 0);
    }

    /**
     * Replaces the clouds by a newly generated set; the wind is kept.
     */
    public void regenerateClouds(long now, Random random) {
        clouds = randomClouds(now, random);
        ensureHorizon(now, random);
    }

    /**
     * Re-anchors every cloud to {@code now}, drops the wind keyframes that are over and extends the
     * schedule so it reaches at least 75 s past {@code now}. Future cloud positions do not change.
     */
    public void ensureHorizon(long now, Random random) {
        for (Cloud c : clouds) {
            Bee.Position position = cloudPositionAt(c, now);
            c.setX(position.x());
            c.setY(position.y());
            c.setT(now);
        }

        // Keep the keyframe in effect at now, starting at now; before the first keyframe its angle holds anyway
        int current = 0;
        while (current + 1 < wind.size() && wind.get(current + 1).t() <= now)
            current++;
        wind.subList(0, current).clear();
        wind.set(0, new WindKeyframe(now, wind.getFirst().angle()));

        if (wind.getLast().t() >= now + HORIZON_REFRESH_MILLIS)
            return;
        if (nextTurnStart <= wind.getLast().t())
            nextTurnStart = wind.getLast().t() + randomTurnGap(random);
        while (wind.getLast().t() < now + HORIZON_TARGET_MILLIS)
            appendTurn(random);
    }

    /**
     * Position of the cloud at time {@code t}: its anchor moved along the wind schedule, then wrapped
     * around the play area.
     */
    public Bee.Position cloudPositionAt(Cloud c, long t) {
        double[] d = displacement(c, Math.min(c.getT(), t), Math.max(c.getT(), t));
        double sign = t >= c.getT() ? 1 : -1;
        double radius = c.getSize() / 2;
        double x = wrap(c.getX() + sign * d[0] / WIDTH_PER_HEIGHT, radius / WIDTH_PER_HEIGHT);
        double y = wrap(c.getY() + sign * d[1], radius);
        return new Bee.Position(x, y);
    }

    /**
     * Whether (x, y) lies within a cloud at time {@code t}; distances are measured in play-area heights.
     */
    public boolean inCloud(double x, double y, long t) {
        for (Cloud c : clouds) {
            Bee.Position p = cloudPositionAt(c, t);
            if (Math.hypot((x - p.x()) * WIDTH_PER_HEIGHT, y - p.y()) <= c.getSize() / 2)
                return true;
        }
        return false;
    }

    /**
     * Copies of the clouds, safe to hand out of the lock.
     */
    public List<Cloud> copyClouds() {
        return clouds.stream().map(c -> c.toBuilder().build()).toList();
    }

    public List<WindKeyframe> copyWind() {
        return List.copyOf(wind);
    }

    // Movement in play-area heights (dx, dy) between from and to (from <= to)
    private double[] displacement(Cloud c, long from, long to) {
        double dx = 0;
        double dy = 0;
        for (int i = 0; i < wind.size(); i++) {
            // The first angle also holds before its keyframe, the last one after it
            long start = i == 0 ? Long.MIN_VALUE : wind.get(i).t();
            long end = i + 1 < wind.size() ? wind.get(i + 1).t() : Long.MAX_VALUE;
            long overlap = Math.min(to, end) - Math.max(from, start);
            if (overlap <= 0)
                continue;
            double distance = c.getSpeed() * overlap / 1000.0;
            double angle = wind.get(i).angle() + c.getDrift();
            dx += distance * Math.cos(angle);
            dy += distance * Math.sin(angle);
        }
        return new double[] { dx, dy };
    }

    // A cloud that has completely left the range [0, 1] re-enters on the opposite side
    private static double wrap(double p, double radius) {
        double min = -radius;
        double span = 1 + 2 * radius;
        return ((p - min) % span + span) % span + min;
    }

    // One keyframe at the turn start (old angle), then equal steps up to the new angle
    private void appendTurn(Random random) {
        long duration = MIN_TURN_MILLIS + (long) (random.nextDouble() * (MAX_TURN_MILLIS - MIN_TURN_MILLIS));
        int steps = (int) Math.ceil((double) duration / MAX_TURN_STEP_MILLIS);
        double delta = (random.nextDouble() * 2 - 1) * MAX_TURN;
        double startAngle = wind.getLast().angle();
        for (int i = 0; i <= steps; i++)
            wind.add(new WindKeyframe(nextTurnStart + Math.round((double) duration * i / steps),
                    startAngle + delta * i / steps));
        nextTurnStart += randomTurnGap(random);
    }

    private static long randomTurnGap(Random random) {
        return MIN_TURN_GAP_MILLIS + (long) (random.nextDouble() * (MAX_TURN_GAP_MILLIS - MIN_TURN_GAP_MILLIS));
    }

    private static List<Cloud> randomClouds(long now, Random random) {
        int count = MIN_CLOUDS + random.nextInt(MAX_CLOUDS - MIN_CLOUDS + 1);
        List<Cloud> clouds = new ArrayList<>();
        for (int i = 0; i < count; i++)
            clouds.add(Cloud.builder()
                    .id("cloud-" + i)
                    .x(random.nextDouble())
                    .y(random.nextDouble())
                    .t(now)
                    .size(CLOUD_SIZE)
                    .speed(MIN_CLOUD_SPEED + random.nextDouble() * (MAX_CLOUD_SPEED - MIN_CLOUD_SPEED))
                    .drift((random.nextDouble() * 2 - 1) * MAX_DRIFT)
                    .build());
        return clouds;
    }
}
