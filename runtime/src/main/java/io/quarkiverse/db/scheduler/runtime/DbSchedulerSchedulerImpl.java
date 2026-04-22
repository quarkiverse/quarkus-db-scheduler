package io.quarkiverse.db.scheduler.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.jboss.logging.Logger;

import com.cronutils.model.CronType;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import com.github.kagkarlsson.scheduler.task.schedule.CronStyle;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.DelayedExecution;
import io.quarkus.scheduler.FailedExecution;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.ScheduledJobPaused;
import io.quarkus.scheduler.ScheduledJobResumed;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.SchedulerPaused;
import io.quarkus.scheduler.SchedulerResumed;
import io.quarkus.scheduler.SkippedExecution;
import io.quarkus.scheduler.SuccessfulExecution;
import io.quarkus.scheduler.Trigger;
import io.quarkus.scheduler.common.runtime.BaseScheduler;
import io.quarkus.scheduler.common.runtime.CronParser;
import io.quarkus.scheduler.common.runtime.Events;
import io.quarkus.scheduler.common.runtime.ScheduledInvoker;
import io.quarkus.scheduler.common.runtime.ScheduledMethod;
import io.quarkus.scheduler.common.runtime.SchedulerContext;
import io.quarkus.scheduler.common.runtime.util.SchedulerUtils;
import io.quarkus.scheduler.runtime.SchedulerConfig;
import io.quarkus.scheduler.runtime.SchedulerRuntimeConfig;
import io.quarkus.scheduler.runtime.SchedulerRuntimeConfig.StartMode;
import io.quarkus.scheduler.spi.JobInstrumenter;
import io.vertx.core.Vertx;

@Typed(Scheduler.class)
@Singleton
public class DbSchedulerSchedulerImpl extends BaseScheduler implements Scheduler {

    public static final String IMPLEMENTATION = "db-scheduler";

    private static final Logger LOG = Logger.getLogger(DbSchedulerSchedulerImpl.class);

    private final com.github.kagkarlsson.scheduler.Scheduler dbScheduler;
    private final Map<String, DbSchedulerTrigger> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<String> pausedJobs = ConcurrentHashMap.newKeySet();
    private final boolean startHalted;
    private final Duration shutdownMaxWait;
    private volatile boolean running;

    public DbSchedulerSchedulerImpl(SchedulerContext context,
            DbSchedulerRuntimeConfig dbSchedulerConfig,
            SchedulerRuntimeConfig schedulerRuntimeConfig,
            Instance<DataSource> dataSources,
            Event<SkippedExecution> skippedExecutionEvent,
            Event<SuccessfulExecution> successExecutionEvent,
            Event<FailedExecution> failedExecutionEvent,
            Event<DelayedExecution> delayedExecutionEvent,
            Event<SchedulerPaused> schedulerPausedEvent,
            Event<SchedulerResumed> schedulerResumedEvent,
            Event<ScheduledJobPaused> scheduledJobPausedEvent,
            Event<ScheduledJobResumed> scheduledJobResumedEvent,
            Vertx vertx,
            SchedulerConfig schedulerConfig,
            Instance<JobInstrumenter> jobInstrumenter,
            ScheduledExecutorService blockingExecutor) {
        super(vertx, new CronParser(context.getCronType()), schedulerRuntimeConfig.overdueGracePeriod(),
                new Events(skippedExecutionEvent, successExecutionEvent, failedExecutionEvent, delayedExecutionEvent,
                        schedulerPausedEvent, schedulerResumedEvent, scheduledJobPausedEvent, scheduledJobResumedEvent),
                jobInstrumenter, blockingExecutor);
        this.shutdownMaxWait = dbSchedulerConfig.shutdownMaxWait();

        StartMode startMode = schedulerRuntimeConfig.startMode();
        boolean forceStart;
        if (startMode != StartMode.NORMAL) {
            startHalted = (startMode == StartMode.HALTED);
            forceStart = startHalted || (startMode == StartMode.FORCED);
        } else {
            startHalted = false;
            forceStart = false;
        }

        JobInstrumenter instrumenter = null;
        if (schedulerConfig.tracingEnabled() && jobInstrumenter.isResolvable()) {
            instrumenter = jobInstrumenter.get();
        }

        if (!schedulerRuntimeConfig.enabled()) {
            LOG.info("db-scheduler is disabled by config property and will not be started");
            this.dbScheduler = null;
            return;
        }

        List<ScheduledMethod> methods = context.getScheduledMethods(IMPLEMENTATION);

        if (!forceStart && methods.isEmpty() && !context.forceSchedulerStart()) {
            LOG.info("No scheduled business methods found - db-scheduler will not be started");
            this.dbScheduler = null;
            return;
        }

        if (!dataSources.isResolvable()) {
            throw new IllegalStateException(
                    "A datasource must be configured for db-scheduler. "
                            + "Please add a JDBC driver extension (e.g. quarkus-jdbc-postgresql) "
                            + "and configure quarkus.datasource.* properties.");
        }
        DataSource dataSource = dataSources.get();

        CronType cronType = context.getCronType();
        List<RecurringTask<?>> recurringTasks = new ArrayList<>();

        for (ScheduledMethod method : methods) {
            int nameSequence = 0;

            for (Scheduled scheduled : method.getSchedules()) {
                if (!context.matchesImplementation(scheduled, IMPLEMENTATION)) {
                    continue;
                }
                String identity = SchedulerUtils.lookUpPropertyValue(scheduled.identity());
                if (identity.isEmpty()) {
                    identity = ++nameSequence + "_" + method.getInvokerClassName();
                }

                ScheduledInvoker invoker = context.createInvoker(method.getInvokerClassName());
                invoker = initInvoker(invoker, events, scheduled.concurrentExecution(),
                        initSkipPredicate(scheduled.skipExecutionIf()), instrumenter, vertx,
                        false,
                        SchedulerUtils.parseExecutionMaxDelayAsMillis(scheduled), blockingExecutor);

                Schedule schedule = createSchedule(scheduled, cronType);
                if (schedule == null) {
                    continue;
                }

                Duration gracePeriod = SchedulerUtils.parseOverdueGracePeriod(scheduled, defaultOverdueGracePeriod);
                DbSchedulerTrigger trigger = new DbSchedulerTrigger(identity, schedule, gracePeriod,
                        method.getMethodDescription());
                scheduledTasks.put(identity, trigger);

                RecurringTask<Void> task = createRecurringTask(identity, schedule, invoker, trigger);
                recurringTasks.add(task);

                LOG.debugf("Scheduled business method %s with config %s", method.getMethodDescription(), scheduled);
            }
        }

        com.github.kagkarlsson.scheduler.SchedulerBuilder builder = com.github.kagkarlsson.scheduler.Scheduler
                .create(dataSource, new ArrayList<>())
                .threads(dbSchedulerConfig.threadCount())
                .pollingInterval(dbSchedulerConfig.pollingInterval())
                .heartbeatInterval(dbSchedulerConfig.heartbeatInterval())
                .shutdownMaxWait(dbSchedulerConfig.shutdownMaxWait())
                .tableName(dbSchedulerConfig.tableName());

        for (RecurringTask<?> task : recurringTasks) {
            builder.startTasks(task);
        }

        if (dbSchedulerConfig.enableImmediateExecution()) {
            builder.enableImmediateExecution();
        }
        if (dbSchedulerConfig.alwaysPersistTimestampInUtc()) {
            builder.alwaysPersistTimestampInUTC();
        }

        this.dbScheduler = builder.build();
    }

    @Produces
    @Singleton
    com.github.kagkarlsson.scheduler.Scheduler produceDbScheduler() {
        if (dbScheduler == null) {
            throw new IllegalStateException(
                    "db-scheduler is either explicitly disabled through quarkus.scheduler.enabled=false or no @Scheduled "
                            + "methods were found. If you only need to schedule a job programmatically you can force the start "
                            + "of the scheduler by setting 'quarkus.scheduler.start-mode=forced'.");
        }
        return dbScheduler;
    }

    @Override
    public boolean isStarted() {
        return dbScheduler != null;
    }

    @Override
    public String implementation() {
        return IMPLEMENTATION;
    }

    @Override
    public void pause() {
        if (!isStarted()) {
            throw notStarted();
        }
        dbScheduler.pause();
        running = false;
        events.fireSchedulerPaused();
    }

    @Override
    public void pause(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity, "Cannot pause - identity is null");
        if (identity.isEmpty()) {
            LOG.warn("Cannot pause - identity is empty");
            return;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        DbSchedulerTrigger trigger = scheduledTasks.get(parsedIdentity);
        if (trigger != null) {
            pausedJobs.add(parsedIdentity);
            events.fireScheduledJobPaused(new ScheduledJobPaused(trigger));
        }
    }

    @Override
    public boolean isPaused(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity);
        if (identity.isEmpty()) {
            return false;
        }
        return pausedJobs.contains(SchedulerUtils.lookUpPropertyValue(identity));
    }

    @Override
    public void resume() {
        if (!isStarted()) {
            throw notStarted();
        }
        dbScheduler.resume();
        running = true;
        events.fireSchedulerResumed();
    }

    @Override
    public void resume(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity, "Cannot resume - identity is null");
        if (identity.isEmpty()) {
            LOG.warn("Cannot resume - identity is empty");
            return;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        DbSchedulerTrigger trigger = scheduledTasks.get(parsedIdentity);
        if (trigger != null) {
            pausedJobs.remove(parsedIdentity);
            events.fireScheduledJobResumed(new ScheduledJobResumed(trigger));
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public List<Trigger> getScheduledJobs() {
        if (!isStarted()) {
            throw notStarted();
        }
        return List.copyOf(scheduledTasks.values());
    }

    @Override
    public Trigger getScheduledJob(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity);
        if (identity.isEmpty()) {
            return null;
        }
        return scheduledTasks.get(SchedulerUtils.lookUpPropertyValue(identity));
    }

    @Override
    public JobDefinition<?> newJob(String identity) {
        throw new UnsupportedOperationException(
                "Programmatic job scheduling is not supported by db-scheduler. "
                        + "Use @Scheduled annotations or inject the db-scheduler Scheduler directly for advanced use cases.");
    }

    @Override
    public Trigger unscheduleJob(String identity) {
        throw new UnsupportedOperationException(
                "Programmatic job unscheduling is not supported by db-scheduler. "
                        + "Inject the db-scheduler Scheduler directly for advanced use cases.");
    }

    void start(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent startupEvent) {
        if (dbScheduler == null || startHalted) {
            return;
        }
        dbScheduler.start();
        running = true;
    }

    void destroy(@Observes(notifyObserver = Reception.IF_EXISTS) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        shutdownScheduler();
    }

    @PreDestroy
    void destroy() {
        shutdownScheduler();
    }

    private synchronized void shutdownScheduler() {
        if (dbScheduler != null && running) {
            running = false;
            dbScheduler.stop();
        }
    }

    private RecurringTask<Void> createRecurringTask(String identity, Schedule schedule,
            ScheduledInvoker invoker, DbSchedulerTrigger trigger) {
        return Tasks.recurring(identity, schedule)
                .execute((taskInstance, executionContext) -> {
                    if (pausedJobs.contains(identity)) {
                        return;
                    }
                    Instant now = Instant.now();
                    trigger.setLastFireTime(now);
                    DbSchedulerExecution execution = new DbSchedulerExecution(trigger, now);
                    try {
                        CompletionStage<Void> result = invoker.invoke(execution);
                        result.toCompletableFuture().get();
                    } catch (Exception e) {
                        if (e.getCause() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new RuntimeException(e);
                    }
                });
    }

    private Schedule createSchedule(Scheduled scheduled, CronType cronType) {
        if (!scheduled.cron().isEmpty()) {
            String cron = SchedulerUtils.lookUpPropertyValue(scheduled.cron());
            if (SchedulerUtils.isOff(cron)) {
                return null;
            }
            ZoneId timeZone = SchedulerUtils.parseCronTimeZone(scheduled);
            return new CronSchedule(cron, timeZone != null ? timeZone : ZoneId.systemDefault(),
                    toCronStyle(cronType));
        } else if (!scheduled.every().isEmpty()) {
            OptionalLong everyMillis = SchedulerUtils.parseEveryAsMillis(scheduled);
            if (everyMillis.isEmpty()) {
                return null;
            }
            return FixedDelay.ofMillis(everyMillis.getAsLong());
        }
        throw new IllegalArgumentException("Invalid schedule configuration: " + scheduled);
    }

    private static CronStyle toCronStyle(CronType cronType) {
        return switch (cronType) {
            case QUARTZ -> CronStyle.QUARTZ;
            case UNIX -> CronStyle.UNIX;
            case CRON4J -> CronStyle.CRON4J;
            case SPRING -> CronStyle.SPRING;
            case SPRING53 -> CronStyle.SPRING53;
        };
    }

    static class DbSchedulerTrigger implements Trigger {

        private final String id;
        private final Schedule schedule;
        private final Duration gracePeriod;
        private final String methodDescription;
        private volatile Instant lastFireTime;

        DbSchedulerTrigger(String id, Schedule schedule, Duration gracePeriod, String methodDescription) {
            this.id = id;
            this.schedule = schedule;
            this.gracePeriod = gracePeriod;
            this.methodDescription = methodDescription;
        }

        void setLastFireTime(Instant lastFireTime) {
            this.lastFireTime = lastFireTime;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Instant getNextFireTime() {
            if (schedule.isDeterministic()) {
                return schedule.getInitialExecutionTime(Instant.now());
            }
            return null;
        }

        @Override
        public Instant getPreviousFireTime() {
            return lastFireTime;
        }

        @Override
        public boolean isOverdue() {
            Instant next = getNextFireTime();
            if (next == null) {
                return false;
            }
            return next.plus(gracePeriod).isBefore(Instant.now());
        }

        @Override
        public String getMethodDescription() {
            return methodDescription;
        }
    }

    static class DbSchedulerExecution implements ScheduledExecution {

        private final DbSchedulerTrigger trigger;
        private final Instant fireTime;

        DbSchedulerExecution(DbSchedulerTrigger trigger, Instant fireTime) {
            this.trigger = trigger;
            this.fireTime = fireTime;
        }

        @Override
        public Trigger getTrigger() {
            return trigger;
        }

        @Override
        public Instant getFireTime() {
            return fireTime;
        }

        @Override
        public Instant getScheduledFireTime() {
            return fireTime;
        }
    }
}
