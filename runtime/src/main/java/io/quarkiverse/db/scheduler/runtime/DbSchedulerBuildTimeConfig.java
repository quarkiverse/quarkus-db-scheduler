package io.quarkiverse.db.scheduler.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.db-scheduler")
public interface DbSchedulerBuildTimeConfig {

    /**
     * The name of the database table used by db-scheduler.
     */
    @WithDefault("scheduled_tasks")
    String tableName();
}
