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

## Getting Started

### Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher

### Running the Application

1. **Clone and navigate to the project directory:**
   ```bash
   cd dynamic-scheduled-tasks
   ```

2. **Build the project:**
   ```bash
   ./gradlew build
   ```

3. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

The application will start on `http://localhost:8080`

## API Endpoints

### Get All Jobs
```http
GET /jobs
```

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "SampleJobOne",
    "cronExpression": null,
    "isRunning": false,
    "status": "SCHEDULED",
    "lastRunTime": null,
    "nextRunTime": null
  }
]
```

### Get Job by ID
```http
GET /jobs/{id}
```

### Start Job Immediately
```http
PUT /jobs/{id}/start
Content-Type: application/json

{
  "params": {
    "param1": "value1",
    "param2": 42
  }
}
```

### Stop Running Job
```http
PUT /jobs/{id}/stop
```

### Update Job
```http
PUT /jobs/{id}
Content-Type: application/json

{
  "cronExpression": "0 */5 * * * *",
  "name": "Updated Job Name"
}
```

### Remove Job
```http
DELETE /jobs/{id}
```

## Creating Custom Jobs

To create a new job, implement the `JobTask` interface and annotate with `@Component`:

```kotlin
@Component
class MyCustomJob : JobTask {
    override val id: UUID = UUID.randomUUID()
    override val name: String = "MyCustomJob"
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
            
            // Your job logic here
            println("Running MyCustomJob with params: $params")
            
            status = JobStatus.COMPLETED
        } catch (e: Exception) {
            status = JobStatus.FAILED
            println("MyCustomJob failed: ${e.message}")
        } finally {
            isRunning = false
        }
    }
}
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

## Example Usage

1. **Start the application** and check available jobs:
   ```bash
   curl http://localhost:8080/jobs
   ```

2. **Run a job immediately**:
   ```bash
   curl -X PUT http://localhost:8080/jobs/{job-id}/start \
     -H "Content-Type: application/json" \
     -d '{"params": {"message": "Hello World"}}'
   ```

3. **Schedule a job with cron**:
   ```bash
   curl -X PUT http://localhost:8080/jobs/{job-id} \
     -H "Content-Type: application/json" \
     -d '{"cronExpression": "0 */2 * * * *"}'
   ```

## Technical Details

- **Thread Safety**: Uses `ConcurrentHashMap` for job storage
- **Scheduling**: Leverages Spring's `TaskScheduler` with `CronTrigger`
- **Auto-discovery**: Uses Spring's `ApplicationContext` to find `JobTask` beans
- **Error Handling**: Comprehensive error handling with proper status updates
- **Logging**: Structured logging with SLF4J

## Building and Testing

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Create executable JAR
./gradlew bootJar
```

The application will be available at `http://localhost:8080` with all REST endpoints ready to use.
