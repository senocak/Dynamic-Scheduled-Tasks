package com.github.senocak.jobscheduler.service

import com.github.senocak.jobscheduler.dto.JobPersistenceDto
import com.github.senocak.jobscheduler.dto.JobsFileDto
import com.github.senocak.jobscheduler.logger
import com.github.senocak.jobscheduler.jobs.JobTask
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class JobPersistenceService {
    private val log: Logger by logger()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
    
    private val jobsFile = "jobs.json"
    private val jobsFilePath = "src/main/resources/$jobsFile"

    init {
        objectMapper.findAndRegisterModules()
    }

    fun loadJobsFromResources(): List<JobPersistenceDto> =
        try {
            val resource = ClassPathResource(jobsFile)
            if (resource.exists()) {
                val content: String = String(bytes = resource.inputStream.readBytes())
                val jobsFileDto: JobsFileDto = objectMapper.readValue(content)
                log.info("Loaded ${jobsFileDto.jobs.size} jobs from resources")
                jobsFileDto.jobs
            } else {
                log.warn("Jobs file not found in resources: $jobsFile")
                emptyList()
            }
        } catch (e: Exception) {
            log.error("Failed to load jobs from resources: ${e.message}", e)
            emptyList()
        }

    fun saveJobsToFile(jobs: Map<String, JobTask>) {
        try {
            val jobDtos: List<JobPersistenceDto> = jobs.map { (_: String, job: JobTask) ->
                JobPersistenceDto(
                    id = job.id,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name,
                    enabled = job.enabled,
                )
            }
            val jobsFileDto = JobsFileDto(jobs = jobDtos)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            val file = File(jobsFilePath)
            file.parentFile?.mkdirs()
            Files.write(Paths.get(jobsFilePath), jsonContent.toByteArray())
            log.info("Saved ${jobDtos.size} jobs to file: $jobsFilePath")
        } catch (e: Exception) {
            log.error("Failed to save jobs to file: ${e.message}", e)
        }
    }

    fun updateJobInFile(job: JobTask) {
        try {
            val existingJobs: MutableList<JobPersistenceDto> = loadJobsFromResources().toMutableList()
            val jobIndex: Int = existingJobs.indexOfFirst { it.id == job.id }
            if (jobIndex != -1) {
                existingJobs[jobIndex] = JobPersistenceDto(
                    id = job.id,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name,
                    enabled = job.enabled,
                )
            } else {
                existingJobs.add(JobPersistenceDto(
                    id = job.id,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name,
                    enabled = job.enabled,
                ))
            }
            val jobsFileDto = JobsFileDto(jobs = existingJobs)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            val file = File(jobsFilePath)
            file.parentFile?.mkdirs()
            Files.write(Paths.get(jobsFilePath), jsonContent.toByteArray())
            log.info("Updated job $job in file: $jobsFilePath")
        } catch (e: Exception) {
            log.error("Failed to update job in file: ${e.message}", e)
        }
    }

    fun removeJobFromFile(id: UUID) {
        try {
            val existingJobs: MutableList<JobPersistenceDto> = loadJobsFromResources().toMutableList()
            existingJobs.removeAll { it.id == id }
            val jobsFileDto = JobsFileDto(jobs = existingJobs)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            val file = File(jobsFilePath)
            file.parentFile?.mkdirs()
            Files.write(Paths.get(jobsFilePath), jsonContent.toByteArray())
            log.info("Removed job $id from file: $jobsFilePath")
        } catch (e: Exception) {
            log.error("Failed to remove job from file: ${e.message}", e)
        }
    }
}
