package com.github.senocak.jobscheduler.dto

import com.github.senocak.jobscheduler.model.JobStatus
import java.time.LocalDateTime

data class JobStartRequest(
    val params: Map<String, Any>? = null
)

data class JobUpdateRequest(
    val cronExpression: String? = null,
    val triggerType: String? = null,
    val name: String? = null
)

data class JobResponse(
    val name: String,
    val cronExpression: String?,
    val isRunning: Boolean,
    val status: JobStatus,
    val lastRunTime: LocalDateTime?,
    val nextRunTime: LocalDateTime?,
    val enabled: Boolean,
    val runs: List<JobRun> = emptyList()
)

data class TriggerTypeResponse(
    val displayName: String,
    val cronExpression: String,
    val description: String
)
