package com.github.senocak.jobscheduler.dto

import com.example.jobscheduler.model.JobStatus
import java.time.LocalDateTime
import java.util.*

data class JobStartRequest(
    val params: Map<String, Any>? = null
)

data class JobUpdateRequest(
    val cronExpression: String? = null,
    val name: String? = null
)

data class JobResponse(
    val id: UUID,
    val name: String,
    val cronExpression: String?,
    val isRunning: Boolean,
    val status: JobStatus,
    val lastRunTime: LocalDateTime?,
    val nextRunTime: LocalDateTime?
)
