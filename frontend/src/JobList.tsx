import React, { useCallback, useEffect, useState } from 'react'
import { api, Job } from './api'
import { Link } from 'react-router-dom'
import { formatDateTime, relativeTime, shortId, statusClass } from './util'

const REFRESH_INTERVAL_MS = 5000

type Notice = { type: 'success' | 'error'; text: string } | null

const JobList: React.FC = (): React.JSX.Element => {
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice>(null)

  const load = useCallback(async (silent: boolean = false): Promise<void> => {
    if (!silent)
      setLoading(true)
    try {
      setJobs(await api.listJobs())
      setError(null)
    } catch (e: any) {
      if (!silent)
        setError(e.message || 'Failed to load jobs')
    } finally {
      if (!silent)
        setLoading(false)
    }
  }, [])

  useEffect((): void => {
    load()
  }, [load])

  useEffect(() => {
    if (!autoRefresh)
      return
    const timer: number = window.setInterval((): void => { load(true) }, REFRESH_INTERVAL_MS)
    return (): void => { window.clearInterval(timer) }
  }, [autoRefresh, load])

  const act = async (id: string, fn: () => Promise<Job>, successText: string): Promise<void> => {
    setBusyId(id)
    setNotice(null)
    try {
      await fn()
      setNotice({ type: 'success', text: successText })
      await load(true)
    } catch (e: any) {
      setNotice({ type: 'error', text: e.message || 'Action failed' })
    } finally {
      setBusyId(null)
    }
  }

  const runJob = (job: Job): Promise<void> =>
    act(job.id, (): Promise<Job> => api.startJob(job.id, { params: {} }), `${job.className || 'Job'} started`)

  const stopJob = (job: Job): Promise<void> =>
    act(job.id, (): Promise<Job> => api.stopJob(job.id), `${job.className || 'Job'} stopped`)

  const q: string = query.trim().toLowerCase()
  const filtered: Job[] = q === ''
    ? jobs
    : jobs.filter((job: Job): boolean =>
        [job.className, job.name, job.id, job.status, job.cronExpression]
          .some((v?: string): boolean => !!v && v.toLowerCase().includes(q)))

  if (loading && jobs.length === 0 && !error)
    return (
      <div className="card">
        <div className="page-header"><h2>Jobs</h2></div>
        <div className="small">Loading jobs…</div>
      </div>
    )

  return (
    <div className="card">
      <div className="page-header">
        <div>
          <h2>Jobs</h2>
          <div className="small">{jobs.length} registered · {filtered.length} shown</div>
        </div>
        <div className="toolbar">
          <input
            className="input search"
            type="search"
            placeholder="Search name, status, cron…"
            aria-label="Search jobs"
            value={query}
            onChange={(e: React.ChangeEvent<HTMLInputElement>): void => setQuery(e.target.value)}
          />
          <label className="checkbox" title={`Refresh every ${REFRESH_INTERVAL_MS / 1000}s`}>
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e: React.ChangeEvent<HTMLInputElement>): void => setAutoRefresh(e.target.checked)}
            />
            Auto-refresh
          </label>
          <button className="button" onClick={(): void => { setNotice(null); load() }}>Refresh</button>
        </div>
      </div>

      {notice && (
        <div className={`banner ${notice.type}`} role="status">
          <span>{notice.text}</span>
          <button className="banner-close" aria-label="Dismiss" onClick={(): void => setNotice(null)}>×</button>
        </div>
      )}

      {error && (
        <div className="banner error" role="alert">
          <span>Could not load jobs: {error}</span>
          <button className="button small" onClick={(): void => { load() }}>Retry</button>
        </div>
      )}

      {filtered.length === 0 ? (
        <div className="empty-state">
          <p><strong>{jobs.length === 0 ? 'No jobs yet.' : 'No jobs match your search.'}</strong></p>
          <p className="small">
            {jobs.length === 0
              ? 'Jobs appear here automatically when a @Component class extending JobTask is registered.'
              : 'Try a different name, status, or cron expression.'}
          </p>
        </div>
      ) : (
        <div className="job-table" role="table" aria-label="Jobs">
          <div className="job-row head" role="row">
            <div className="job-cell" role="columnheader">Job</div>
            <div className="job-cell" role="columnheader">Status</div>
            <div className="job-cell" role="columnheader">Schedule</div>
            <div className="job-cell" role="columnheader">Last run</div>
            <div className="job-cell" role="columnheader">Actions</div>
          </div>
          {filtered.map((job: Job): React.JSX.Element => {
            const busy: boolean = busyId === job.id
            const canStop: boolean = !!job.isRunning || !!job.enabled
            return (
              <div className="job-row" role="row" key={job.id}>
                <div className="job-cell" role="cell">
                  <Link to={`/jobs/${job.id}`} className="job-title">
                    {job.className || job.name || 'Job'}
                  </Link>
                  <span className="small mono" title={job.id}>{shortId(job.id)}</span>
                </div>
                <div className="job-cell" role="cell">
                  <span className={`badge ${statusClass(job.status)}`}>{job.status || 'UNKNOWN'}</span>
                  {job.isRunning && <span className="badge running pulse">running</span>}
                  {!job.enabled && <span className="badge muted">scheduling off</span>}
                </div>
                <div className="job-cell" role="cell">
                  <code className="cron">{job.cronExpression || 'manual only'}</code>
                  <span className="small" title={formatDateTime(job.nextRunTime)}>
                    Next: {job.cronExpression ? relativeTime(job.nextRunTime) : '—'}
                  </span>
                </div>
                <div className="job-cell" role="cell">
                  <span className="small" title={formatDateTime(job.lastRunTime)}>
                    {job.lastRunTime ? relativeTime(job.lastRunTime) : 'never'}
                  </span>
                </div>
                <div className="job-cell actions" role="cell">
                  <button
                    className="button small"
                    disabled={busy || !!job.isRunning}
                    title={job.isRunning ? 'Job is already running' : 'Run once now (no params)'}
                    onClick={(): Promise<void> => runJob(job)}
                  >
                    {busy ? '…' : 'Run'}
                  </button>
                  <button
                    className="button small danger"
                    disabled={busy || !canStop}
                    title={canStop ? 'Stop and unschedule this job' : 'Job is not running or scheduled'}
                    onClick={(): Promise<void> => stopJob(job)}
                  >
                    Stop
                  </button>
                  <Link className="button small" to={`/jobs/${job.id}`}>Details</Link>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
export default JobList
