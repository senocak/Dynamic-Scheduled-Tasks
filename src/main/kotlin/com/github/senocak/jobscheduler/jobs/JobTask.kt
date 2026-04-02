package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import java.time.LocalDateTime
import java.util.UUID

abstract class JobTask {
    open var id: UUID = UUID.randomUUID()
    protected val log: Logger by logger()
    open var cronExpression: String? = null
    open var name: String? = null
    open var isRunning: Boolean = false
    open var status: JobStatus = JobStatus.SCHEDULED
    open var lastRunTime: LocalDateTime? = null
    open var nextRunTime: LocalDateTime? = null
    open var enabled: Boolean = false
    val logs: MutableList<JobLogEntry> = mutableListOf()

    protected abstract fun execute(params: Map<String, Any>? = null)

    fun executes(params: Map<String, Any>? = null) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        log.info("Running Job `$name` at $lastRunTime with params: $params, at $lastRunTime")
        logs.add(element = JobLogEntry(timestamp = lastRunTime!!, level = "INFO", message = "START params=$params"))
        try {
            execute(params = params)
            Thread.sleep(1_000)
            status = JobStatus.COMPLETED
            logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = "COMPLETED"))
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job OperatingSystemJob failed: ${e.message}")
            logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "ERROR", message = "FAILED ${e.message}"))
        } finally {
            isRunning = false
        }
    }

    override fun toString(): String =
        "JobTask(id=$id, log=$log, name=$name, cronExpression=$cronExpression, isRunning=$isRunning, status=$status, lastRunTime=$lastRunTime, nextRunTime=$nextRunTime)"
}