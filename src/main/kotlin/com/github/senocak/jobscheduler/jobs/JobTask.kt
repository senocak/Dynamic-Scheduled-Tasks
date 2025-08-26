package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime

abstract class JobTask {
    protected val log: Logger by logger()
    open var cronExpression: String? = null
    open var isRunning: Boolean = false
    open var status: JobStatus = JobStatus.SCHEDULED
    open var lastRunTime: LocalDateTime? = null
    open var nextRunTime: LocalDateTime? = null

    protected abstract fun execute(params: Map<String, Any>? = null)

    fun executes(params: Map<String, Any>? = null) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        log.info("Running Job with params: $params, at $lastRunTime")
        try {
            execute(params = params)
            log.info("fireTime: $lastRunTime, nextFireTime: $nextRunTime")
            Thread.sleep(1_000)
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job OperatingSystemJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }

    val sdf: SimpleDateFormat
        get() = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
}