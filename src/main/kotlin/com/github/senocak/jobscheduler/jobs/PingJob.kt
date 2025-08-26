package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.LocalDateTime

@Component
class PingJob : JobTask() {
    override fun execute(params: Map<String, Any>?) {
        val host: String = params?.get("host").toString()
        val message: String = when {
            InetAddress.getByName(host).isReachable(3_000) -> "$host is reachable.".also { log.info(it) }
            else -> "$host is not reachable.".also { log.warn(it) }
        }
        log.info("Response:\n$message")
        logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = message))
    }
}