package com.github.senocak.jobscheduler.service

import com.github.senocak.jobscheduler.dto.JobPersistenceDto
import com.github.senocak.jobscheduler.dto.JobResponse
import com.github.senocak.jobscheduler.dto.TriggerTypeResponse
import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import com.github.senocak.jobscheduler.jobs.JobTask
import com.github.senocak.jobscheduler.model.TriggerType
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
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
        
        // Load job configurations from JSON file
        val persistedJobs: List<JobPersistenceDto> = jobPersistenceService.loadJobsFromResources()
        
        // Discover all JobTask beans
        val jobTaskBeans: Map<String, JobTask> = applicationContext.getBeansOfType(JobTask::class.java)
        
        // Register jobs with persisted configurations
        jobTaskBeans.values.forEach { job: JobTask ->
            val persistedJob: JobPersistenceDto? = persistedJobs.find { it.className == job::class.java.name }
            if (persistedJob != null) {
                // Apply persisted configuration
                job.cronExpression = persistedJob.cronExpression
                job.status = persistedJob.status
                job.lastRunTime = persistedJob.lastRunTime
                job.nextRunTime = persistedJob.nextRunTime
                log.info("Applied persisted configuration for job: ${job.name}")
                registerJob(job = job)
            }
        }
        log.info("Registered ${jobs.size} jobs")
    }

    fun registerJob(job: JobTask) {
        jobs[job.id] = job
        
        // Schedule job if it has a cron expression
        job.cronExpression?.let { cronExpression: String ->
            scheduleJob(job = job, cronExpression = cronExpression)
        }
        
        // Persist job configuration
        jobPersistenceService.updateJobInFile(job = job)
        
        log.info("Registered job: ${job.name} with ID: ${job.id}")
    }

    fun getAllJobs(): List<JobResponse> {
        return jobs.values.map { job: JobTask ->
            JobResponse(
                id = job.id,
                name = job.name,
                cronExpression = job.cronExpression,
                isRunning = job.isRunning,
                status = job.status,
                lastRunTime = job.lastRunTime,
                nextRunTime = job.nextRunTime
            )
        }
    }

    fun getJob(id: UUID): JobResponse? {
        val job: JobTask = jobs[id] ?: return null
        return JobResponse(
            id = job.id,
            name = job.name,
            cronExpression = job.cronExpression,
            isRunning = job.isRunning,
            status = job.status,
            lastRunTime = job.lastRunTime,
            nextRunTime = job.nextRunTime
        )
    }

    fun startJob(id: UUID, params: Map<String, Any>? = null): Boolean {
        val job: JobTask = jobs[id] ?: return false
        if (job.isRunning) {
            log.warn("Job ${job.name} is already running")
            return false
        }
        try {
            job.execute(params = params)
            // Calculate next run time if job has a cron expression
            job.cronExpression?.let { cronExpression: String ->
                calculateNextRunTime(job = job, cronExpression = cronExpression)
            }
            
            // Persist job state after execution
            jobPersistenceService.updateJobInFile(job = job)
            
            return true
        } catch (e: Exception) {
            log.error("Failed to start job ${job.name}: ${e.message}", e)
            return false
        }
    }

    fun stopJob(id: UUID): Boolean {
        val job: JobTask = jobs[id] ?: return false
        
        if (!job.isRunning) {
            log.warn("Job ${job.name} is not running")
            return false
        }

        // For a real implementation, you might need to implement a way to interrupt the job
        // This is a simplified version
        job.status = JobStatus.SCHEDULED
        job.isRunning = false
        log.info("Stopped job: ${job.name}")
        return true
    }

    fun updateJob(id: UUID, cronExpression: String?, triggerType: String?, name: String?): Boolean {
        val job: JobTask = jobs[id] ?: return false
        // Determine the cron expression to use
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
        
        // Handle cron expression update
        if (finalCronExpression != job.cronExpression) {
            // Cancel existing scheduled task if any
            scheduledTasks[id]?.cancel(false)
            scheduledTasks.remove(id)
            
            // Update cron expression
            job.cronExpression = finalCronExpression
            
            // Schedule new task if cron expression is provided
            finalCronExpression?.let { scheduleJob(job = job, cronExpression = it) }
        }
        
        // Persist job configuration
        jobPersistenceService.updateJobInFile(job = job)
        
        log.info("Updated job: ${job.name} with cron: $finalCronExpression")
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

    fun removeJob(id: UUID): Boolean {
        val job: JobTask = jobs.remove(id) ?: return false
        
        // Cancel scheduled task if any
        scheduledTasks[id]?.cancel(false)
        scheduledTasks.remove(id)
        
        // Remove from persistence
        jobPersistenceService.removeJobFromFile(jobId = job.id.toString())
        
        log.info("Removed job: ${job.name}")
        return true
    }

    fun saveAllJobs() {
        jobPersistenceService.saveJobsToFile(jobs.values.toList())
    }

    private fun scheduleJob(job: JobTask, cronExpression: String) {
        try {
            val scheduledTask: ScheduledFuture<*>? = taskScheduler.schedule(
                {
                    try {
                        job.execute()
                        // Calculate next run time after execution
                        calculateNextRunTime(job = job, cronExpression = cronExpression)
                    } catch (e: Exception) {
                        log.error("Scheduled job ${job.name} failed: ${e.message}", e)
                        job.status = JobStatus.FAILED
                    }
                },
                CronTrigger(cronExpression)
            )
            
            scheduledTasks[job.id] = scheduledTask as ScheduledFuture<*>
            job.status = JobStatus.SCHEDULED
            
            // Calculate initial next run time when scheduling
            calculateNextRunTime(job = job, cronExpression = cronExpression)
            
            log.info("Scheduled job ${job.name} with cron: $cronExpression, next run: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to schedule job ${job.name} with cron $cronExpression: ${e.message}", e)
        }
    }

    private fun calculateNextRunTime(job: JobTask, cronExpression: String) {
        try {
            val now: LocalDateTime = LocalDateTime.now()
            
            // Calculate next run time based on current time
            // This is a simplified calculation - in a production environment,
            // you might want to use a more sophisticated cron parser
            val nextRun: LocalDateTime? = calculateNextRunFromCron(cronExpression = cronExpression, from = now)
            job.nextRunTime = nextRun
            
            log.info("Calculated next run time for job ${job.name}: ${job.nextRunTime}")
        } catch (e: Exception) {
            log.error("Failed to calculate next run time for job ${job.name}: ${e.message}", e)
            job.nextRunTime = null
        }
    }
    
    private fun calculateNextRunFromCron(cronExpression: String, from: LocalDateTime): LocalDateTime? {
        return try {
            // Parse cron expression and calculate next run time
            // This is a basic implementation - for production use, consider using a library like Quartz
            val parts: List<String> = cronExpression.split(" ")
            if (parts.size != 6) return null
            
            val minute: String = parts[1]
            val hour: String = parts[2]
            
            // Simple calculation for common patterns
            when {
                minute == "*" && hour == "*" -> {
                    // Every minute
                    from.plusMinutes(1)
                }
                minute.contains(other = "/") -> {
                    // Every X minutes
                    val interval: Int = minute.split("/")[1].toInt()
                    from.plusMinutes(interval.toLong())
                }
                hour.contains(other = "/") -> {
                    // Every X hours
                    val interval: Int = hour.split("/")[1].toInt()
                    from.plusHours(interval.toLong())
                }
                else -> {
                    // Default: next minute
                    from.plusMinutes(1)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to parse cron expression: $cronExpression", e)
            null
        }
    }
}
