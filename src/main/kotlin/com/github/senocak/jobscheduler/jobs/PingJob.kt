package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@Component
class PingJob : JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "PingJob"
    override var cronExpression: String? = null
    override var isRunning: Boolean = false
    override var status: JobStatus = JobStatus.SCHEDULED
    override var lastRunTime: LocalDateTime? = null
    override var nextRunTime: LocalDateTime? = null

    override fun execute(params: Map<String, Any>?) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        val currentTimeMillis: Long = System.currentTimeMillis()
        val now: String = sdf.format(Timestamp(currentTimeMillis))
        log.info("Running Job with params: $params, at $now")
        try {
            val host: String = params?.get("host").toString()
            val message: String = when {
                InetAddress.getByName(host).isReachable(3_000) -> "$host is reachable.".also { log.info(it) }
                else -> "$host is not reachable.".also { log.warn(it) }
            }
            log.info("fireTime: $now, nextFireTime: $nextRunTime, Response:\n$message")
            Thread.sleep(1_000)
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job OperatingSystemJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }
}