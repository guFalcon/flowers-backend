package info.unterrainer.htl.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.unterrainer.htl.dtos.Bee;

import info.unterrainer.htl.ColorUtils;
import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.Level;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class LevelService {
    private Level currentLevel;
    private final Map<String, Bee> bees = new HashMap<>();

    @Inject
    ObjectMapper mapper;
    @Inject
    EventBusService eventBusService;

    public LevelService() {
        restartLevel();
    }

    public Level getLevel() {
        return currentLevel;
    }

    public void restartLevel() {
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

    public synchronized Bee registerBee(String id) {
        if (!bees.containsKey(id)) {
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

            publishLevel();
            return bee;
        }
        return bees.get(id);
    }

    public synchronized Level getLevelForPlayer(String id) {
        Bee bee = registerBee(id);
        return currentLevel.toBuilder().yourBeeId(bee.getId()).build();
    }

    @Scheduled(every = "10s")
    public void cleanupInactiveBees() {
        long now = System.currentTimeMillis();
        long timeout = 60_000;

        List<String> toRemove = new ArrayList<>();
        for (Bee bee : bees.values()) {
            if (now - bee.getLastActive() > timeout) {
                toRemove.add(bee.getId());
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(bees::remove);
            currentLevel.setBees(new ArrayList<>(bees.values()));
            publishLevel();
            log.info("Removed {} inactive bees", toRemove.size());
        }
    }

    public synchronized void setTarget(String playerId, double x, double y) {
        Bee bee = bees.get(playerId);
        if (bee != null) {
            bee.setTargetX(x);
            bee.setTargetY(y);
            bee.setLastActive(System.currentTimeMillis());
            publishLevel();
        } else {
            registerBee(playerId);
            setTarget(playerId, x, y);
        }
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
    public void fillFlowers() {
        for (Flower f : currentLevel.getFlowers()) {
            double newFill = Math.min(1.0, f.getFill() + f.getRate());
            f.setFill(newFill);
        }
    }

    @Scheduled(every = "3s")
    public void publishLevel() {
        try {
            currentLevel.setBees(new ArrayList<>(bees.values()));
            String json = mapper.writeValueAsString(currentLevel);
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "level-update");
            msg.put("level", json);
            eventBusService.publish(msg);
        } catch (Exception e) {
            log.error("Could not publish level update!", e);
        }
    }
}
