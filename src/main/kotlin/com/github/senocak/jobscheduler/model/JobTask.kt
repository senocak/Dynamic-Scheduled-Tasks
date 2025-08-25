package com.github.senocak.jobscheduler.model

import java.time.LocalDateTime
import java.util.*

interface JobTask {
    val id: UUID
    val name: String
    var cronExpression: String?
    var isRunning: Boolean
    var status: JobStatus
    var lastRunTime: LocalDateTime?
    var nextRunTime: LocalDateTime?
    
    fun execute(params: Map<String, Any>? = null)
}
