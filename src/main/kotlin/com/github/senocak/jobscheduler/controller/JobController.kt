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

@RestController
@RequestMapping(value = ["/jobs"])
class JobController(
    private val jobSchedulerService: JobSchedulerService
) {
    @GetMapping
    fun getAllJobs(): List<JobResponse> =
        jobSchedulerService.getAllJobs()

    @GetMapping(value = ["/{name}"])
    fun getJob(@PathVariable name: String): JobResponse =
        jobSchedulerService.getJob(name = name) ?: throw NoSuchElementException("Job with name $name not found")

    @PutMapping(value = ["/{name}/start"])
    fun startJob(@PathVariable name: String, @RequestBody(required = false) request: JobStartRequest?): Map<String, Any> =
        when {
            jobSchedulerService.startJob(name = name, params = request?.params) ->
                mapOf("message" to "Job started successfully", "jobName" to name)
            else -> mapOf("error" to "Failed to start job", "jobName" to name)
        }

    @PutMapping(value = ["/{name}/stop"])
    fun stopJob(@PathVariable name: String): Map<String, Any> =
        when {
            jobSchedulerService.stopJob(name = name) -> mapOf("message" to "Job stopped successfully", "jobName" to name)
            else -> mapOf("error" to "Failed to stop job", "jobName" to name)
        }

    @PutMapping(value = ["/{name}"])
    fun updateJob(@PathVariable name: String, @RequestBody request: JobUpdateRequest): Map<String, Any> =
        when {
            jobSchedulerService.updateJob(name = name, cronExpression = request.cronExpression, triggerType = request.triggerType, newName = request.name) ->
                mapOf("message" to "Job updated successfully", "jobName" to name)
            else -> mapOf("error" to "Failed to update job", "jobName" to name)
        }

    @DeleteMapping(value = ["/{name}"])
    fun removeJob(@PathVariable name: String): Map<String, Any> =
        when {
            jobSchedulerService.removeJob(name = name) -> mapOf("message" to "Job removed successfully", "jobName" to name)
            else -> mapOf("error" to "Failed to remove job", "jobName" to name)
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
