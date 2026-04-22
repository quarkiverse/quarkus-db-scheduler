package io.quarkiverse.db.scheduler.test;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.QuarkusUnitTest;

public class DbSchedulerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Jobs.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=postgresql\n"
                                    + "quarkus.datasource.devservices.init-script-path=init-pg.sql\n"
                                    + "quarkus.db-scheduler.polling-interval=1s\n"),
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

    @Inject
    Scheduler scheduler;

    @Test
    public void testScheduledMethodExecutes() {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> Jobs.COUNTER.get() > 0);
    }

    @Test
    public void testSchedulerIsStarted() {
        assert scheduler.isStarted();
        assert scheduler.isRunning();
    }

    public static class Jobs {
        static final AtomicInteger COUNTER = new AtomicInteger();

        @Scheduled(every = "1s", identity = "test-job")
        void run() {
            COUNTER.incrementAndGet();
        }
    }
}
