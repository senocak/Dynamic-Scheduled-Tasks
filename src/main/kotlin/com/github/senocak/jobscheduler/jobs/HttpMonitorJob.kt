package com.github.senocak.jobscheduler.jobs

import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URL

@Component
class HttpMonitorJob: JobTask() {
    override fun execute(params: Map<String, Any>?) {
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
        addLog(level = "INFO", message = message)
        log.info("Monitoring Result: $message")
    }
}
