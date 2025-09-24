package info.unterrainer.htl.services;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.dtos.Level;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@ApplicationScoped
public class LevelService {
    private Level currentLevel;

    public LevelService() {
        restartLevel();
    }

    public Level getLevel() {
        return currentLevel;
    }

    public void restartLevel() {
        List<Flower> flowers = new ArrayList<>();
        for (int i = 0; i < 6 + (int)(Math.random()*6); i++) {
            flowers.add(Flower.builder().id("flower-" + i).x(Math.random()).y(Math.random()).size(0.08 + Math.random() * 0.05).petals(5 + (int)(Math.random()*5)).color(pickColor()).fill(Math.random()).build());
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
        String[] colors = { "pink", "lightblue", "violet", "lightyellow", "plum", "salmon", "lightgreen", "blue", "ivory", "salmon", "red", "mediumvioletred", "orangered", "darkorange", "orange", "gold", "khaki", "thistle", "mediumslateblue", "palegreen" };
        return colors[new Random().nextInt(colors.length)];
    }
}
