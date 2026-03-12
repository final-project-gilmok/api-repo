package kr.gilmok.api.queue.health;

import kr.gilmok.api.queue.scheduler.AdmissionScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QueueSchedulerHealthIndicator implements HealthIndicator {

    private final AdmissionScheduler admissionScheduler;
    private final long staleThresholdMs;

    public QueueSchedulerHealthIndicator(
            AdmissionScheduler admissionScheduler,
            @Value("${queue.health.stale-threshold-ms:30000}") long staleThresholdMs) {
        this.admissionScheduler = admissionScheduler;
        this.staleThresholdMs = staleThresholdMs;
    }

    @Override
    public Health health() {
        long lastRun = admissionScheduler.getLastSuccessfulRunMs();
        long elapsed = System.currentTimeMillis() - lastRun;

        if (elapsed > staleThresholdMs) {
            return Health.down()
                    .withDetail("lastSuccessfulRun", lastRun)
                    .withDetail("elapsedMs", elapsed)
                    .withDetail("threshold", staleThresholdMs)
                    .build();
        }

        return Health.up()
                .withDetail("lastSuccessfulRun", lastRun)
                .withDetail("elapsedMs", elapsed)
                .withDetail("threshold", staleThresholdMs)
                .build();
    }
}
