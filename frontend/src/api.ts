// API client for Job Scheduler backend
import axios, { AxiosInstance } from 'axios'

const API_BASE_URL: string = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8090';

const client: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

client.interceptors.response.use(
  res => res,
  err => {
    // Normalize error message
    if (err.response && err.response.data) {
      const msg: any = err.response.data.message || JSON.stringify(err.response.data);
      return Promise.reject(new Error(msg));
    }
    return Promise.reject(err);
  }
);

export interface Job {
  id: string;
  name?: string;
  cronExpression?: string;
  isRunning?: boolean;
  status?: string;
  lastRunTime?: string | null;
  nextRunTime?: string | null;
  className?: string;
  enabled?: boolean;
  runs?: JobRun[];
}

export interface JobRun {
  startTime: string | null;
  logs: JobLog[];
}

export interface JobLog {
  timestamp: string | null;
  level: string;
  message: string;
}

export interface JobStartParams {
  params: Record<string, any>;
}

// Matches backend JobUpdateRequest (NOT params)
export interface JobScheduleUpdate {
  cronExpression?: string;
  triggerType?: string;
  name?: string;
}

// Matches backend TriggerTypeResponse
export interface TriggerType {
  displayName: string;
  cronExpression: string;
  description: string;
}

function extractIdFromName(name?: string): string | undefined {
  if (!name) return undefined;
  const parts = name.split(':');
  if (parts.length >= 2) return parts[1];
  return undefined;
}

function convertTimestamp(raw: any): string | null {
  // Backend may return an ISO string or an array like [year, month, day, hour, minute, second, nanos]
  if (!raw)
    return null;
  if (typeof raw === 'string')
    return raw;
  if (Array.isArray(raw) && raw.length >= 6) {
    const year: any = raw[0];
    const month: any = raw[1];
    const day: any = raw[2];
    const hour: any = raw[3] ?? 0;
    const minute: any = raw[4] ?? 0;
    const second: any = raw[5] ?? 0;
    const nanos: any = raw[6] ?? 0;
    const millisFromNanos: number = Math.round(nanos / 1e6);
    const date = new Date(Date.UTC(year, month - 1, day, hour, minute, second, millisFromNanos));
    return date.toISOString();
  }
  return null;
}

function toJob(raw: any): Job {
  // Backend may return `id` or a `name` like "ClassName:UUID". Ensure we always have `id`.
  const id: any = raw.id || extractIdFromName(raw.name) || (raw.name ?? '');
  const runsRaw: any = raw.runs ?? raw.history ?? [];
  const runs: JobRun[] = Array.isArray(runsRaw)
    ? runsRaw.map((r: any) => {
        const logsRaw: any = r.logs ?? r.log ?? [];
        const logs: JobLog[] = Array.isArray(logsRaw)
          ? logsRaw.map((l: any) => ({
              timestamp: convertTimestamp(l.timestamp ?? l.time ?? null),
              level: l.level ?? 'INFO',
              message: l.message ?? l.msg ?? '',
            }))
          : [];
        return {
          startTime: convertTimestamp(r.startTime ?? r.started ?? null),
          logs,
        };
      })
    : [];

  return {
    id,
    name: raw.name,
    cronExpression: raw.cronExpression ?? raw.cron ?? undefined,
    isRunning: raw.isRunning ?? raw.running ?? false,
    status: raw.status ?? undefined,
    lastRunTime: convertTimestamp(raw.lastRunTime ?? raw.lastRun ?? null),
    nextRunTime: convertTimestamp(raw.nextRunTime ?? raw.nextRun ?? null),
    className: raw.className ?? (raw.name ? raw.name.split(':')[0] : undefined),
    enabled: raw.enabled ?? true,
    runs,
  };
}

async function fetchJob(id: string): Promise<Job> {
  const res = await client.get(`/jobs/${id}`);
  return toJob(res.data);
}

// Mutation endpoints return {message} or {error} (HTTP 200), not a Job.
// Surface `error` as a rejection, then re-fetch the real job state.
async function mutateJob(id: string, path: string, body?: unknown): Promise<Job> {
  const res = await client.put(path, body ?? {});
  const data = res.data;
  if (data && typeof data === 'object' && !Array.isArray(data) && (data as any).error)
    throw new Error(String((data as any).error));
  return fetchJob(id);
}

export const api = {
  listJobs: async (): Promise<Job[]> => {
    const res = await client.get(`/jobs`);
    const data = res.data as any[];
    return Array.isArray(data) ? data.map(toJob) : [];
  },
  getJob: fetchJob,
  updateJob: (id: string, update: JobScheduleUpdate): Promise<Job> =>
    mutateJob(id, `/jobs/${id}`, update),
  startJob: (id: string, params: JobStartParams): Promise<Job> =>
    mutateJob(id, `/jobs/${id}/start`, params),
  stopJob: (id: string): Promise<Job> =>
    mutateJob(id, `/jobs/${id}/stop`),
  listTriggers: async (): Promise<TriggerType[]> => {
    const res = await client.get(`/jobs/triggers`);
    const data = res.data as any[];
    return Array.isArray(data) ? data : [];
  },
};
