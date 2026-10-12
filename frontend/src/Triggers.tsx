import React, { useEffect, useState } from 'react'
import { api, TriggerType } from './api'

const Triggers: React.FC = (): React.JSX.Element => {
  const [triggers, setTriggers] = useState<TriggerType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [copiedCron, setCopiedCron] = useState<string | null>(null)

  useEffect((): void => {
    api.listTriggers()
      .then(setTriggers)
      .catch((e: any): void => setError(e.message || 'Failed to load triggers'))
      .finally((): void => setLoading(false))
  }, [])

  const copy = async (cron: string): Promise<void> => {
    try {
      await navigator.clipboard.writeText(cron)
      setCopiedCron(cron)
      window.setTimeout((): void => setCopiedCron(null), 1500)
    } catch {
      setCopiedCron(null)
    }
  }

  if (loading)
    return <div className="small">Loading triggers…</div>

  if (error)
    return <div className="banner error" role="alert">{error}</div>

  if (triggers.length === 0)
    return <div className="small">No triggers available.</div>

  return (
    <div className="trigger-table" role="table" aria-label="Available triggers">
      <div className="trigger-row head" role="row">
        <div className="trigger-cell" role="columnheader">Trigger</div>
        <div className="trigger-cell" role="columnheader">Cron</div>
        <div className="trigger-cell" role="columnheader">Description</div>
        <div className="trigger-cell" role="columnheader"><span className="sr-only">Copy</span></div>
      </div>
      {triggers.map((t: TriggerType): React.JSX.Element => (
        <div className="trigger-row" role="row" key={t.displayName}>
          <div className="trigger-cell" role="cell"><strong>{t.displayName}</strong></div>
          <div className="trigger-cell" role="cell"><code className="cron">{t.cronExpression}</code></div>
          <div className="trigger-cell small" role="cell">{t.description}</div>
          <div className="trigger-cell actions" role="cell">
            <button
              className="button small ghost"
              onClick={(): Promise<void> => copy(t.cronExpression)}
              title="Copy cron expression"
            >
              {copiedCron === t.cronExpression ? 'Copied!' : 'Copy'}
            </button>
          </div>
        </div>
      ))}
      <p className="small" style={{ marginTop: 8 }}>
        Select a preset on the job’s Schedule card, or paste a custom expression there.
      </p>
    </div>
  )
}
export default Triggers
