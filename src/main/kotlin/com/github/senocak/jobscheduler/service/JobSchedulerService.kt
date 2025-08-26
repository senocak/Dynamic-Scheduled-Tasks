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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class JobSchedulerService(
    private val applicationContext: ApplicationContext,
    private val taskScheduler: TaskScheduler,
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
        jobTaskBeans.forEach { (_: String, job: JobTask) ->
            val persistedJob: JobPersistenceDto? = persistedJobs.find { it.className == job::class.java.name }
            if (persistedJob != null && persistedJob.enabled) {
                job.id = persistedJob.id
                job.cronExpression = persistedJob.cronExpression
                job.status = persistedJob.status
                job.lastRunTime = persistedJob.lastRunTime
                job.nextRunTime = persistedJob.nextRunTime
                job.enabled = true
                log.info("Applied persisted configuration for job: ${job::class.java.simpleName}")
                registerJob(job = job)
            }
        }
    }

    fun registerJob(job: JobTask) {
        jobs[job.id] = job
        job.cronExpression?.let { cronExpression: String ->
            scheduleJob(jobId = job.id, job = job, cronExpression = cronExpression)
        }
        jobPersistenceService.updateJobInFile(job = job)
        log.info("Registered job: ${job::class.java.simpleName} with id: ${job.id}")
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
                nextRunTime = job.nextRunTime
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
            nextRunTime = job.nextRunTime
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
        if (!job.isRunning) {
            log.warn("Job ${job.id} is not running")
            return false
        }
        job.status = JobStatus.SCHEDULED
        job.isRunning = false
        jobPersistenceService.updateJobInFile(job = job)
        log.info("Stopped job: ${job.id}")
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
            finalCronExpression?.let { scheduleJob(jobId = id, job = job, cronExpression = it) }
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

    private fun scheduleJob(jobId: UUID, job: JobTask, cronExpression: String) {
        try {
            val scheduledTask: ScheduledFuture<*>? = taskScheduler.schedule(
                {
                    try {
                        job.executes()
                        calculateNextRunTime(job = job, cronExpression = cronExpression)
                    } catch (e: Exception) {
                        log.error("Scheduled job $jobId failed: ${e.message}", e)
                        job.status = JobStatus.FAILED
                    }
                },
                CronTrigger(cronExpression)
            )
            scheduledTasks[jobId] = scheduledTask as ScheduledFuture<*>
            job.status = JobStatus.SCHEDULED
            calculateNextRunTime(job = job, cronExpression = cronExpression)
            log.info("Scheduled job $jobId with cron: $cronExpression, next run: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to schedule job $jobId with cron $cronExpression: ${e.message}", e)
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
