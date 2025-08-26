package com.github.senocak.jobscheduler.dto

import com.github.senocak.jobscheduler.model.JobStatus
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.*

data class JobPersistenceDto(
    val id: String,
    val name: String,
    val cronExpression: String?,
    val isRunning: Boolean,
    val status: JobStatus,
    val lastRunTime: LocalDateTime?,
    val nextRunTime: LocalDateTime?,
    @JsonProperty("className")
    val className: String
)

data class JobsFileDto(
    val jobs: List<JobPersistenceDto>
)
