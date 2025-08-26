package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

@Component
class WeatherInformationJob(
    private val restTemplate: RestTemplate
): JobTask() {
    private val API_URL = "http://api.weatherstack.com/current"

    override fun execute(params: Map<String, Any>?) {
        Thread.sleep(10_000)
        val location: String = params?.get("location")?.toString() ?: "Istanbul,TR"
        val response: WeatherResponse? = restTemplate.getForObject("$API_URL?access_key=46aa57b9789c0b758c19e10e06fdea04&query=$location",
            WeatherResponse::class.java)
        log.info("Weather information for $location: $response")
        logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = response.toString()))
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
