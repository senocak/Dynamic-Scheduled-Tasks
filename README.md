## Dynamic Scheduled Tasks (Kotlin + Spring Boot)

This project is a lightweight, extensible job scheduler built with Kotlin and Spring Boot. It auto-discovers job classes, persists their configuration and lightweight logs as JSON, and exposes a REST API to manage and trigger jobs at runtime.

### Core Concepts

- **JobTask (abstract class)**: The base type all jobs extend. It encapsulates common lifecycle behavior and metadata.
  - `id: UUID`: Unique identifier for each job instance (supports multiple instances of the same class).
  - `cronExpression: String?`: Optional scheduling rule; when set and the job is enabled, it is scheduled automatically.
  - `status: JobStatus`: SCHEDULED | RUNNING | COMPLETED | FAILED.
  - `isRunning: Boolean`: Runtime flag.
  - `lastRunTime`, `nextRunTime`: Execution timestamps.
  - `enabled: Boolean`: Whether the job should be (re)scheduleable on startup.
  - `logs: List<JobLogEntry>`: Lightweight structured log entries persisted to JSON (START/COMPLETED/FAILED with timestamps).
  - `execute(params)`: Implement your job’s core work here.
  - `executes(params)`: Public wrapper that manages lifecycle, timestamps, status, logging, and error capture.

- **JobStatus (enum)**: Represents the current state of a job: SCHEDULED, RUNNING, COMPLETED, FAILED.

- **TriggerType (enum)**: Predefined, human-friendly scheduling options (e.g., Every minute, Every hour) that map to cron expressions.

### Architecture Overview

1. **Discovery**: On startup, Spring discovers all `JobTask` beans. The `JobSchedulerService` loads `jobs.json` and applies persisted state (id, cron, status, next/last times, enabled flag) per job class.
2. **Registration**: Jobs are registered in-memory keyed by `UUID` only, allowing multiple instances of the same job class.
3. **Scheduling**: If a job is `enabled` and has a `cronExpression`, it is scheduled via Spring’s `TaskScheduler` and `CronTrigger`.
4. **Execution**: When a job runs (scheduled or via API), `executes` updates status, timestamps, persists state, and appends a log entry. Errors set status to FAILED and are logged.
5. **Persistence**: Job state and logs are stored in `src/main/resources/jobs.json` using Jackson (Kotlin + JavaTime modules). Updates occur after state changes (e.g., start, stop, schedule, calculate next run time).

### Persistence Schema (jobs.json)

Each job record includes:
- `id` (UUID), `className` (FQCN), `enabled` (Boolean)
- `cronExpression` (String?), `isRunning` (Boolean), `status` (Enum)
- `lastRunTime` (ISO datetime), `nextRunTime` (ISO datetime?)
- `logs`: List of `{ timestamp, level, message }` entries

This format is designed for clarity and portability, not high-volume logging. For heavy logs, forward to a dedicated log sink.

### Scheduling Logic

- Uses `CronExpression.parse(cron)` to compute `nextRunTime` on schedule/after run.
- `TaskScheduler.schedule` with a `CronTrigger` executes the job on schedule.
- Changing a job’s `cronExpression` via API cancels any prior schedule and registers a new one.

### REST API (IDs are UUIDs)

- `GET /jobs`: List all jobs (name is presented as `<JobClassSimpleName>:<id>` for readability).
- `GET /jobs/{id}`: Fetch one job.
- `PUT /jobs/{id}/start`: Run immediately with optional params body.
- `PUT /jobs/{id}/stop`: Mark a running job as stopped (status→SCHEDULED, isRunning=false).
- `PUT /jobs/{id}`: Update cron/trigger type and reschedule if changed. You can also toggle `enabled` via payload if you extend the DTO accordingly.
- `DELETE /jobs/{id}`: Remove job from memory and persistence.
- `GET /jobs/triggers`: List easy trigger presets with display names and cron expressions.
- `POST /jobs/save`: Force-save all in-memory jobs to `jobs.json`.

All endpoints return JSON. IDs uniquely address job instances; multiple instances of the same class are supported.

### Adding a New Job

1. Create a class extending `JobTask` and annotate with `@Component`:
   - Override `execute(params)` with your business logic.
   - Optionally set a default `cronExpression` and `enabled = true` to start scheduled.
2. On application start, the job will be discovered and registered. If there’s an entry for its `className` in `jobs.json`, that configuration (including `id`) will be applied.

Example sketch:
```kotlin
@Component
class MyJob: JobTask() {
    override fun execute(params: Map<String, Any>?) {
        // Your job logic
    }
}
```

### Job Logs

- Minimal, structured logs are appended to each job’s in-memory buffer during lifecycle events and persisted with state updates.
- Each log entry: `{ timestamp: ISO, level: INFO|ERROR, message: String }`.
- Intended for audit and quick inspection; not a replacement for centralized logging.

### Quick Start

1. Java 21+, Kotlin, Gradle installed.
2. Run:
```bash
./gradlew bootRun
```
3. Explore APIs (default `http://localhost:8080`):
   - `GET /jobs`
   - `GET /jobs/{id}`
   - `PUT /jobs/{id}/start` with optional JSON body `{ "params": { "k": "v" } }`
   - `PUT /jobs/{id}` to update cron or a trigger type
   - `GET /jobs/triggers`

### Design Rationale

- **UUID-centric identity**: Clean separation between a job’s type (class) and its instance identity. Supports parallel instances of the same job class.
- **JSON persistence**: Simple, human-readable state for demos and small deployments. Swap with DB if needed.
- **Encapsulated lifecycle**: `executes` centralizes state transitions and logging to keep job implementations focused on business logic.
- **Predefined triggers**: Friendly options for common schedules; still supports custom cron when needed.

### Extensibility

- Replace JSON with a repository (e.g., JPA) by implementing a new persistence service.
- Add validation, rate limits, or concurrency controls per job.
- Introduce job scoping/tenancy by grouping IDs.
- Wire observability (metrics/tracing) around `executes`.

# Dynamic Scheduled Tasks - Kotlin Spring Boot Job Scheduler

A Kotlin Spring Boot application that provides a dynamic job scheduling system with REST API endpoints for managing scheduled tasks.

## Features

- **Dynamic Job Discovery**: Automatically discovers and registers `JobTask` implementations from Spring context
- **Cron-based Scheduling**: Supports cron expressions for automatic job scheduling
- **Manual Job Execution**: Run jobs on-demand via REST API
- **Job Lifecycle Management**: Start, stop, update, and remove jobs
- **Real-time Status Tracking**: Monitor job status, last run time, and next scheduled run
- **Thread-safe Operations**: Uses `ConcurrentHashMap` for thread-safe job storage

## Project Structure

```
src/main/kotlin/com/example/jobscheduler/
├── Application.kt                    # Main Spring Boot application
├── config/
│   └── SchedulingConfig.kt          # TaskScheduler configuration
├── controller/
│   └── JobController.kt             # REST API endpoints
├── dto/
│   └── JobRequest.kt                # API request/response DTOs
├── jobs/
│   ├── SampleJobOne.kt              # Example job implementation
│   └── SampleJobTwo.kt              # Example job implementation
├── model/
│   ├── JobStatus.kt                 # Job status enum
│   └── JobTask.kt                   # Job task interface
└── service/
    └── JobSchedulerService.kt       # Core job scheduling logic
```

## Job Status
- **SCHEDULED**: Job is registered and ready to run
- **RUNNING**: Job is currently executing
- **COMPLETED**: Job finished successfully
- **FAILED**: Job encountered an error during execution

## Cron Expression Examples
- `0 */5 * * * *` - Every 5 minutes
- `0 0 */2 * * *` - Every 2 hours
- `0 0 9 * * *` - Every day at 9 AM
- `0 0 9 * * MON` - Every Monday at 9 AM

## Technical Details

- **Thread Safety**: Uses `ConcurrentHashMap` for job storage
- **Scheduling**: Leverages Spring's `TaskScheduler` with `CronTrigger`
- **Auto-discovery**: Uses Spring's `ApplicationContext` to find `JobTask` beans
- **Error Handling**: Comprehensive error handling with proper status updates
- **Logging**: Structured logging with SLF4J
