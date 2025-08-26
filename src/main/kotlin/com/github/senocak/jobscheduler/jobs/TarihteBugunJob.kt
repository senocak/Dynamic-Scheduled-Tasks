package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.model.JobStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.Month
import java.util.Date
import java.util.UUID

@Component
class TarihteBugunJob : JobTask {
    private val log: Logger by logger()
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "TarihteBugunJob"
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
        sleep(2_000)
        val dataMap: HashMap<String, List<TarihteBugun?>> = hashMapOf()
        val date = Date()
        try {
            val day: String = SimpleDateFormat("dd").format(date)
            val month: Int = SimpleDateFormat("M").format(date).toInt()
            val urlToScrap = "https://tr.wikipedia.org/wiki/${day}_${ayAdi(ay = month - 1)}"
            log.info("urlToScrap: $urlToScrap")
            val doc: Document = Jsoup.connect(urlToScrap).get()
            val contentDiv: Elements = doc.select("div[class='mw-content-ltr mw-parser-output']>ul")
            if (contentDiv.size != 4) {
                log.error("Invalid size: ${contentDiv.size} of data found")
                return
            }
            contentDiv.forEachIndexed {
                    index: Int, element: Element ->
                val key: String = when(index) {
                    0 -> "olaylar"
                    1 -> "dogumlar"
                    2 -> "ölümler"
                    3 -> "tatiller"
                    else -> throw RuntimeException("else")
                }
                dataMap[key] = element.select("li")
                    .map { it: Element ->
                        val split: List<String> = it.text().split(" - ") ?: listOf(it.text())
                        when(split.size) {
                            1 -> TarihteBugun(day = 1, month = Month.of(1), year = null, text = split[0])
                            2 -> TarihteBugun(day = 1, month = Month.of(1), year = split[0], text = split[1])
                            else -> log.info("TarihteBugun is set null").run { null }
                        }
                    }
            }
            log.info("fireTime: $now, nextFireTime: $nextRunTime, Response:\n$dataMap")
            sleep(1_000)
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            log.error("Job OperatingSystemJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }

    private fun ayAdi(ay: Int): String =
        arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")[ay]
}

data class TarihteBugun(val day: Int, val month: Month, val year: String?, val text: String)
