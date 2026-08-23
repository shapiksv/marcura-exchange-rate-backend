package com.example.marcuraexchangeratebackend.rate.scheduler;

import com.example.marcuraexchangeratebackend.rate.application.RateCollectionResult;
import com.example.marcuraexchangeratebackend.rate.application.RateCollectionService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for daily exchange rate collection from Fixer.io.
 * <p>
 * Execution:
 * - Runs once per day at 00:05 UTC (configured via application.yml)
 * - Uses ShedLock to ensure only one instance executes when multiple app instances are running
 * - Database-backed lock prevents duplicate collection
 * <p>
 * Lock Strategy:
 * - lockAtMostFor: 10 minutes - protects against stale locks if an instance crashes
 * - lockAtLeastFor: 30 seconds - prevents too-frequent execution in case of clock skew
 * <p>
 * Transaction Boundary:
 * This scheduler does NOT have @Transactional. The transaction boundary is defined in:
 * RateCollectionService → RatePersistenceService (@Transactional)
 * <p>
 * Error Handling:
 * Exceptions are logged and allowed to propagate. ShedLock automatically releases the lock.
 * The next scheduled execution will run normally.
 * <p>
 * Multi-Instance Safety:
 * - ShedLock prevents normal duplicate scheduler execution across instances
 * - PostgreSQL ON CONFLICT in RatePersistenceService protects database integrity
 * - Both layers work together: ShedLock reduces load, DB constraint ensures correctness
 */
@Component
public class RateCollectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RateCollectionScheduler.class);

    private final RateCollectionService rateCollectionService;

    public RateCollectionScheduler(RateCollectionService rateCollectionService) {
        this.rateCollectionService = rateCollectionService;
    }

    /**
     * Scheduled task that collects latest exchange rates from Fixer.io.
     * <p>
     * Schedule: 00:05 UTC daily (configurable via scheduler.rate-collection.cron and .zone)
     * <p>
     * Lock: "dailyRateCollection"
     * - lockAtMostFor: 10 minutes (600 seconds)
     *   Rationale: Fixer fetch + validation + persistence should complete in under 1 minute normally.
     *   10 minutes allows for network delays while preventing indefinite stale locks.
     *   If an instance crashes, the lock is released after 10 minutes maximum.
     * <p>
     * - lockAtLeastFor: 30 seconds
     *   Rationale: Guards against immediate repeated execution after a fast successful run.
     *   Provides a small safety window even if the job completes quickly.
     * <p>
     * ShedLock uses database time (configured in SchedulerConfig.usingDbTime()), so clock
     * differences between application instances do not affect lock behavior.
     *
     * Data integrity relies on PostgreSQL unique constraints and atomic ON CONFLICT upsert
     * in RatePersistenceService.
     */
    @Scheduled(
            cron = "${scheduler.rate-collection.cron:0 5 0 * * *}",
            zone = "${scheduler.rate-collection.zone:UTC}"
    )
    @SchedulerLock(
            name = "dailyRateCollection",
            lockAtMostFor = "10m",
            lockAtLeastFor = "30s"
    )
    public void collectDailyRates() {
        log.info("=== Starting scheduled daily rate collection (00:05 UTC) ===");

        try {
            RateCollectionResult result = rateCollectionService.collectAndPersistLatestRates();

            log.info("Daily rate collection completed: date={}, base={}, total={}, inserted={}, updated={}",
                    result.rateDate(),
                    result.baseCurrency(),
                    result.totalRates(),
                    result.inserted(),
                    result.updated());

        } catch (RuntimeException e) {
            log.error("Daily rate collection failed", e);
            throw e;
        }
    }
}
