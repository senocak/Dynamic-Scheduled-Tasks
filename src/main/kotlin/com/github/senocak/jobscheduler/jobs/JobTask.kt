package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.model.JobStatus
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.UUID

interface JobTask {
    val id: UUID
    val name: String
    var cronExpression: String?
    var isRunning: Boolean
    var status: JobStatus
    var lastRunTime: LocalDateTime?
    var nextRunTime: LocalDateTime?

    fun execute(params: Map<String, Any>? = null)

    val sdf: SimpleDateFormat
        get() = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
}