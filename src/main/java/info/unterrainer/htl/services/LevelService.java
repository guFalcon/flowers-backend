package info.unterrainer.htl.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import info.unterrainer.htl.BeeNames;
import info.unterrainer.htl.ColorUtils;
import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.HarvestResult;
import info.unterrainer.htl.dtos.Level;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the game state. Every access to {@code currentLevel}, its flowers and {@code bees} runs under
 * the monitor of this bean ({@code synchronized}); SSE events are published after the lock has been
 * released, so a slow subscriber never blocks the game logic.
 * <p>
 * Public methods use the current time; the package-private overloads take {@code now} (epoch millis),
 * so tests can check time-dependent rules without sleeping.
 */
@Slf4j
@ApplicationScoped
public class LevelService {
    private static final long INACTIVITY_TIMEOUT_MILLIS = 60_000;
    // Same flight speed as the frontend: 5 s per unit of distance in relative coordinates, at least 0.2 s
    private static final double FLIGHT_MILLIS_PER_UNIT = 5_000;
    private static final long MIN_FLIGHT_MILLIS = 200;
    // A harvest request may arrive this early before the computed arrival (network latency)
    private static final long ARRIVAL_TOLERANCE_MILLIS = 500;
    // Radius of the drawn flower centre relative to the flower size, both in play-area heights
    private static final double HARVEST_RADIUS_PER_SIZE = 0.175;
    // Play-area width per height (aspect 9:16), converts horizontal distances to heights
    private static final double WIDTH_PER_HEIGHT = 9.0 / 16.0;
    private static final double MIN_HARVEST_FILL = 0.1;
    // Honey is counted in microlitres; a full flower yields about one crop load of a honey bee
    private static final double HONEY_PER_FULL_FLOWER = 50;

    private Level currentLevel;
    private final Map<String, Bee> bees = new HashMap<>();
    // Only used under the lock
    private final Random random = new Random();

    @Inject
    EventBusService eventBusService;

    private record BeeRegistration(Bee bee, boolean created) {
    }

    @PostConstruct
    void init() {
        restartLevel();
    }

    /**
     * Returns the live level, not a copy. Meant for tests and internal use only; clients get a
     * snapshot via {@link #getLevelForPlayer(String)}.
     */
    public synchronized Level getLevel() {
        return currentLevel;
    }

    public synchronized void restartLevel() {
    	List<Flower> flowers = new ArrayList<>();
        for (int i = 0; i < 6 + (int) (Math.random() * 6); i++) {
            int petals = 5 + (int) (Math.random() * 5);
            String baseColor = ColorUtils.pickRandomBaseColor();
            List<String> petalColors = ColorUtils.generatePetalColors(baseColor, petals);
            String stampColor = ColorUtils.pickStampColor(petalColors.get(0));

            flowers.add(Flower.builder()
                    .id("flower-" + i)
                    .x(Math.random())
                    .y(Math.random())
                    .size(0.08 + Math.random() * 0.05)
                    .petals(petals)
                    .color(baseColor)
                    .petalColors(petalColors)
                    .stampColor(stampColor)
                    .fill(Math.random())
                    .rate(Math.random() * 0.1 + 0.01)
                    .build());
        }
        // A restart starts a new round: bees keep their positions and flights, but not their honey
        bees.values().forEach(b -> b.setHoney(0));
        currentLevel = Level.builder().flowers(flowers).bees(new ArrayList<>(bees.values())).build();
    }

    public Bee registerBee(String id) {
        return registerBee(id, System.currentTimeMillis());
    }

    Bee registerBee(String id, long now) {
        BeeRegistration registration = addBeeIfAbsent(id, now);
        if (registration.created())
            publishLevel();
        return registration.bee();
    }

    private synchronized BeeRegistration addBeeIfAbsent(String id, long now) {
        Bee existing = bees.get(id);
        if (existing != null) {
            existing.setLastActive(now);
            return new BeeRegistration(existing, false);
        }

        String baseName = ColorUtils.pickRandomBaseColor();
        String beeColor = ColorUtils.generatePetalColors(baseName, 1).getFirst();
        // Picked under the lock, so two simultaneous joins cannot get the same name
        Set<String> usedNames = bees.values().stream().map(Bee::getName).collect(Collectors.toSet());
        String name = BeeNames.pick(usedNames, random);

        // A new bee stands still at a random position: no flight, target = position
        double x = Math.random();
        double y = Math.random();
        Bee bee = Bee.builder()
                .id(id)
                .x(x)
                .y(y)
                .targetX(x)
                .targetY(y)
                .fromX(x)
                .fromY(y)
                .flightStart(now)
                .flightEnd(now)
                .honey(0)
                .color(beeColor)
                .name(name)
                .lastActive(now)
                .build();

        bees.put(id, bee);

        if (currentLevel != null)
            currentLevel.setBees(new ArrayList<>(bees.values()));

        return new BeeRegistration(bee, true);
    }

    /**
     * Registers the player's bee if necessary and returns a deep copy of the level, so it can be
     * serialised outside the lock without seeing concurrent changes.
     */
    public Level getLevelForPlayer(String id) {
        return getLevelForPlayer(id, System.currentTimeMillis());
    }

    Level getLevelForPlayer(String id, long now) {
        Bee bee = registerBee(id, now);
        return snapshotFor(bee.getId(), now);
    }

    private synchronized Level snapshotFor(String beeId, long now) {
        return copyLevel(beeId, now);
    }

    /**
     * Deep copy of the level with the current bees, each placed at its position at {@code now}. Must be
     * called while holding the lock.
     */
    private Level copyLevel(String yourBeeId, long now) {
        List<Flower> flowers = currentLevel.getFlowers().stream()
                .map(f -> f.toBuilder().build())
                .toList();
        List<Bee> beeCopies = bees.values().stream()
                .map(b -> {
                    Bee.Position position = b.positionAt(now);
                    return b.toBuilder().x(position.x()).y(position.y()).build();
                })
                .toList();
        return currentLevel.toBuilder()
                .flowers(new ArrayList<>(flowers))
                .bees(new ArrayList<>(beeCopies))
                .yourBeeId(yourBeeId)
                .build();
    }

    @Scheduled(every = "10s")
    public void cleanupInactiveBees() {
        cleanupInactiveBees(System.currentTimeMillis());
    }

    void cleanupInactiveBees(long now) {
        int removed = removeInactiveBees(now);
        if (removed > 0) {
            publishLevel();
            log.info("Removed {} inactive bees", removed);
        }
    }

    private synchronized int removeInactiveBees(long now) {
        List<String> toRemove = new ArrayList<>();
        for (Bee bee : bees.values()) {
            if (now - bee.getLastActive() > INACTIVITY_TIMEOUT_MILLIS) {
                toRemove.add(bee.getId());
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(bees::remove);
            currentLevel.setBees(new ArrayList<>(bees.values()));
        }
        return toRemove.size();
    }

    public void setTarget(String playerId, double x, double y) {
        setTarget(playerId, x, y, System.currentTimeMillis());
    }

    void setTarget(String playerId, double x, double y, long now) {
        registerBee(playerId, now);
        updateTarget(playerId, x, y, now);
        publishLevel();
    }

    private synchronized void updateTarget(String playerId, double x, double y, long now) {
        // Re-checks under the lock in case the bee vanished between registerBee and this call
        Bee bee = addBeeIfAbsent(playerId, now).bee();
        // The new flight starts where the bee is now, also when an earlier flight is still under way
        Bee.Position start = bee.positionAt(now);
        double distance = Math.hypot(x - start.x(), y - start.y());
        bee.setFromX(start.x());
        bee.setFromY(start.y());
        bee.setX(start.x());
        bee.setY(start.y());
        bee.setTargetX(x);
        bee.setTargetY(y);
        bee.setFlightStart(now);
        bee.setFlightEnd(now + Math.max(Math.round(FLIGHT_MILLIS_PER_UNIT * distance), MIN_FLIGHT_MILLIS));
        bee.setLastActive(now);
    }

    /**
     * Harvests the flower under the player's bee, if the bee has arrived and the flower is filled.
     * Empty if no bee with that id exists (none is created).
     */
    public Optional<HarvestResult> harvest(String playerId) {
        return harvest(playerId, System.currentTimeMillis());
    }

    synchronized Optional<HarvestResult> harvest(String playerId, long now) {
        Bee bee = bees.get(playerId);
        if (bee == null)
            return Optional.empty();
        bee.setLastActive(now);

        boolean arrived = now >= bee.getFlightEnd() - ARRIVAL_TOLERANCE_MILLIS;
        // Within the tolerance the bee counts as arrived, so its position is the target
        Bee.Position position = arrived ? new Bee.Position(bee.getTargetX(), bee.getTargetY()) : bee.positionAt(now);
        Optional<Flower> flower = flowerAt(position);
        if (!arrived || flower.isEmpty() || flower.get().getFill() <= MIN_HARVEST_FILL)
            return Optional.of(new HarvestResult(flower.map(Flower::getId).orElse(null), 0, bee.getHoney()));

        Flower f = flower.get();
        // Treat fill as radius, yield proportional to area (r²)
        long gained = Math.round(f.getFill() * f.getFill() * HONEY_PER_FULL_FLOWER);
        f.setFill(0);
        bee.setHoney(bee.getHoney() + gained);
        return Optional.of(new HarvestResult(f.getId(), gained, bee.getHoney()));
    }

    /**
     * The flower whose centre is closest to the position, among those within their harvest radius.
     * Must be called while holding the lock.
     */
    private Optional<Flower> flowerAt(Bee.Position position) {
        Flower closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Flower f : currentLevel.getFlowers()) {
            double distance = Math.hypot((f.getX() - position.x()) * WIDTH_PER_HEIGHT, f.getY() - position.y());
            if (distance <= HARVEST_RADIUS_PER_SIZE * f.getSize() && distance < closestDistance) {
                closest = f;
                closestDistance = distance;
            }
        }
        return Optional.ofNullable(closest);
    }

    @Scheduled(every = "1s")
    public synchronized void fillFlowers() {
        for (Flower f : currentLevel.getFlowers()) {
            double newFill = Math.min(1.0, f.getFill() + f.getRate());
            f.setFill(newFill);
        }
    }

    @Scheduled(every = "3s")
    public void publishLevel() {
        try {
            eventBusService.publish(buildLevelUpdate());
        } catch (Exception e) {
            log.error("Could not publish level update!", e);
        }
    }

    /**
     * Builds the level-update event around a deep copy of the level, so it is serialised by the SSE
     * writer outside the lock without seeing concurrent changes.
     */
    private synchronized Map<String, Object> buildLevelUpdate() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "level-update");
        msg.put("level", copyLevel(null, System.currentTimeMillis()));
        return msg;
    }
}
