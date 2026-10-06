package info.unterrainer.htl.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import info.unterrainer.htl.ColorUtils;
import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Flower;
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
 */
@Slf4j
@ApplicationScoped
public class LevelService {
    private static final long INACTIVITY_TIMEOUT_MILLIS = 60_000;

    private Level currentLevel;
    private final Map<String, Bee> bees = new HashMap<>();

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
            String baseColor = pickColor();
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
        currentLevel = Level.builder().flowers(flowers).bees(new ArrayList<>(bees.values())).build();
    }

    public Bee registerBee(String id) {
        BeeRegistration registration = addBeeIfAbsent(id);
        if (registration.created())
            publishLevel();
        return registration.bee();
    }

    private synchronized BeeRegistration addBeeIfAbsent(String id) {
        Bee existing = bees.get(id);
        if (existing != null)
            return new BeeRegistration(existing, false);

        String baseName = ColorUtils.pickRandomBaseColor();
        String beeColor = ColorUtils.generatePetalColors(baseName, 1).getFirst();

        Bee bee = Bee.builder()
                .id(id)
                .x(Math.random())
                .y(Math.random())
                .targetX(Math.random())
                .targetY(Math.random())
                .color(beeColor)
                .lastActive(System.currentTimeMillis())
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
        Bee bee = registerBee(id);
        return snapshotFor(bee.getId());
    }

    private synchronized Level snapshotFor(String beeId) {
        return copyLevel(beeId);
    }

    /**
     * Deep copy of the level with the current bees. Must be called while holding the lock.
     */
    private Level copyLevel(String yourBeeId) {
        List<Flower> flowers = currentLevel.getFlowers().stream()
                .map(f -> f.toBuilder().build())
                .toList();
        List<Bee> beeCopies = bees.values().stream()
                .map(b -> b.toBuilder().build())
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
        registerBee(playerId);
        updateTarget(playerId, x, y);
        publishLevel();
    }

    private synchronized void updateTarget(String playerId, double x, double y) {
        // Re-checks under the lock in case the bee vanished between registerBee and this call
        Bee bee = addBeeIfAbsent(playerId).bee();
        bee.setTargetX(x);
        bee.setTargetY(y);
        bee.setLastActive(System.currentTimeMillis());
    }

    public synchronized double harvest(String flowerId) {
        Optional<Flower> fOpt = currentLevel.getFlowers().stream()
                .filter(f -> f.getId().equals(flowerId))
                .findFirst();

        if (fOpt.isPresent()) {
            Flower f = fOpt.get();
            double fill = f.getFill();
            if (fill <= 0.1)
                return 0;

            // Treat fill as radius, yield proportional to area (r²)
            double collected = Math.pow(fill, 2) * 100.0;

            // Empty after harvest
            f.setFill(0);
            return collected;
        }
        return 0;
    }


    private String pickColor() {
        String[] colors = {
                "pink", "lightblue", "violet", "lightyellow", "plum", "salmon",
                "lightgreen", "blue", "ivory", "salmon", "red", "mediumvioletred",
                "orangered", "darkorange", "orange", "gold", "khaki", "thistle",
                "mediumslateblue", "palegreen"
        };
        return colors[new Random().nextInt(colors.length)];
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
        msg.put("level", copyLevel(null));
        return msg;
    }
}
