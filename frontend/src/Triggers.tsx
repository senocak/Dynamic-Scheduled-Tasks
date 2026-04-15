import React, { useEffect, useState } from 'react'
import { api } from './api'

type TriggersProps = {
  onClose?: () => void
}

const Triggers: React.FC<TriggersProps> = ({ onClose }: TriggersProps): React.JSX.Element => {
  const [triggers, setTriggers] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect((): void => {
    api.listTriggers()
      .then(setTriggers)
      .catch((e: any): void => setError(e.message))
      .finally((): void => setLoading(false))
  }, [])
  if (loading) return <div className="card">Loading triggers...</div>
  if (error) return <div className="card">Error: {error}</div>
  return (
    <div className="card triggers-content">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>Job Triggers</h2>
        <div>
          {onClose && (
            <button className="button" onClick={onClose} aria-label="Close triggers">Close</button>
          )}
        </div>
      </div>

      {triggers.length === 0 ? (
        <div className="small">No triggers found.</div>
      ) : (
        <pre style={{ whiteSpace: 'pre-wrap', marginTop: 8 }}>{JSON.stringify(triggers, null, 2)}</pre>
      )}
    </div>
  )
}
export default Triggers