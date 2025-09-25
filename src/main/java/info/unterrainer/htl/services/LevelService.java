package info.unterrainer.htl.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.Level;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@ApplicationScoped
public class LevelService {
    private Level currentLevel;

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
        for (int i = 0; i < 6 + (int)(Math.random()*6); i++) {
            flowers.add(Flower.builder()
                    .id("flower-" + i)
                    .x(Math.random())
                    .y(Math.random())
                    .size(0.08 + Math.random() * 0.05)
                    .petals(5 + (int)(Math.random()*5))
                    .color(pickColor())
                    .fill(Math.random())
                    .rate(Math.random() * 0.1 + 0.01)
                    .build());
        }
        currentLevel = Level.builder().flowers(flowers).build();
    }

    public synchronized double harvest(String flowerId) {
        Optional<Flower> fOpt = currentLevel.getFlowers().stream()
                .filter(f -> f.getId().equals(flowerId))
                .findFirst();
        if (fOpt.isPresent()) {
            Flower f = fOpt.get();
            double collected = f.getFill();
            f.setFill(0); // empty after harvest
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
