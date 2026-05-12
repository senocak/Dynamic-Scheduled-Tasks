package com.github.senocak.jobscheduler.config

import com.github.senocak.jobscheduler.jobs.JobTask
import com.github.senocak.jobscheduler.logger
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import java.util.UUID
import kotlin.getValue

@Aspect
@Configuration
class ScheduledJobTrackingAspect {
    private val log: Logger by logger()

    @Before(value = "execution(* com.github.senocak.jobscheduler.jobs.JobTask+.executes(..)) && target(jobTask))")
    fun before(joinPoint: JoinPoint, jobTask: JobTask) {
        log.info("BEFORE: ${joinPoint.signature.name} - $jobTask")
    }

    @After(value = "execution(* com.github.senocak.jobscheduler.jobs.JobTask+.executes(..)) && target(jobTask))")
    fun after(joinPoint: JoinPoint, jobTask: JobTask) {
        log.info("AFTER: ${joinPoint.signature.name} - $jobTask")
    }
}
