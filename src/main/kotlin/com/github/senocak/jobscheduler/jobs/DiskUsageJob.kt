package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDateTime

@Component
class DiskUsageJob: JobTask() {
    private val WARNING_THRESHOLD = 90.0 // Warning when disk usage is above 90%
    private val CRITICAL_THRESHOLD = 95.0 // Critical warning when disk usage is above 95%

    override fun execute(params: Map<String, Any>?) {
        Thread.sleep(20_000)
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
        logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = message))
        log.info("Disk Usage Information:\n$message")
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
