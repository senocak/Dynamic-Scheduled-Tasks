package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@Component
class HttpMonitorJob: JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "HttpMonitorJob"
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
            val url: String = params!!["url"].toString()
            val connection: HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
            }

            val startTime: Long = System.currentTimeMillis()
            connection.connect()
            val responseTime: Long = System.currentTimeMillis() - startTime

            val statusCode: Int = connection.responseCode
            val message: String = buildString {
                append("URL: $url - ")
                append("Status: $statusCode - ")
                append("Response Time: ${responseTime}ms")
                if (statusCode !in 200..299) {
                    append(" - WARNING: Non-successful status code")
                }
            }
            log.info(message)
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
