package com.hsbc.gps.integrator;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
public class IntegratorEngineApplication {

    private static ConfigurableApplicationContext appContext;
    private static boolean bRestartInProgress = false;
    private static Lock mutex = new ReentrantLock();

    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    public IntegratorEngineApplication() {
        bRestartInProgress = false;
    }

    public static void main(String[] args) {
        appContext = SpringApplication.run(IntegratorEngineApplication.class, args);
    }

    public static synchronized void restartApplication() {

        if (!bRestartInProgress) {
            try {
                mutex.lock();

                bRestartInProgress = true;
                ApplicationArguments args = appContext.getBean(ApplicationArguments.class);

                Thread thread = new Thread(() -> {
                    appContext.close();
                    appContext = SpringApplication.run(IntegratorEngineApplication.class, args.getSourceArgs());
                });

                thread.setDaemon(false);
                thread.start();

            } finally {
                mutex.unlock();
            }
        }

    }

    @Bean
    public ThreadPoolTaskScheduler setSchedulerToWait(ThreadPoolTaskScheduler threadPoolTaskScheduler) {
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        return threadPoolTaskScheduler;
    }

    @PreDestroy
    public void destroy() throws Exception {
        System.out.println("Spring Container is being gracefully destroyed....");

        while (threadPoolTaskScheduler.getActiveCount() > 0) {
            System.out.println("Waiting for child threads to finish the shutdown....");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        // TODO::Final cleanup if any

        System.out.println("!!!!!Spring Container is gracefully destroyed!!!!!");
    }


}
