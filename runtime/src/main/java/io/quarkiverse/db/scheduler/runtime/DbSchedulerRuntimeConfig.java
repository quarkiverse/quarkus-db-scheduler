package io.quarkiverse.db.scheduler.runtime;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.db-scheduler")
public interface DbSchedulerRuntimeConfig {

    /**
     * Number of threads used by the scheduler.
     */
    @WithDefault("10")
    int threadCount();

    /**
     * How often the scheduler checks the database for due executions.
     */
    @WithDefault("10s")
    Duration pollingInterval();

    /**
     * How often the scheduler updates its heartbeat timestamp in the database.
     */
    @WithDefault("5m")
    Duration heartbeatInterval();

    /**
     * Maximum time to wait for running tasks to complete during shutdown.
     */
    @WithDefault("10s")
    Duration shutdownMaxWait();

    /**
     * If set to {@code true}, the scheduler will trigger an immediate check for due executions when a task is scheduled.
     */
    @WithDefault("false")
    boolean enableImmediateExecution();

    /**
     * Always persist timestamps in UTC. Recommended for MySQL and MariaDB.
     */
    @WithDefault("false")
    boolean alwaysPersistTimestampInUtc();
}
