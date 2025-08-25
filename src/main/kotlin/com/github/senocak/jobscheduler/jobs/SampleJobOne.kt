package com.github.senocak.jobscheduler.jobs

import com.example.jobscheduler.model.JobStatus
import com.example.jobscheduler.model.JobTask
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*

@Component
class SampleJobOne : JobTask {
    override val id: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    override val name: String = "SampleJobOne"
    override var cronExpression: String? = null
    override var isRunning: Boolean = false
    override var status: JobStatus = JobStatus.SCHEDULED
    override var lastRunTime: LocalDateTime? = null
    override var nextRunTime: LocalDateTime? = null

    override fun execute(params: Map<String, Any>?) {
        try {
            isRunning = true
            status = JobStatus.RUNNING
            lastRunTime = LocalDateTime.now()
            
            println("Running Job One with params: $params")
            
            // Simulate some work
            Thread.sleep(1000)
            
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            println("Job One failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }
}
