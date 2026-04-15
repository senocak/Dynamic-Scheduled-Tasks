import React, { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api, Job, JobUpdateParams, JobRun, JobLog } from './api'
import {NavigateFunction} from "react-router"

const JobDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate: NavigateFunction = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updateParams, setUpdateParams] = useState('')
  const [actionMsg, setActionMsg] = useState<string | null>(null)

  const load: () => void = (): void => {
    if (!id)
      return
    setLoading(true)
    setError(null)
    api.getJob(id)
      .then(setJob)
      .catch((e: any) => setError(e.message || 'Failed to load job'))
      .finally((): void => setLoading(false))
  }
  useEffect((): void => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const parseParamsSafe: (text: string) => Record<string, any> = (text: string): Record<string, any> => {
    if (!text || text.trim() === '') return {}
    try {
      const p: any = JSON.parse(text)
      if (typeof p === 'object' && p !== null)
        return p
      return {}
    } catch {
      return {}
    }
  }

  const handleUpdate: () => Promise<void> = async () => {
    if (!id)
      return
    const parsed: Record<string, any> = parseParamsSafe(updateParams)
    try {
      const params: JobUpdateParams = { params: parsed }
      const updated: Job = await api.updateJob(id, params)
      setJob(updated)
      setActionMsg('Job updated!')
    } catch (e: any) {
      setActionMsg('Update failed: ' + (e.message || e.toString()))
    }
  }

  const handleStart: () => Promise<void> = async (): Promise<void> => {
    if (!id)
      return
    const parsed: Record<string, any> = parseParamsSafe(updateParams)
    try {
      const params: JobUpdateParams = { params: parsed }
      const started: Job = await api.startJob(id, params)
      setJob(started)
      setActionMsg('Job started!')
    } catch (e: any) {
      setActionMsg('Start failed: ' + (e.message || e.toString()))
    }
  }

  const handleStop: () => Promise<void> = async (): Promise<void> => {
    if (!id)
      return
    try {
      const stopped: Job = await api.stopJob(id)
      setJob(stopped)
      setActionMsg('Job stopped!')
    } catch (e: any) {
      setActionMsg('Stop failed: ' + (e.message || e.toString()))
    }
  }

  const formatTime: (iso: (string | null)) => string = (iso: string | null): string => {
    if (!iso)
      return '-'
    try {
      const d = new Date(iso)
      return d.toLocaleString()
    } catch {
      return iso
    }
  }

  const renderLogLine: (log: JobLog, index: number) => React.JSX.Element = (log: JobLog, index: number): React.JSX.Element => (
    <div key={index} className="log-line">
      <span className="small">[{log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '-'}]</span>
      &nbsp;
      <strong className="small">{log.level}</strong>
      &nbsp;-&nbsp;
      <span>{log.message}</span>
    </div>
  )
  if (loading) return <div className="card">Loading job...</div>
  if (error) return <div className="card">Error: {error}</div>
  if (!job) return <div className="card">Job not found</div>
  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>Job Detail</h2>
        <div>
          <button className="button" onClick={() => navigate(-1)}>Back</button>
          <button className="button" onClick={load}>Refresh</button>
        </div>
      </div>

      <div style={{ marginTop: 8 }}>
        <strong>ID:</strong> {job.id}
      </div>
      <div className="small"><strong>Class:</strong> {job.className}</div>
      <div className="small"><strong>Cron:</strong> {job.cronExpression}</div>
      <div className="small"><strong>Status:</strong> {job.status} {job.isRunning ? ' · Running' : ''}</div>

      <h3 style={{ marginTop: 12 }}>Runs</h3>
      {(!job.runs || job.runs.length === 0) ? (
        <div className="small">No runs available.</div>
      ) : (
        <div>
          {job.runs.map((run: JobRun, idx: number): React.JSX.Element => (
            <div key={idx} className="run-card">
              <div style={{ marginBottom: 6 }}><strong>Start:</strong> {formatTime(run.startTime)}</div>
              <div>
                {run.logs && run.logs.length > 0 ? (
                  run.logs.map((l: JobLog, i: number): React.JSX.Element => renderLogLine(l, i))
                ) : (
                  <div className="small">No logs for this run.</div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <h3>Actions</h3>
      <div>
        <textarea
          rows={4}
          cols={60}
          placeholder='{"host": "http://google.com"}'
          value={updateParams}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement>) => setUpdateParams(e.target.value)}
        />
      </div>

      <div style={{ marginTop: 8 }}>
        <button className="button" onClick={handleUpdate}>Update Job</button>
        <button className="button primary" onClick={handleStart}>Start Job</button>
        <button className="button" onClick={handleStop}>Stop Job</button>
      </div>

      {actionMsg && <div style={{ marginTop: 8 }}>{actionMsg}</div>}
    </div>
  )
}
export default JobDetail