package com.github.senocak.jobscheduler.controller

import com.github.senocak.jobscheduler.dto.JobResponse
import com.github.senocak.jobscheduler.dto.JobStartRequest
import com.github.senocak.jobscheduler.dto.JobUpdateRequest
import com.github.senocak.jobscheduler.dto.TriggerTypeResponse
import com.github.senocak.jobscheduler.service.JobSchedulerService
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@RestController
@RequestMapping(value = ["/jobs"])
class JobController(
    private val jobSchedulerService: JobSchedulerService
) {
    @GetMapping
    fun getAllJobs(): List<JobResponse> =
        jobSchedulerService.getAllJobs()

    @GetMapping(value = ["/{id}"])
    fun getJob(@PathVariable id: UUID): JobResponse =
        jobSchedulerService.getJobById(id = id) ?: throw NoSuchElementException("Job with id $id not found")

    @PutMapping(value = ["/{id}/start"])
    fun startJob(@PathVariable id: UUID, @RequestBody(required = false) request: JobStartRequest?): Map<String, Any> =
        when {
            jobSchedulerService.startJobById(id = id, params = request?.params) ->
                mapOf("message" to "Job started successfully", "jobId" to id)
            else -> mapOf("error" to "Failed to start job", "jobId" to id)
        }

    @PutMapping(value = ["/{id}/stop"])
    fun stopJob(@PathVariable id: UUID): Map<String, Any> =
        when {
            jobSchedulerService.stopJobById(id = id) -> mapOf("message" to "Job stopped successfully", "jobId" to id)
            else -> mapOf("error" to "Failed to stop job", "jobId" to id)
        }

    @PutMapping(value = ["/{id}"])
    fun updateJob(@PathVariable id: UUID, @RequestBody request: JobUpdateRequest): Map<String, Any> =
        when {
            jobSchedulerService.updateJobById(id = id, cronExpression = request.cronExpression, triggerType = request.triggerType, newName = request.name) ->
                mapOf("message" to "Job updated successfully", "jobId" to id)
            else -> mapOf("error" to "Failed to update job", "jobId" to id)
        }

    @DeleteMapping(value = ["/{id}"])
    fun removeJob(@PathVariable id: UUID): Map<String, Any> =
        when {
            jobSchedulerService.removeJobById(id = id) -> mapOf("message" to "Job removed successfully", "jobId" to id)
            else -> mapOf("error" to "Failed to remove job", "jobId" to id)
        }

    @PostMapping(value = ["/save"])
    fun saveAllJobs(): Map<String, Any> {
        jobSchedulerService.saveAllJobs()
        return mapOf("message" to "All jobs saved to file successfully")
    }

    @GetMapping(value = ["/triggers"])
    fun getAvailableTriggerTypes(): List<TriggerTypeResponse> =
        jobSchedulerService.getAvailableTriggerTypes()
}
