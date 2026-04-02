package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics
import org.springframework.boot.info.ProcessInfo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class VirtualThreadsInfoJob(
    private val meterRegistry: MeterRegistry
): JobTask() {
    override fun execute(params: Map<String, Any>?) {
        val submitFailed: Double = getMetric("jvm.threads.virtual.submit.failed")
        val pinnedThreads: Double = getMetric("jvm.threads.virtual.pinned")
        "Virtual Thread Stats: Submit Failed: ${submitFailed.toInt()}, Pinned Events: ${pinnedThreads.toInt()}".also { it: String ->
            log.info(it)
            logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = it))
        }
    }

    private fun getMetric(gaugeName: String): Double {
        val meter = meterRegistry.find(gaugeName).gauge()
        return if (meter != null) meter.value() else 0.0
    }
}
