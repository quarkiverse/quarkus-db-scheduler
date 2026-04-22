package io.quarkiverse.db.scheduler.it;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class DbSchedulerResourceTest {

    @Test
    public void testSchedulerExecutes() {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> given()
                        .when().get("/db-scheduler/count")
                        .then()
                        .statusCode(200)
                        .body(greaterThan("0")));
    }
}
