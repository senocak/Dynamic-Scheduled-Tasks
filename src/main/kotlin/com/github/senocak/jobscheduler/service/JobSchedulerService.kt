package com.github.senocak.jobscheduler.service

import com.github.senocak.jobscheduler.dto.JobPersistenceDto
import com.github.senocak.jobscheduler.dto.JobResponse
import com.github.senocak.jobscheduler.dto.TriggerTypeResponse
import com.github.senocak.jobscheduler.jobs.JobTask
import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import com.github.senocak.jobscheduler.model.TriggerType
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture


@Service
class JobSchedulerService(
    private val applicationContext: ApplicationContext,
    private val taskScheduler: TaskScheduler,
    private val jobPersistenceService: JobPersistenceService
) {
    private val log: Logger by logger()
    private val jobs: ConcurrentHashMap<String, JobTask> = ConcurrentHashMap()
    private val scheduledTasks: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()

    @PostConstruct
    fun initializeJobs() {
        log.info("Initializing job scheduler...")
        val persistedJobs: List<JobPersistenceDto> = jobPersistenceService.loadJobsFromResources()
        val jobTaskBeans: Map<String, JobTask> = applicationContext.getBeansOfType(JobTask::class.java)
        jobTaskBeans.forEach { (beanName: String, job: JobTask) ->
            val persistedJob: JobPersistenceDto? = persistedJobs.find { it.className == job::class.java.name }
            if (persistedJob != null) {
                job.cronExpression = persistedJob.cronExpression
                job.status = persistedJob.status
                job.lastRunTime = persistedJob.lastRunTime
                job.nextRunTime = persistedJob.nextRunTime
                log.info("Applied persisted configuration for job: $beanName")
                registerJob(beanName = beanName, job = job)
            }
        }
        log.info("Registered ${jobs.size} jobs")
    }

    fun registerJob(beanName: String, job: JobTask) {
        jobs[beanName] = job
        job.cronExpression?.let { cronExpression: String ->
            scheduleJob(beanName = beanName, job = job, cronExpression = cronExpression)
        }
        jobPersistenceService.updateJobInFile(beanName = beanName, job = job)
        log.info("Registered job: $beanName")
    }

    fun getAllJobs(): List<JobResponse> =
        jobs.map { (beanName: String, job: JobTask) ->
            JobResponse(
                name = beanName,
                cronExpression = job.cronExpression,
                isRunning = job.isRunning,
                status = job.status,
                lastRunTime = job.lastRunTime,
                nextRunTime = job.nextRunTime
            )
        }

    fun getJob(name: String): JobResponse? {
        val job: JobTask = jobs[name] ?: return null
        return JobResponse(
            name = name,
            cronExpression = job.cronExpression,
            isRunning = job.isRunning,
            status = job.status,
            lastRunTime = job.lastRunTime,
            nextRunTime = job.nextRunTime
        )
    }

    fun startJob(name: String, params: Map<String, Any>? = null): Boolean {
        val job: JobTask = jobs[name] ?: return false
        if (job.isRunning) {
            log.warn("Job $name is already running")
            return false
        }
        return try {
            job.executes(params = params)
            job.cronExpression?.let { cronExpression: String ->
                calculateNextRunTime(job = job, cronExpression = cronExpression)
            }
            jobPersistenceService.updateJobInFile(beanName = name, job = job)
            true
        } catch (e: Exception) {
            log.error("Failed to start job $name: ${e.message}", e)
            false
        }
    }

    fun stopJob(name: String): Boolean {
        val job: JobTask = jobs[name] ?: return false
        if (!job.isRunning) {
            log.warn("Job $name is not running")
            return false
        }
        job.status = JobStatus.SCHEDULED
        job.isRunning = false
        log.info("Stopped job: $name")
        return true
    }

    fun updateJob(name: String, cronExpression: String?, triggerType: String?, newName: String?): Boolean {
        val job: JobTask = jobs[name] ?: return false
        val finalCronExpression: String? = when {
            triggerType != null -> {
                val trigger: TriggerType? = TriggerType.fromDisplayName(displayName = triggerType)
                when {
                    trigger != null && trigger != TriggerType.CUSTOM -> trigger.cronExpression
                    else -> cronExpression
                }
            }
            else -> cronExpression
        }
        if (finalCronExpression != job.cronExpression) {
            scheduledTasks[name]?.cancel(false)
            scheduledTasks.remove(name)
            job.cronExpression = finalCronExpression
            finalCronExpression?.let { scheduleJob(beanName = name, job = job, cronExpression = it) }
        }
        jobPersistenceService.updateJobInFile(beanName = name, job = job)
        log.info("Updated job: $name with cron: $finalCronExpression")
        return true
    }

    fun getAvailableTriggerTypes(): List<TriggerTypeResponse> =
        TriggerType.getPredefinedTriggers().map { triggerType: TriggerType ->
            TriggerTypeResponse(
                displayName = triggerType.displayName,
                cronExpression = triggerType.cronExpression,
                description = triggerType.description
            )
        }

    fun removeJob(name: String): Boolean {
        val job: JobTask = jobs.remove(name) ?: return false
        scheduledTasks[name]?.cancel(false)
        scheduledTasks.remove(name)
        jobPersistenceService.removeJobFromFile(jobName = name)
        log.info("Removed job: $name")
        return true
    }

    fun saveAllJobs() {
        jobPersistenceService.saveJobsToFile(jobs)
    }

    private fun scheduleJob(beanName: String, job: JobTask, cronExpression: String) {
        try {
            val scheduledTask: ScheduledFuture<*>? = taskScheduler.schedule(
                {
                    try {
                        job.executes()
                        calculateNextRunTime(job = job, cronExpression = cronExpression)
                    } catch (e: Exception) {
                        log.error("Scheduled job $beanName failed: ${e.message}", e)
                        job.status = JobStatus.FAILED
                    }
                },
                CronTrigger(cronExpression)
            )
            scheduledTasks[beanName] = scheduledTask as ScheduledFuture<*>
            job.status = JobStatus.SCHEDULED
            calculateNextRunTime(job = job, cronExpression = cronExpression)
            log.info("Scheduled job $beanName with cron: $cronExpression, next run: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to schedule job $beanName with cron $cronExpression: ${e.message}", e)
        }
    }

    private fun calculateNextRunTime(job: JobTask, cronExpression: String) {
        try {
            val now: LocalDateTime = LocalDateTime.now()
            val cronTrigger: CronExpression = CronExpression.parse(cronExpression)
            val nextRun: LocalDateTime? = cronTrigger.next(now)
            job.nextRunTime = nextRun
            log.info("Calculated next run time: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to calculate next run time: ${e.message}", e)
            job.nextRunTime = null
        }
    }
}
