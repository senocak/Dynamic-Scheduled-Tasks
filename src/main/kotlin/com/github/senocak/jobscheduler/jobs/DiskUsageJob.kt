package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@Component
class DiskUsageJob: JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "DiskUsageJob"
    override var cronExpression: String? = null
    override var isRunning: Boolean = false
    override var status: JobStatus = JobStatus.SCHEDULED
    override var lastRunTime: LocalDateTime? = null
    override var nextRunTime: LocalDateTime? = null

    private val WARNING_THRESHOLD = 90.0 // Warning when disk usage is above 90%
    private val CRITICAL_THRESHOLD = 95.0 // Critical warning when disk usage is above 95%

    override fun execute(params: Map<String, Any>?) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        val currentTimeMillis: Long = System.currentTimeMillis()
        val now: String = sdf.format(Timestamp(currentTimeMillis))
        log.info("Running Job with params: $params, at $now")
        try {
            val diskUsageInfo: List<DiskInfo> = File.listRoots().map { root: File ->
                val totalSpace: Long = root.totalSpace
                val usableSpace: Long = root.usableSpace
                val usedSpace: Long = totalSpace - usableSpace
                val usagePercentage: Double = (usedSpace.toDouble() / totalSpace.toDouble()) * 100
                when {
                    usagePercentage >= CRITICAL_THRESHOLD ->
                        log.error("CRITICAL: Disk usage for ${root.absolutePath} is at $usagePercentage%")
                    usagePercentage >= WARNING_THRESHOLD ->
                        log.warn("WARNING: Disk usage for ${root.absolutePath} is at $usagePercentage%")
                }
                DiskInfo(
                    path = root.absolutePath,
                    totalSpace = formatSize(totalSpace),
                    usableSpace = formatSize(usableSpace),
                    usedSpace = formatSize(usedSpace),
                    usagePercentage = String.format("%.2f", usagePercentage)
                )
            }
            val message: String = diskUsageInfo.joinToString(separator = "\n") { info: DiskInfo ->
                "Path: ${info.path}, Total: ${info.totalSpace}, Used: ${info.usedSpace}, Available: ${info.usableSpace}, Usage: ${info.usagePercentage}% "
            }
            log.info("fireTime: $now, nextFireTime: $nextRunTime, Disk Usage Information:\n$message")
            Thread.sleep(1_000)
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job DiskUsageJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }

    private fun formatSize(size: Long): String {
        val units: Array<String> = arrayOf("B", "KB", "MB", "GB", "TB")
        var value: Double = size.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return String.format(format = "%.2f %s", value, units[unitIndex])
    }
}

data class DiskInfo(
    val path: String,
    val totalSpace: String,
    val usableSpace: String,
    val usedSpace: String,
    val usagePercentage: String
)
