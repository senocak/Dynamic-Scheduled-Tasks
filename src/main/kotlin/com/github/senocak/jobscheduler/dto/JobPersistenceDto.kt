package com.github.senocak.jobscheduler.dto

import com.github.senocak.jobscheduler.model.JobStatus
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

data class JobLogEntry(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val level: String,
    val message: String
)

data class JobRun(
    val startTime: LocalDateTime,
    val logs: MutableList<JobLogEntry> = mutableListOf()
)

data class JobPersistenceDto(
    val id: UUID,
    val cronExpression: String?,
    val isRunning: Boolean,
    val status: JobStatus,
    val lastRunTime: LocalDateTime?,
    val nextRunTime: LocalDateTime?,
    @JsonProperty("className")
    val className: String,
    val enabled: Boolean,
    val runs: List<JobRun> = emptyList()
)

data class JobsFileDto(
    val jobs: List<JobPersistenceDto>
)
