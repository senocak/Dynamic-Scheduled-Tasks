package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.springframework.stereotype.Component
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.UUID
import com.sun.management.OperatingSystemMXBean
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.sql.Timestamp

@Component
class OperatingSystemJob : JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "OperatingSystemJob"
    override var cronExpression: String? = null
    override var isRunning: Boolean = false
    override var status: JobStatus = JobStatus.SCHEDULED
    override var lastRunTime: LocalDateTime? = null
    override var nextRunTime: LocalDateTime? = null

    private val operatingSystemMXBean: OperatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    private val byte = 1L
    private val kb: Long = byte * 1000
    private val mb: Long = kb * 1000
    private val gb: Long = mb * 1000
    private val tb: Long = gb * 1000

    override fun execute(params: Map<String, Any>?) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        val currentTimeMillis: Long = System.currentTimeMillis()
        val now: String = sdf.format(Timestamp(currentTimeMillis))
        log.info("Running Job with params: $params, at $now")
        try {
            val runtime: Runtime = Runtime.getRuntime()
            val performance = Performance(
                timestamp = currentTimeMillis,
                committedVirtualMemorySize = operatingSystemMXBean.committedVirtualMemorySize,
                totalSwapSpaceSize = operatingSystemMXBean.totalSwapSpaceSize,
                freeSwapSpaceSize = operatingSystemMXBean.freeSwapSpaceSize,
                totalMemorySize = operatingSystemMXBean.totalMemorySize,
                freeMemorySize = operatingSystemMXBean.freeMemorySize,
                cpuLoad = operatingSystemMXBean.cpuLoad,
                processCpuLoad = operatingSystemMXBean.processCpuLoad,
                availableProcessors = runtime.availableProcessors(),
                totalMemory = toHumanReadableSIPrefixes(size = runtime.totalMemory()),
                maxMemory = toHumanReadableSIPrefixes(size = runtime.maxMemory()),
                freeMemory = toHumanReadableSIPrefixes(size = runtime.freeMemory())
            )
            log.info("fireTime: $now, nextFireTime: $nextRunTime, Performance:\n$performance")
            Thread.sleep(1_000)
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job OperatingSystemJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }

    private fun toHumanReadableSIPrefixes(size: Long): String =
        when {
            size >= tb -> formatSize(size = size, divider = tb, unitName = "TB")
            size >= gb -> formatSize(size = size, divider = gb, unitName = "GB")
            size >= mb -> formatSize(size = size, divider = mb, unitName = "MB")
            size >= kb -> formatSize(size = size, divider = kb, unitName = "KB")
            else -> formatSize(size = size, divider = byte, unitName = "Bytes")
        }
    private fun formatSize(size: Long, divider: Long, unitName: String): String =
        DecimalFormat("#.##").format(size.toDouble() / divider) + " " + unitName
}

data class Performance(
    var timestamp: Long = 0,
    var committedVirtualMemorySize: Long = 0,
    var totalSwapSpaceSize: Long = 0,
    var freeSwapSpaceSize: Long = 0,
    var totalMemorySize: Long = 0,
    var freeMemorySize: Long = 0,
    var cpuLoad: Double = 0.0,
    var processCpuLoad: Double = 0.0,
    var availableProcessors: Int = 0,
    var totalMemory: String = "",
    var maxMemory: String = "",
    var freeMemory: String = "",
)