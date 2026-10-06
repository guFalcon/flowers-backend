package info.unterrainer.htl.resources;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.htl.dtos.Flower;
import info.unterrainer.htl.services.EventBusService;
import info.unterrainer.htl.services.LevelService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;

@QuarkusTest
class AdminAccessTest {

    // %test.flowers.admin-token in application.properties
    private static final String TOKEN = "test-admin-token";

    @Inject
    LevelService levelService;
    @Inject
    EventBusService eventBus;

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private Cancellable subscription;

    @BeforeEach
    void setUp() {
        events.clear();
        subscription = eventBus.eventStream().subscribe().with(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
    }

    @Test
    void restartWithCorrectTokenIsPerformed() {
        List<Flower> previousFlowers = levelService.getLevel().getFlowers();

        restart(given().header("X-Admin-Token", TOKEN)).then().statusCode(200);

        assertThat(levelService.getLevel().getFlowers()).isNotSameAs(previousFlowers);
        assertThat(restartEvents()).hasSize(1);
    }

    @Test
    void restartWithoutTokenIsRefused() {
        assertRefused(given());
    }

    @Test
    void restartWithWrongTokenIsRefused() {
        assertRefused(given().header("X-Admin-Token", "true"));
    }

    @Test
    void preflightAllowsTheAdminTokenHeaderForTheFrontend() {
        String allowedHeaders = given()
                .header("Origin", "https://flowers.htl.dev")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-admin-token")
                .when().options("/api/admin/restart")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", "https://flowers.htl.dev")
                .extract().header("Access-Control-Allow-Headers");

        assertThat(allowedHeaders.toLowerCase()).contains("x-admin-token");
    }

    private void assertRefused(RequestSpecification request) {
        List<Flower> previousFlowers = levelService.getLevel().getFlowers();

        restart(request).then().statusCode(403);

        assertThat(levelService.getLevel().getFlowers()).isSameAs(previousFlowers);
        assertThat(restartEvents()).isEmpty();
    }

    private static io.restassured.response.Response restart(RequestSpecification request) {
        return request.contentType(ContentType.JSON).when().post("/api/admin/restart");
    }

    private List<Object> restartEvents() {
        return events.stream()
                .filter(e -> e instanceof Map<?, ?> m && "levelRestarted".equals(m.get("type")))
                .toList();
    }
}
