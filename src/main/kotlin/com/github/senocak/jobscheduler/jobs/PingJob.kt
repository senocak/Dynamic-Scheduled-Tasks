package com.github.senocak.jobscheduler.jobs

import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class PingJob : JobTask() {
    override fun execute(params: Map<String, Any>?) {
        val host: String = params?.get("host").toString()
        val message: String = when {
            InetAddress.getByName(host).isReachable(3_000) -> "$host is reachable.".also { log.info(it) }
            else -> "$host is not reachable.".also { log.warn(it) }
        }
        log.info("Ping:\n$message")
        addLog(level = "INFO", message = message)
    }
}