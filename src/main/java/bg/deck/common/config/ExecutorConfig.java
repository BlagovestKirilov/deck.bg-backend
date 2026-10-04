package bg.deck.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService gameScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /** The general one: anything asking for an ExecutorService means this. */
    @Primary
    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * One thread, so account emails leave in the order they were asked for.
     *
     * <p>Asking for a new link expires the one before it, so the order the
     * emails arrive in is what tells the person which link still works. Sending
     * them all at once left that to chance — and to a mail server that answers
     * five connections at once in whatever order it likes.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService mailExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mail-", 0).factory());
    }
}
