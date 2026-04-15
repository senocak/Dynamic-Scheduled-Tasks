import React, { useEffect, useState } from 'react'
import { api, Job } from './api'
import { Link } from 'react-router-dom'

const JobList: React.FC = (): React.JSX.Element => {
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load: () => void = (): void => {
    setLoading(true)
    setError(null)
    api.listJobs()
      .then(setJobs)
      .catch((e: any) => setError(e.message || 'Failed to load jobs'))
      .finally((): void => setLoading(false))
  }
  useEffect((): void => {
    load()
  }, [])
  if (loading) return <div className="card">Loading jobs...</div>
  if (error) return <div className="card">Error: {error}</div>

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>Jobs</h2>
        <div>
          <button className="button" onClick={load}>Refresh</button>
        </div>
      </div>

      {jobs.length === 0 ? (
        <div className="small">No jobs found.</div>
      ) : (
        <ul className="job-list">
          {jobs.map((job: Job) => (
              <li key={job.id}>
              <Link to={`/jobs/${job.id}`}>{job.className} ({job.id})</Link>
              <div className="small">{job.cronExpression} {job.isRunning ? ' · Running' : ''}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
export default JobList