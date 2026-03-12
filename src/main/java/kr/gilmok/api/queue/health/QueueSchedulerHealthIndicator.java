package kr.gilmok.api.queue.health;

import kr.gilmok.api.queue.scheduler.AdmissionScheduler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QueueSchedulerHealthIndicator implements HealthIndicator {

    private static final long STALE_THRESHOLD_MS = 30_000;

    private final AdmissionScheduler admissionScheduler;

    public QueueSchedulerHealthIndicator(AdmissionScheduler admissionScheduler) {
        this.admissionScheduler = admissionScheduler;
    }

    @Override
    public Health health() {
        long lastRun = admissionScheduler.getLastSuccessfulRunMs();
        long elapsed = System.currentTimeMillis() - lastRun;

        if (elapsed > STALE_THRESHOLD_MS) {
            return Health.down()
                    .withDetail("lastSuccessfulRun", lastRun)
                    .withDetail("elapsedMs", elapsed)
                    .withDetail("threshold", STALE_THRESHOLD_MS)
                    .build();
        }

        return Health.up()
                .withDetail("lastSuccessfulRun", lastRun)
                .withDetail("elapsedMs", elapsed)
                .build();
    }
}
