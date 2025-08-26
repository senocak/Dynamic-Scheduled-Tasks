package com.github.senocak.jobscheduler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
class SchedulingConfig {

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 10
        scheduler.setThreadNamePrefix("job-scheduler-")
        scheduler.initialize()
        return scheduler
    }

    @Bean
    fun scheduledExecutorService(): ScheduledExecutorService =
        Executors.newScheduledThreadPool(10)



    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
