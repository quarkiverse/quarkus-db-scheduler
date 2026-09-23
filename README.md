<div align="center">
  <div style="display: flex; align-items: center; justify-content: center; gap: 8px;">
    <img src="https://raw.githubusercontent.com/quarkiverse/.github/main/assets/images/quarkus.svg" alt="Quarkus logo" style="height: 70px; width: auto;">
    <img src="https://raw.githubusercontent.com/quarkiverse/.github/main/assets/images/plus-sign.svg" alt="Plus sign" style="height: 70px; width: auto;">
    <img src="https://raw.githubusercontent.com/quarkiverse/quarkus-db-scheduler/main/docs/modules/ROOT/assets/images/logo.png" alt="DBScheduler logo" style="height: 70px; width: auto;">
  </div>
  <h1>Quarkus Db Scheduler</h1>
</div>
<br>

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.db-scheduler/quarkus-db-scheduler?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.db-scheduler/quarkus-db-scheduler)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://opensource.org/licenses/Apache-2.0)
[![Build](https://github.com/quarkiverse/quarkus-db-scheduler/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-db-scheduler/actions/workflows/build.yml)

A Quarkus extension that integrates [db-scheduler](https://github.com/kagkarlsson/db-scheduler) with the Quarkus scheduler API. It provides persistent, cluster-friendly scheduling backed by a single database table, while you keep writing plain `@Scheduled` methods.

> [!NOTE]
> Quarkus Db Scheduler is a lightweight alternative to `quarkus-quartz` when you need jobs that survive restarts and run only once across a cluster, but don't want to manage the 11 tables Quartz requires.

## Features

- Full integration with `@Scheduled` annotations from `quarkus-scheduler`
- Persistent task storage using a single database table
- Cluster-safe execution: only one node picks up a given task
- Supports both cron expressions and fixed-interval schedules
- Configurable polling interval, thread pool, and heartbeat
- Pause and resume individual jobs or the entire scheduler
- Access the underlying `com.github.kagkarlsson.scheduler.Scheduler` for advanced use cases
- Run side by side with the in-memory Quarkus scheduler for jobs that should not be clustered

## Getting started

Read the full [Db Scheduler documentation](https://docs.quarkiverse.io/quarkus-db-scheduler/dev/index.html).

### Installation

Create a new project with db-scheduler:

- With [code.quarkus.io](https://code.quarkus.io/?e=io.quarkiverse.db-scheduler%3Aquarkus-db-scheduler&e=jdbc-postgresql)
- With the [Quarkus CLI](https://quarkus.io/guides/cli-tooling):

```bash
quarkus create app db-scheduler-app -x=io.quarkiverse.db-scheduler:quarkus-db-scheduler,jdbc-postgresql
```

Or add it to your `pom.xml` directly:

```xml
<dependency>
    <groupId>io.quarkiverse.db-scheduler</groupId>
    <artifactId>quarkus-db-scheduler</artifactId>
    <version>${quarkus-db-scheduler.version}</version>
</dependency>
```

You also need a JDBC driver extension (e.g. `quarkus-jdbc-postgresql`) and a configured datasource.

### Database table

Create the table db-scheduler requires (PostgreSQL shown; see the [documentation](https://docs.quarkiverse.io/quarkus-db-scheduler/dev/index.html#database-setup) for MySQL, MariaDB and Oracle):

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

> [!TIP]
> In dev and test mode, Dev Services can create the table for you: put the DDL in `src/main/resources/init.sql` and set `quarkus.datasource.devservices.init-script-path=init.sql`.

## Usage

Use `@Scheduled` as usual. Each job is persisted under its `identity` and runs on only one node in the cluster:

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

> [!IMPORTANT]
> The `identity` is the task name stored in the database. Always set an explicit, stable `identity` on every `@Scheduled` method.

## Non-clustered jobs

Some jobs, such as refreshing a local cache, need to run on **every** node. Quarkus can [run multiple scheduler implementations side by side](https://quarkus.io/guides/scheduler-reference#how-to-use-multiple-scheduler-implementations). Enable the composite scheduler:

```properties
quarkus.scheduler.use-composite-scheduler=true
```

Then use `executeWith` to send those jobs to the in-memory scheduler:

```java
@Scheduled(every = "30s", identity = "refresh-local-cache", executeWith = Scheduled.SIMPLE)
void refreshLocalCache() {
    // in-memory, runs on every node, nothing stored in the database
}
```

Methods without `executeWith` still run on db-scheduler.

## Configuration

```properties
quarkus.db-scheduler.polling-interval=10s
quarkus.db-scheduler.thread-count=10
quarkus.db-scheduler.table-name=scheduled_tasks
```

See the [configuration reference](https://docs.quarkiverse.io/quarkus-db-scheduler/dev/index.html#extension-configuration-reference) for all options.

## 🧑‍💻 Contributing

- Contribution is the best way to support and get involved in the community!
- Please consult the [Quarkiverse Code of Conduct](https://github.com/quarkiverse/.github/blob/main/CODE_OF_CONDUCT.md) for interacting in our community.
- To contribute to `quarkus-db-scheduler`, please check the [Quarkiverse contributing guide](https://github.com/quarkiverse/.github/blob/main/CONTRIBUTING.md).

### If you have any idea or question 🤷

- [Ask a question](https://github.com/quarkiverse/quarkus-db-scheduler/discussions)
- [Raise an issue](https://github.com/quarkiverse/quarkus-db-scheduler/issues)
- [Feature request](https://github.com/quarkiverse/quarkus-db-scheduler/issues)
- [Code submission](https://github.com/quarkiverse/quarkus-db-scheduler/pulls)

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://melloware.com"><img src="https://avatars.githubusercontent.com/u/4399574?v=4?s=100" width="100px;" alt="Melloware"/><br /><sub><b>Melloware</b></sub></a><br /><a href="#maintenance-melloware" title="Maintenance">🚧</a> <a href="https://github.com/quarkiverse/quarkus-db-scheduler/commits?author=melloware" title="Code">💻</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
