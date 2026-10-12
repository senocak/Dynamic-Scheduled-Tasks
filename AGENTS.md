# AGENTS.md

## Structure
- Two independent apps, **not** wired together:
  - Kotlin + Spring Boot backend at repo root (single Gradle project, no `settings.gradle`). Code: `src/main/kotlin/com/github/senocak/jobscheduler/`.
  - React 19 / TypeScript (CRA, `react-scripts`) frontend in `frontend/` — its own npm project; Gradle never builds it.
- Backend entrypoint: `Application.kt`. REST API base path `/jobs` in `controller/JobController.kt`.
- Jobs live in `jobs/`: any `@Component` extending `JobTask` is auto-discovered on `ApplicationReadyEvent` by `JobSchedulerService.initializeJobs()` and matched to persisted state in `jobs.json` by FQCN.
- `config/SchedulingConfig.kt` is a `@Configuration` **and** a servlet filter; it defines the `taskScheduler` bean (`@Qualifier("taskScheduler")` is required when injecting it).

## Commands (verified)
Backend (JDK 21 toolchain, Gradle 9.6 wrapper):
- `./gradlew compileKotlin` — fast compile check
- `./gradlew test` — passes (there are **no backend tests**, `src/test` does not exist)
- `./gradlew bootRun` — runs on port 8090

Frontend (run inside `frontend/`):
- `npm run build` — the only real gate; type-checks + lints + bundles
- `npm start` — dev server on :3000
- `CI=true npm test -- --watchAll=false` — **exits 1: no frontend tests exist**; add `--passWithNoTests` or treat as informational

There is no ktlint/spotless/checkstyle task and no CI workflow (.github absent). Verification order: `./gradlew build` → `cd frontend && npm run build`.

## Runtime gotchas
- Backend port is **8090** (`SERVER_PORT` default in `application.yml`); CORS allows only `http://localhost:3000`. Frontend calls `REACT_APP_API_BASE_URL` (default `http://localhost:8090`).
- `JobPersistenceService` **reads** `jobs.json` from the classpath but **writes** to the relative path `src/main/resources/jobs.json` (cwd-dependent). Running the app from the repo root will modify the tracked `src/main/resources/jobs.json`; expect dirty git state after exercising jobs and do not commit that churn unintentionally. In Docker/k8s the path doesn't resolve to a volume, so runtime state is not persisted.
- Jobs are keyed by `UUID` (multiple instances of one class allowed). API responses expose `name = "ClassName:uuid"`; the frontend extracts the UUID from `name` (`frontend/src/api.ts`), so keep that format stable.
- Cron is Spring's **6-field** format (`0 * * * * *` = every minute), not 5-field Unix cron. Presets are in `model/TriggerType.kt`.
- `JobTask.executes()` is the lifecycle wrapper (status/timestamps/`runs`) and sleeps 1s after `execute`; it is advised by `config/ScheduledJobTrackingAspect.kt` (logs BEFORE/AFTER). Put job logic in `execute()`, never override `executes()`.
- Profile `local` swaps in a trust-all SSL `RestClient` (`SchedulingConfig`). Virtual threads are enabled (`spring.threads.virtual.enabled: true`).
- Sample REST calls: `src/main/resources/generated-requests.http`.

## Docs drift
- `README.md` is partly stale: it documents a `logs` list on `JobTask`; the real model is `runs: MutableList<JobRun>` where each run holds `logs`. Trust the code over the README.

## Deploy
- `docker compose up --build` → backend on :8090. `Dockerfile` `EXPOSE 8080` is cosmetic/mismatched; k8s sets `SERVER_PORT=8080`.
- K8s: `docker build -t springboot-kotlin-k8s -f Dockerfile .` then `kubectl apply -f k8s/.`. Deployment uses `imagePullPolicy: Never`, so the image must exist in the cluster's local runtime.
