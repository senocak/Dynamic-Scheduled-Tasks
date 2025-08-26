package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.Month
import java.util.Date

@Component
class TarihteBugunJob : JobTask() {
    override fun execute(params: Map<String, Any>?) {
        sleep(2_000)
        val dataMap: HashMap<String, List<TarihteBugun?>> = hashMapOf()
        val date = Date()
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
        contentDiv.forEachIndexed { index: Int, element: Element ->
            val key: String = when(index) {
                0 -> "olaylar"
                1 -> "dogumlar"
                2 -> "ölümler"
                3 -> "tatiller"
                else -> throw RuntimeException("else")
            }
            dataMap[key] = element.select("li")
                .map { it: Element ->
                    val split: List<String> = it.text().split(" - ")
                    when(split.size) {
                        1 -> TarihteBugun(day = 1, month = Month.of(1), year = null, text = split[0])
                        2 -> TarihteBugun(day = 1, month = Month.of(1), year = split[0], text = split[1])
                        else -> log.info("TarihteBugun is set null").run { null }
                    }
                }
        }
        log.info("Response:\n$dataMap")
        logs.add(element = JobLogEntry(timestamp = LocalDateTime.now(), level = "INFO", message = dataMap.toString()))
    }

    private fun ayAdi(ay: Int): String =
        arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")[ay]
}

data class TarihteBugun(val day: Int, val month: Month, val year: String?, val text: String)
