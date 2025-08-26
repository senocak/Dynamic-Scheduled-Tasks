package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.slf4j.Logger
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@Component
class WeatherInformationJob(
    private val restTemplate: RestTemplate
): JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "WeatherInformationJob"
    override var cronExpression: String? = null
    override var isRunning: Boolean = false
    override var status: JobStatus = JobStatus.SCHEDULED
    override var lastRunTime: LocalDateTime? = null
    override var nextRunTime: LocalDateTime? = null
    private val API_URL = "http://api.weatherstack.com/current"

    override fun execute(params: Map<String, Any>?) {
        isRunning = true
        status = JobStatus.RUNNING
        lastRunTime = LocalDateTime.now()
        val currentTimeMillis: Long = System.currentTimeMillis()
        val now: String = sdf.format(Timestamp(currentTimeMillis))
        log.info("Running Job with params: $params, at $now")
        val location: String = params?.get("location")?.toString() ?: "Istanbul,TR" // Default to Istanbul if no location is specified
        try {
            val response: WeatherResponse? = restTemplate.getForObject("$API_URL?access_key=46aa57b9789c0b758c19e10e06fdea04&query=$location",
                WeatherResponse::class.java)
            log.info("Weather information for $location: $response")
            log.info("fireTime: $now, nextFireTime: $nextRunTime, Location: $location, Response:\n$response")
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

data class WeatherResponse(
    val request: Request? = null,
    val location: Location? = null,
    val current: Current? = null,
    val success: Boolean? = null,
    val error: Error? = null,
)

data class Error(
    val code: Int,
    val type: String,
    val info: String,
)

data class Request(
    val type: String,
    val query: String,
    val language: String,
    val unit: String
)

data class Location(
    val name: String,
    val country: String,
    val region: String,
    val lat: String,
    val lon: String,
    val timezone_id: String,
    val localtime: String,
    val localtime_epoch: Long,
    val utc_offset: String
)

data class Current(
    val observation_time: String,
    val temperature: Int,
    val weather_code: Int,
    val weather_icons: List<String>,
    val weather_descriptions: List<String>,
    val wind_speed: Int,
    val wind_degree: Int,
    val wind_dir: String,
    val pressure: Int,
    val precip: Int,
    val humidity: Int,
    val cloudcover: Int,
    val feelslike: Int,
    val uv_index: Int,
    val visibility: Int,
    val is_day: String
)
