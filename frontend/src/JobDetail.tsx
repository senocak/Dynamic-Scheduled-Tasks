import React, { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api, Job, JobRun, JobLog, TriggerType } from './api'
import { cronFieldCount, formatDateTime, parseJsonObject, relativeTime, shortId, statusClass } from './util'

const REFRESH_INTERVAL_MS = 5000
const CUSTOM = 'Custom'

type Notice = { type: 'success' | 'error'; text: string } | null
type Busy = 'start' | 'stop' | 'schedule' | null

const JobDetail: React.FC = (): React.JSX.Element => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [triggers, setTriggers] = useState<TriggerType[]>([])
  const [paramsText, setParamsText] = useState('')
  const [busy, setBusy] = useState<Busy>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [openRuns, setOpenRuns] = useState<string[] | null>(null)
  const [copied, setCopied] = useState(false)
  // Schedule form: null means "not edited yet, derive from job"
  const [triggerName, setTriggerName] = useState<string | null>(null)
  const [cronText, setCronText] = useState<string | null>(null)

  const load = useCallback(async (silent: boolean = false): Promise<void> => {
    if (!id)
      return
    if (!silent)
      setLoading(true)
    try {
      setJob(await api.getJob(id))
      setError(null)
    } catch (e: any) {
      if (!silent)
        setError(e.message || 'Failed to load job')
    } finally {
      if (!silent)
        setLoading(false)
    }
  }, [id])

  useEffect((): void => {
    load()
  }, [load])

  useEffect((): void => {
    api.listTriggers()
      .then(setTriggers)
      .catch((e: any): void => setTriggers([]))
  }, [])

  useEffect(() => {
    if (!autoRefresh)
      return
    const timer: number = window.setInterval((): void => { load(true) }, REFRESH_INTERVAL_MS)
    return (): void => { window.clearInterval(timer) }
  }, [autoRefresh, load])

  const matchedTrigger: string =
    triggers.find((t: TriggerType): boolean => t.cronExpression === job?.cronExpression)?.displayName || CUSTOM
  const activeTrigger: string = triggerName ?? matchedTrigger
  const activeCron: string = cronText ?? job?.cronExpression ?? ''
  const desiredCron: string = activeTrigger === CUSTOM
    ? activeCron.trim()
    : (triggers.find((t: TriggerType): boolean => t.displayName === activeTrigger)?.cronExpression ?? activeCron.trim())
  const scheduleDirty: boolean = !!job && desiredCron !== (job.cronExpression || '')
  const cronValid: boolean = activeTrigger !== CUSTOM || cronFieldCount(desiredCron) === 6

  const params = parseJsonObject(paramsText)
  const canStop: boolean = !!job && (!!job.isRunning || !!job.enabled)
  const runs: JobRun[] = job?.runs ? [...job.runs].reverse() : []
  const runKey = (run: JobRun, idx: number): string => run.startTime || `run-${idx}`
  // null = user never toggled: keep the newest run open
  const openKeys: string[] = openRuns ?? (runs.length > 0 ? [runKey(runs[0], 0)] : [])

  const toggleRun = (key: string): void => {
    setOpenRuns(openKeys.includes(key) ? openKeys.filter((k: string): boolean => k !== key) : [...openKeys, key])
  }

  const runAction = async (kind: 'start' | 'stop'): Promise<void> => {
    if (!id || !job)
      return
    setBusy(kind)
    setNotice(null)
    try {
      if (kind === 'start') {
        if (!params.ok)
          throw new Error(params.error)
        await api.startJob(id, { params: params.value })
        setNotice({ type: 'success', text: 'Job executed successfully' })
      } else {
        await api.stopJob(id)
        setNotice({ type: 'success', text: 'Job stopped and unscheduled' })
      }
      await load(true)
    } catch (e: any) {
      setNotice({ type: 'error', text: (kind === 'start' ? 'Start failed: ' : 'Stop failed: ') + (e.message || 'unknown error') })
    } finally {
      setBusy(null)
    }
  }

  const saveSchedule = async (): Promise<void> => {
    if (!id || !job)
      return
    setBusy('schedule')
    setNotice(null)
    try {
      if (!cronValid)
        throw new Error('Cron must have 6 fields: second minute hour day-of-month month day-of-week (e.g. 0 */5 * * * *)')
      if (activeTrigger === CUSTOM)
        await api.updateJob(id, { cronExpression: desiredCron })
      else
        await api.updateJob(id, { triggerType: activeTrigger })
      setNotice({ type: 'success', text: 'Schedule updated' })
      await load(true)
    } catch (e: any) {
      setNotice({ type: 'error', text: 'Schedule update failed: ' + (e.message || 'unknown error') })
    } finally {
      setBusy(null)
    }
  }

  const copyId = async (): Promise<void> => {
    if (!job)
      return
    try {
      await navigator.clipboard.writeText(job.id)
      setCopied(true)
      window.setTimeout((): void => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }

  if (loading && !job && !error)
    return (
      <div className="card">
        <div className="page-header"><h2>Job</h2></div>
        <div className="small">Loading job…</div>
      </div>
    )

  if (error && !job)
    return (
      <div className="card">
        <div className="page-header">
          <h2>Job</h2>
          <button className="button" onClick={(): void => { navigate(-1) }}>Back</button>
        </div>
        <div className="banner error" role="alert">
          <span>{error}</span>
          <button className="button small" onClick={(): void => { load() }}>Retry</button>
        </div>
      </div>
    )

  if (!job)
    return <div className="card">Job not found</div>

  const logLine = (log: JobLog, index: number): React.JSX.Element => (
    <div key={index} className={`log-line ${log.level === 'ERROR' ? 'log-error' : 'log-info'}`}>
      <span className="log-time">{log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '—'}</span>
      <span className="log-level">{log.level}</span>
      <span className="log-msg">{log.message}</span>
    </div>
  )

  return (
    <div className="detail">
      <div className="page-header">
        <div className="title-row">
          <button className="button ghost" onClick={(): void => { navigate(-1) }}>← Back</button>
          <div>
            <h2>{job.className || job.name || 'Job'}</h2>
            <div className="title-meta">
              <span className={`badge ${statusClass(job.status)}`}>{job.status || 'UNKNOWN'}</span>
              {job.isRunning && <span className="badge running pulse">running</span>}
              {!job.enabled && <span className="badge muted">scheduling off</span>}
              <span className="small mono" title={job.id}>{shortId(job.id)}</span>
            </div>
          </div>
        </div>
        <div className="toolbar">
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

      <div className="detail-grid">
        <section className="card">
          <h3>Overview</h3>
          <dl className="info-list">
            <dt>Status</dt>
            <dd>{job.status || '—'}{job.isRunning ? ' · running now' : ''}</dd>
            <dt>Schedule</dt>
            <dd><code className="cron">{job.cronExpression || 'manual only'}</code></dd>
            <dt>Next run</dt>
            <dd title={formatDateTime(job.nextRunTime)}>
              {job.cronExpression ? `${relativeTime(job.nextRunTime)} · ${formatDateTime(job.nextRunTime)}` : '—'}
            </dd>
            <dt>Last run</dt>
            <dd title={formatDateTime(job.lastRunTime)}>
              {job.lastRunTime ? `${relativeTime(job.lastRunTime)} · ${formatDateTime(job.lastRunTime)}` : 'never'}
            </dd>
            <dt>Scheduling</dt>
            <dd>{job.enabled ? 'Enabled (resumes on restart)' : 'Off'}</dd>
            <dt>ID</dt>
            <dd>
              <span className="mono">{job.id}</span>
              <button className="button small ghost copy" onClick={copyId} title="Copy job ID">
                {copied ? 'Copied!' : 'Copy'}
              </button>
            </dd>
          </dl>
        </section>

        <section className="card">
          <h3>Schedule</h3>
          <p className="small">Pick a preset or write a custom 6-field cron (second minute hour day month weekday).</p>
          <label className="field">
            <span className="field-label">Trigger</span>
            <select
              className="input"
              value={activeTrigger}
              onChange={(e: React.ChangeEvent<HTMLSelectElement>): void => {
                const v: string = e.target.value
                setTriggerName(v)
                if (v !== CUSTOM)
                  setCronText(null)
              }}
            >
              {triggers.map((t: TriggerType): React.JSX.Element => (
                <option key={t.displayName} value={t.displayName}>{t.displayName}</option>
              ))}
              <option value={CUSTOM}>{CUSTOM}</option>
            </select>
          </label>
          {activeTrigger === CUSTOM && (
            <label className="field">
              <span className="field-label">Cron expression</span>
              <input
                className={`input mono ${cronValid ? '' : 'invalid'}`}
                value={activeCron}
                placeholder="0 */5 * * * *"
                onChange={(e: React.ChangeEvent<HTMLInputElement>): void => {
                  setCronText(e.target.value)
                  setTriggerName(CUSTOM)
                }}
              />
              {!cronValid && <span className="field-hint error-text">Expected 6 fields, got {cronFieldCount(activeCron)}.</span>}
            </label>
          )}
          {activeTrigger !== CUSTOM && (
            <div className="small">
              {triggers.find((t: TriggerType): boolean => t.displayName === activeTrigger)?.description}
              {' → '}
              <code className="cron">{desiredCron}</code>
            </div>
          )}
          <div className="form-actions">
            <button
              className="button primary"
              disabled={busy === 'schedule' || !scheduleDirty || !cronValid}
              title={scheduleDirty ? 'Save the new schedule' : 'No schedule changes yet'}
              onClick={saveSchedule}
            >
              {busy === 'schedule' ? 'Saving…' : 'Save schedule'}
            </button>
            {scheduleDirty && <span className="small">Unsaved changes</span>}
          </div>
        </section>
      </div>

      <section className="card">
        <h3>Run once</h3>
        <p className="small">Parameters are sent as a JSON object when the job runs now.</p>
        <textarea
          className={`input mono params ${params.ok ? '' : 'invalid'}`}
          rows={4}
          placeholder='{"url": "https://example.com", "city": "Istanbul,TR"}'
          value={paramsText}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>): void => setParamsText(e.target.value)}
        />
        {!params.ok && <div className="field-hint error-text">{params.error}</div>}
        <div className="form-actions">
          <button
            className="button primary"
            disabled={busy !== null || !!job.isRunning || !params.ok}
            title={job.isRunning ? 'Job is already running' : 'Run this job immediately'}
            onClick={(): Promise<void> => runAction('start')}
          >
            {busy === 'start' ? 'Running…' : 'Run now'}
          </button>
          <button
            className="button danger"
            disabled={busy !== null || !canStop}
            title={canStop ? 'Stop and unschedule this job' : 'Job is not running or scheduled'}
            onClick={(): Promise<void> => runAction('stop')}
          >
            {busy === 'stop' ? 'Stopping…' : 'Stop job'}
          </button>
        </div>
      </section>

      <section className="card">
        <h3>Runs ({runs.length})</h3>
        {runs.length === 0 ? (
          <div className="empty-state">
            <p className="small">No runs yet. Use “Run now” or wait for the schedule.</p>
          </div>
        ) : (
          <div>
            {runs.map((run: JobRun, idx: number): React.JSX.Element => {
              const key: string = runKey(run, idx)
              const open: boolean = openKeys.includes(key)
              const errors: number = run.logs.filter((l: JobLog): boolean => l.level === 'ERROR').length
              return (
                <div key={key} className="run-card">
                  <button
                    className="run-header"
                    aria-expanded={open}
                    onClick={(): void => toggleRun(key)}
                  >
                    <span className="run-caret">{open ? '▾' : '▸'}</span>
                    <span title={formatDateTime(run.startTime)}>
                      {formatDateTime(run.startTime)} ({relativeTime(run.startTime)})
                    </span>
                    <span className="small">{run.logs.length} log{run.logs.length === 1 ? '' : 's'}</span>
                    {errors > 0 && <span className="badge failed">{errors} error{errors === 1 ? '' : 's'}</span>}
                  </button>
                  {open && (
                    <div className="run-body">
                      {run.logs.length > 0
                        ? run.logs.map(logLine)
                        : <div className="small">No logs for this run.</div>}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
export default JobDetail
