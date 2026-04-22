package io.quarkiverse.db.scheduler.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.db.scheduler.runtime.DbSchedulerSchedulerImpl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.scheduler.deployment.SchedulerImplementationBuildItem;

class DbSchedulerProcessor {

    private static final String FEATURE = "db-scheduler";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    SchedulerImplementationBuildItem implementation() {
        return new SchedulerImplementationBuildItem(DbSchedulerSchedulerImpl.IMPLEMENTATION,
                DotName.createSimple(DbSchedulerSchedulerImpl.class), 1);
    }

    @BuildStep
    void additionalBeans(BuildProducer<io.quarkus.arc.deployment.AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(io.quarkus.arc.deployment.AdditionalBeanBuildItem.unremovableOf(
                DbSchedulerSchedulerImpl.class));
    }

    @BuildStep
    ServiceStartBuildItem serviceStart() {
        return new ServiceStartBuildItem(FEATURE);
    }

    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(ReflectiveClassBuildItem
                .builder(
                        "com.github.kagkarlsson.scheduler.task.helper.RecurringTask",
                        "com.github.kagkarlsson.scheduler.task.helper.OneTimeTask",
                        "com.github.kagkarlsson.scheduler.task.schedule.CronSchedule",
                        "com.github.kagkarlsson.scheduler.task.schedule.FixedDelay",
                        "com.github.kagkarlsson.scheduler.task.schedule.Daily",
                        "com.github.kagkarlsson.scheduler.jdbc.AutodetectJdbcCustomization")
                .methods()
                .fields()
                .build());
    }
}
