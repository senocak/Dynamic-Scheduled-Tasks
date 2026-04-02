package com.github.senocak.jobscheduler.jobs

import com.github.senocak.jobscheduler.dto.JobLogEntry
import org.springframework.boot.info.ProcessInfo
import org.springframework.stereotype.Component

@Component
class ProcessInfoJob: JobTask() {
    override fun execute(params: Map<String, Any>?) {
        val pinfo: ProcessInfo = ProcessInfo()
        "Owner: ${pinfo.owner}, Pid: ${pinfo.pid}, ParentPId: ${pinfo.parentPid}, CPU: ${pinfo.cpus}".also { it: String ->
            log.info(it)
            logs.add(element = JobLogEntry(level = "INFO", message = it))
        }
        "[Memory Heap] init: ${pinfo.memory.heap.init}, used: ${pinfo.memory.heap.used}, committed: ${pinfo.memory.heap.committed}, max: ${pinfo.memory.heap.max}".also { it: String ->
            log.info(it)
            logs.add(element = JobLogEntry(level = "INFO", message = it))
        }
        "[Memory NonHeap] init: ${pinfo.memory.nonHeap.init}, used: ${pinfo.memory.nonHeap.used}, committed: ${pinfo.memory.nonHeap.committed}, max: ${pinfo.memory.nonHeap.max}".also { it: String ->
            log.info(it)
            logs.add(element = JobLogEntry(level = "INFO", message = it))
        }
        for (gcInfo: ProcessInfo.MemoryInfo.GarbageCollectorInfo in pinfo.memory.garbageCollectors) {
            "[GarbageCollector] name: ${gcInfo.name}, collectionCount: ${gcInfo.collectionCount}".also { it: String ->
                log.info(it)
                logs.add(element = JobLogEntry(level = "INFO", message = it))
            }
        }
    }
}
