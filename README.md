# Quarkus Db Scheduler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.db-scheduler/quarkus-db-scheduler?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.db-scheduler/quarkus-db-scheduler-parent)

A Quarkus extension that integrates [db-scheduler](https://github.com/kagkarlsson/db-scheduler) with the Quarkus scheduler API. It provides persistent, cluster-friendly scheduling backed by a single database table.

## Features

- Full integration with `@Scheduled` annotations from `quarkus-scheduler`
- Persistent task storage using a single database table
- Cluster-safe execution - only one node picks up a given task
- Supports both cron expressions and fixed-interval schedules
- Configurable polling interval, thread pool, and heartbeat
- Pause and resume individual jobs or the entire scheduler
- Access the underlying `com.github.kagkarlsson.scheduler.Scheduler` for advanced use cases

## Getting Started

Add the extension dependency to your project:

```xml
<dependency>
    <groupId>io.quarkiverse.db-scheduler</groupId>
    <artifactId>quarkus-db-scheduler</artifactId>
    <version>${quarkus-db-scheduler.version}</version>
</dependency>
```

You also need a JDBC driver extension (e.g. `quarkus-jdbc-postgresql`) and a configured datasource.

Create the required database table:

```sql
create table scheduled_tasks (
  task_name text not null,
  task_instance text not null,
  task_data bytea,
  execution_time timestamp with time zone not null,
  picked boolean not null,
  picked_by text,
  last_success timestamp with time zone,
  last_failure timestamp with time zone,
  consecutive_failures int,
  last_heartbeat timestamp with time zone,
  version bigint not null,
  primary key (task_name, task_instance)
);
```

Then use `@Scheduled` as usual:

```java
@ApplicationScoped
public class MyJobs {

    @Scheduled(every = "10s", identity = "my-recurring-job")
    void everyTenSeconds() {
        // persisted and cluster-safe
    }

    @Scheduled(cron = "0 0 12 * * ?", identity = "daily-noon")
    void dailyAtNoon() {
        // runs once per day across the cluster
    }
}
```

## Documentation

Full documentation is available at <https://docs.quarkiverse.io/quarkus-db-scheduler/dev/>.
