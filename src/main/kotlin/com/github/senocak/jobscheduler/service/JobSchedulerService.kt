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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class JobSchedulerService(
    private val applicationContext: ApplicationContext,
    @Qualifier("taskScheduler") private val taskScheduler: TaskScheduler,
    private val jobPersistenceService: JobPersistenceService
) {
    private val log: Logger by logger()
    private val jobs: ConcurrentHashMap<UUID, JobTask> = ConcurrentHashMap()
    private val scheduledTasks: ConcurrentHashMap<UUID, ScheduledFuture<*>> = ConcurrentHashMap()

    @PostConstruct
    fun initializeJobs() {
        log.info("Initializing job scheduler...")
        val persistedJobs: List<JobPersistenceDto> = jobPersistenceService.loadJobsFromResources()
        val jobTaskBeans: Map<String, JobTask> = applicationContext.getBeansOfType(JobTask::class.java)

        persistedJobs.forEach { persistedJob: JobPersistenceDto ->
            val jobTask: JobTask? = jobTaskBeans.values.find { job: JobTask -> job::class.java.name == persistedJob.className }
            if (jobTask != null) {
                jobTask.id = persistedJob.id
                jobTask.name = persistedJob.className
                jobTask.cronExpression = persistedJob.cronExpression
                jobTask.status = persistedJob.status
                jobTask.lastRunTime = persistedJob.lastRunTime
                jobTask.nextRunTime = persistedJob.nextRunTime
                jobTask.enabled = persistedJob.enabled
                registerJob(job = jobTask)
            }
        }
    }

    fun registerJob(job: JobTask) {
        jobs[job.id] = job
        if (job.enabled) {
            job.cronExpression?.let { cronExpression: String ->
                scheduleJob(job = job, cronExpression = cronExpression)
            }
            jobPersistenceService.updateJobInFile(job = job)
            log.info("Registered job: ${job::class.java.simpleName} with id: ${job.id}")
        }
    }

    fun getAllJobs(): List<JobResponse> =
        jobs.map { (id: UUID, job: JobTask) ->
            val simpleName: String = job::class.java.simpleName
            JobResponse(
                name = "$simpleName:$id",
                cronExpression = job.cronExpression,
                isRunning = job.isRunning,
                status = job.status,
                lastRunTime = job.lastRunTime,
                nextRunTime = job.nextRunTime,
                enabled = job.enabled,
                runs = job.runs
            )
        }

    fun getJobById(id: UUID): JobResponse? {
        val job: JobTask = jobs[id] ?: return null
        val simpleName: String = job::class.java.simpleName
        return JobResponse(
            name = "$simpleName:${job.id}",
            cronExpression = job.cronExpression,
            isRunning = job.isRunning,
            status = job.status,
            lastRunTime = job.lastRunTime,
            nextRunTime = job.nextRunTime,
            enabled = job.enabled,
            runs = job.runs
        )
    }

    fun startJobById(id: UUID, params: Map<String, Any>? = null): Boolean {
        val job: JobTask = jobs[id] ?: return false
        if (job.isRunning) {
            log.warn("Job ${job.id} is already running")
            return false
        }
        return try {
            jobPersistenceService.updateJobInFile(job = job)
            job.executes(params = params)
            job.cronExpression?.let { cronExpression: String ->
                calculateNextRunTime(job = job, cronExpression = cronExpression)
            }
            jobPersistenceService.updateJobInFile(job = job)
            true
        } catch (e: Exception) {
            log.error("Failed to start job ${job.id}: ${e.message}", e)
            false
        }
    }

    fun stopJobById(id: UUID): Boolean {
        val job: JobTask = jobs[id] ?: return false
        if (!job.isRunning && scheduledTasks[id] == null) {
            log.warn("Job ${job.id} is not running and not scheduled")
            return false
        }
        // Unschedule future executions
        scheduledTasks[id]?.cancel(false)
        scheduledTasks.remove(id)
        // Update runtime state
        job.status = JobStatus.SCHEDULED
        job.isRunning = false
        job.enabled = false
        job.nextRunTime = null
        jobPersistenceService.updateJobInFile(job = job)
        log.info("Stopped and unscheduled job: ${job.id}")
        return true
    }

    fun updateJobById(id: UUID, cronExpression: String?, triggerType: String?, newName: String?): Boolean {
        val job: JobTask = jobs[id] ?: return false
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
            scheduledTasks[id]?.cancel(false)
            scheduledTasks.remove(id)
            job.cronExpression = finalCronExpression
            finalCronExpression?.let { scheduleJob(job = job, cronExpression = it) }
        }
        jobPersistenceService.updateJobInFile(job = job)
        log.info("Updated job: ${job.id} with cron: $finalCronExpression")
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

    fun removeJobById(id: UUID): Boolean {
        val job: JobTask = jobs.remove(id) ?: return false
        scheduledTasks[id]?.cancel(false)
        scheduledTasks.remove(id)
        jobPersistenceService.removeJobFromFile(id = id)
        jobPersistenceService.updateJobInFile(job = job)
        log.info("Removed job: ${job.id}")
        return true
    }

    fun saveAllJobs() {
        // Convert to a map keyed by a synthetic name for persistence if needed; here only ids are used in DTO
        val mapForPersistence: Map<String, JobTask> = jobs.mapKeys { (id: UUID, _): Map.Entry<UUID, JobTask> -> id.toString() }
        jobPersistenceService.saveJobsToFile(mapForPersistence)
    }

    private fun scheduleJob(job: JobTask, cronExpression: String) {
        try {
            val scheduledTask: ScheduledFuture<*>? = taskScheduler.schedule(
                {
                    try {
                        job.executes()
                        calculateNextRunTime(job = job, cronExpression = cronExpression)
                    } catch (e: Exception) {
                        log.error("Scheduled job ${job.id} failed: ${e.message}", e)
                        job.status = JobStatus.FAILED
                    }
                },
                CronTrigger(cronExpression)
            )
            scheduledTasks[job.id] = scheduledTask as ScheduledFuture<*>
            job.status = JobStatus.SCHEDULED
            calculateNextRunTime(job = job, cronExpression = cronExpression)
            log.info("Scheduled job ${job.id} with cron: $cronExpression, next run: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to schedule job ${job.id} with cron $cronExpression: ${e.message}", e)
        }
    }

    private fun calculateNextRunTime(job: JobTask, cronExpression: String) {
        try {
            val now: LocalDateTime = LocalDateTime.now()
            val cronTrigger: CronExpression = CronExpression.parse(cronExpression)
            val nextRun: LocalDateTime? = cronTrigger.next(now)
            job.nextRunTime = nextRun
            log.info("Calculated next run for ${job.name} time: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to calculate next run time: ${e.message}", e)
            job.nextRunTime = null
        } finally {
            jobPersistenceService.updateJobInFile(job = job)
        }
    }
}
