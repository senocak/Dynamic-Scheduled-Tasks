package com.github.senocak.jobscheduler.service

import com.example.jobscheduler.dto.JobPersistenceDto
import com.example.jobscheduler.dto.JobsFileDto
import com.example.jobscheduler.logger
import com.example.jobscheduler.model.JobTask
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

@Service
class JobPersistenceService {
    private val log: Logger by logger()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
    
    private val jobsFile = "jobs.json"
    private val jobsFilePath = "src/main/resources/$jobsFile"

    init {
        // Configure ObjectMapper for better JSON handling
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

    fun saveJobsToFile(jobs: List<JobTask>) {
        try {
            val jobDtos: List<JobPersistenceDto> = jobs.map { job: JobTask ->
                JobPersistenceDto(
                    id = job.id.toString(),
                    name = job.name,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name
                )
            }
            val jobsFileDto = JobsFileDto(jobs = jobDtos)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            
            // Ensure the directory exists
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
            // Find and update the job
            val jobIndex: Int = existingJobs.indexOfFirst { it.id == job.id.toString() }
            if (jobIndex != -1) {
                existingJobs[jobIndex] = JobPersistenceDto(
                    id = job.id.toString(),
                    name = job.name,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name
                )
            } else {
                // Add new job if not found
                existingJobs.add(JobPersistenceDto(
                    id = job.id.toString(),
                    name = job.name,
                    cronExpression = job.cronExpression,
                    isRunning = job.isRunning,
                    status = job.status,
                    lastRunTime = job.lastRunTime,
                    nextRunTime = job.nextRunTime,
                    className = job::class.java.name
                ))
            }
            
            val jobsFileDto = JobsFileDto(jobs = existingJobs)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            
            val file = File(jobsFilePath)
            file.parentFile?.mkdirs()
            Files.write(Paths.get(jobsFilePath), jsonContent.toByteArray())
            
            log.info("Updated job ${job.name} in file: $jobsFilePath")
        } catch (e: Exception) {
            log.error("Failed to update job in file: ${e.message}", e)
        }
    }

    fun removeJobFromFile(jobId: String) {
        try {
            val existingJobs: MutableList<JobPersistenceDto> = loadJobsFromResources().toMutableList()
            existingJobs.removeAll { it.id == jobId }
            
            val jobsFileDto = JobsFileDto(jobs = existingJobs)
            val jsonContent: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsFileDto)
            
            val file = File(jobsFilePath)
            file.parentFile?.mkdirs()
            Files.write(Paths.get(jobsFilePath), jsonContent.toByteArray())
            
            log.info("Removed job $jobId from file: $jobsFilePath")
        } catch (e: Exception) {
            log.error("Failed to remove job from file: ${e.message}", e)
        }
    }
}
