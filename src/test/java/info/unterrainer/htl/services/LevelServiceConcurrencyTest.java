package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.htl.dtos.Bee;
import info.unterrainer.htl.dtos.Flower;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceConcurrencyTest {

    private static final int THREADS = 16;
    private static final int PLAYERS = 200;
    private static final int OPERATIONS = 5_000;

    @Inject
    LevelService levelService;
    @Inject
    ObjectMapper mapper;

    @Test
    void requestsAndScheduledJobsRunConcurrentlyWithoutErrorsOrLostBees() throws Exception {
        List<String> players = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++)
            players.add("concurrency-" + UUID.randomUUID());

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> jobFailure = new AtomicReference<>();
        Thread jobs = new Thread(() -> {
            try {
                while (running.get()) {
                    levelService.fillFlowers();
                    levelService.cleanupInactiveBees();
                    levelService.publishLevel();
                    Thread.sleep(1);
                }
            } catch (Throwable t) {
                jobFailure.set(t);
            }
        });
        jobs.start();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < OPERATIONS; i++) {
                // Round 0 joins every player; later rounds cycle through the other operations
                String player = players.get(i % PLAYERS);
                int round = i / PLAYERS;
                int index = i;
                futures.add(executor.submit(() -> {
                    runOperation(round, index, player);
                    return null;
                }));
            }
            for (Future<?> future : futures)
                future.get(30, TimeUnit.SECONDS);
        } finally {
            running.set(false);
            jobs.join(10_000);
            executor.shutdownNow();
        }

        assertThat(jobFailure.get()).isNull();
        Set<String> beeIds = levelService.getLevelForPlayer(players.getFirst()).getBees().stream()
                .map(Bee::getId)
                .collect(Collectors.toSet());
        assertThat(beeIds).containsAll(players);
    }

    @Test
    void harvestAndGrowthDoNotInterleave() throws Exception {
        levelService.restartLevel();
        Flower flower = levelService.getLevel().getFlowers().getFirst();
        String playerId = "concurrency-" + UUID.randomUUID();
        // Arrived on the flower's centre long ago, so every harvest below is judged by fill alone
        LevelServiceTestSupport.placeArrived(levelService, playerId, flower.getX(), flower.getY());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2_000; i++) {
                flower.setFill(0.5);
                CountDownLatch start = new CountDownLatch(1);
                Future<Long> harvest = executor.submit(() -> {
                    start.await();
                    return levelService.harvest(playerId).orElseThrow().gained();
                });
                Future<?> growth = executor.submit(() -> {
                    start.await();
                    levelService.fillFlowers();
                    return null;
                });
                start.countDown();
                assertThat(harvest.get(5, TimeUnit.SECONDS)).isPositive();
                growth.get(5, TimeUnit.SECONDS);

                // 0: growth ran first, then the harvest emptied the flower; rate: harvest first, then growth
                assertThat(flower.getFill()).isIn(0.0, flower.getRate());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void runOperation(int round, int index, String player) throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (round % 5) {
            case 0 -> levelService.registerBee(player);
            case 1 -> levelService.setTarget(player, random.nextDouble(), random.nextDouble());
            case 2 -> levelService.harvest(player);
            // Serialise like Jackson does in GameResource, outside the service
            case 3 -> mapper.writeValueAsString(levelService.getLevelForPlayer(player));
            default -> {
                if (index % 50 == 4)
                    levelService.restartLevel();
                else
                    levelService.registerBee(player);
            }
        }
    }
}
