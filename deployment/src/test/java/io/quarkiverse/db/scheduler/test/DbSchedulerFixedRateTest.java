package io.quarkiverse.db.scheduler.test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.test.QuarkusUnitTest;

public class DbSchedulerFixedRateTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Jobs.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=postgresql\n"
                                    + "quarkus.datasource.devservices.init-script-path=init-pg.sql\n"
                                    + "quarkus.db-scheduler.polling-interval=2s\n"),
                            "application.properties")
                    .addAsResource(new StringAsset(
                            "create table if not exists scheduled_tasks (\n"
                                    + "  task_name text not null,\n"
                                    + "  task_instance text not null,\n"
                                    + "  task_data bytea,\n"
                                    + "  execution_time timestamp with time zone not null,\n"
                                    + "  picked boolean not null,\n"
                                    + "  picked_by text,\n"
                                    + "  last_success timestamp with time zone,\n"
                                    + "  last_failure timestamp with time zone,\n"
                                    + "  consecutive_failures int,\n"
                                    + "  last_heartbeat timestamp with time zone,\n"
                                    + "  version bigint not null,\n"
                                    + "  primary key (task_name, task_instance)\n"
                                    + ");\n"),
                            "init-pg.sql"));

    @Test
    public void testFixedRateNotDoubled() {
        // With polling-interval=2s and every=2s, a FixedDelay schedule would
        // fire every ~4s (completion + 2s lands just past the next poll),
        // yielding only ~3 executions in 9s (at T=0, T=4, T=8).
        // A correct fixed-rate schedule should fire every ~2s,
        // yielding ~5 executions in 9s (at T=0, T=2, T=4, T=6, T=8).
        await().atMost(Duration.ofSeconds(9))
                .untilAsserted(() -> assertTrue(Jobs.COUNTER.get() >= 4,
                        "Expected at least 4 executions in 9s with every=2s, but got " + Jobs.COUNTER.get()));
    }

    public static class Jobs {
        static final AtomicInteger COUNTER = new AtomicInteger();

        @Scheduled(every = "2s", identity = "fixed-rate-job")
        void run() throws InterruptedException {
            COUNTER.incrementAndGet();
            // Sleep to push completion well past the next poll boundary.
            // With FixedDelay, next = completionTime + 2s = pickupTime + 0.5 + 2 = pickupTime + 2.5s.
            // The poll at pickupTime + 2s misses it, so effective rate doubles to ~4s.
            Thread.sleep(500);
        }
    }
}
