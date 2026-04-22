package io.quarkiverse.db.scheduler.it;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.scheduler.Scheduled;

@Path("/db-scheduler")
@ApplicationScoped
public class DbSchedulerResource {

    static final AtomicInteger COUNTER = new AtomicInteger();

    @Scheduled(every = "1s", identity = "every-second")
    void everySecond() {
        COUNTER.incrementAndGet();
    }

    @GET
    @Path("/count")
    public int count() {
        return COUNTER.get();
    }
}
