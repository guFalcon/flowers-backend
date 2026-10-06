package info.unterrainer.htl.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Bee;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

@QuarkusTest
class LevelServiceCleanupTest {

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;

    @Test
    void beesInactiveForMoreThanSixtySecondsAreRemovedAndLevelIsPublished() throws InterruptedException {
        String p1 = "cleanup-" + UUID.randomUUID();
        String p2 = "cleanup-" + UUID.randomUUID();

        levelService.registerBee(p1);
        Thread.sleep(5);
        long t0 = System.currentTimeMillis();
        Thread.sleep(5);
        levelService.registerBee(p2);

        List<Object> events = new CopyOnWriteArrayList<>();
        Cancellable subscription = eventBus.eventStream().subscribe().with(events::add);
        try {
            // p1 was last active before t0 (> 60 s ago from "now"), p2 after t0 (< 60 s ago)
            levelService.cleanupInactiveBees(t0 + 60_001);
        } finally {
            subscription.cancel();
        }

        assertThat(levelService.getLevel().getBees()).extracting(Bee::getId)
                .doesNotContain(p1)
                .contains(p2);
        assertThat(events)
                .filteredOn(e -> e instanceof Map<?, ?> m && "level-update".equals(m.get("type")))
                .hasSize(1);
    }
}
