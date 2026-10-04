package bg.deck.common.config;

import bg.deck.common.constant.LogConstants;
import lombok.extern.log4j.Log4j2;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The scheduled jobs: the threads they run on, and the lock that keeps them to
 * one runner.
 *
 * <p>Only one instance of the service runs today, but a scheduled job that
 * assumes that is a job that runs twice the first time a second one exists —
 * during a deploy, where the old and the new overlap. The lock lives in the
 * database, which both instances already share.
 *
 * @see SchedulingProperties for the timings
 */
@Log4j2
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    /**
     * A small, named pool of its own.
     *
     * <p>The application runs on virtual threads, and Boot's own scheduler
     * would be an unbounded one. Scheduled work here is the opposite of what
     * that suits: a couple of jobs, each blocking on JDBC, that must not
     * multiply. A fixed pool bounds them, the name prefix makes them findable
     * in a thread dump or a log line, and there are two threads rather than one
     * so a long job cannot hold up an unrelated one.
     *
     * <p>Shutdown waits for a run in flight: these jobs write to the database,
     * and a deploy should not cut one in half.
     */
    @Bean
    public TaskScheduler taskScheduler(SchedulingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix("deck-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds((int) properties.shutdownGrace().toSeconds());
        // A cancelled job should leave the queue, not sit in it until its turn.
        scheduler.setRemoveOnCancelPolicy(true);
        // During shutdown the pool stops accepting: run it on the caller rather
        // than drop it silently.
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Without this, a job that throws is logged by Spring's own handler and
        // the schedule carries on — which is right, but the line says nothing
        // about which job it was.
        scheduler.setErrorHandler(throwable -> log.error(LogConstants.SCHEDULED_JOB_FAILED, throwable));
        return scheduler;
    }

    /**
     * The lock, kept in ShedLock's own table (changelog 017).
     *
     * <p>{@code usingDbTime} makes the database's clock the only one that
     * counts. Otherwise two servers whose clocks disagree by a minute can both
     * believe the lock is theirs.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
