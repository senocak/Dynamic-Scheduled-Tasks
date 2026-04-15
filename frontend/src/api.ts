// API client for Job Scheduler backend
import axios, {AxiosInstance} from 'axios'

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

export interface JobUpdateParams {
  params: Record<string, any>;
}

function extractIdFromName(name?: string): string | undefined {
  if (!name) return undefined;
  const parts = name.split(':');
  if (parts.length >= 2) return parts[1];
  return undefined;
}

function convertTimestamp(raw: any): string | null {
  // raw can be ISO string or an array like [year, month, day, hour, minute, second, nanos]
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
    // Construct a Date in local timezone (use ISO by creating Date.UTC)
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

export const api = {
  listJobs: async (): Promise<Job[]> => {
    const res = await client.get(`/jobs`);
    const data = res.data as any[];
    return Array.isArray(data) ? data.map(toJob) : [];
  },
  getJob: async (id: string): Promise<Job> => {
    const res = await client.get(`/jobs/${id}`);
    return toJob(res.data);
  },
  updateJob: async (id: string, params: JobUpdateParams): Promise<Job> => {
    const res = await client.put(`/jobs/${id}`, params);
    return toJob(res.data);
  },
  startJob: async (id: string, params: JobUpdateParams): Promise<Job> => {
    const res = await client.put(`/jobs/${id}/start`, params);
    return toJob(res.data);
  },
  stopJob: async (id: string): Promise<Job> => {
    const res = await client.put(`/jobs/${id}/stop`);
    return toJob(res.data);
  },
  listTriggers: async (): Promise<any[]> => {
    const res = await client.get(`/jobs/triggers`);
    return res.data;
  },
};
