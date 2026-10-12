// Shared display/validation helpers for job data

export function shortId(id?: string): string {
  if (!id) return ''
  return id.length > 10 ? `${id.slice(0, 8)}…` : id
}

export function relativeTime(iso?: string | null): string {
  if (!iso) return '—'
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return '—'
  const diff = t - Date.now()
  const abs = Math.abs(diff)
  const sec = Math.round(abs / 1000)
  let label: string
  if (sec < 60) label = `${sec}s`
  else if (sec < 3600) label = `${Math.round(sec / 60)}m`
  else if (sec < 86400) label = `${Math.round(sec / 3600)}h`
  else label = `${Math.round(sec / 86400)}d`
  return diff >= 0 ? `in ${label}` : `${label} ago`
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function statusClass(status?: string): string {
  return (status || 'unknown').toLowerCase()
}

export type JsonParse = { ok: true; value: Record<string, any> } | { ok: false; error: string }

export function parseJsonObject(text: string): JsonParse {
  const trimmed = (text || '').trim()
  if (!trimmed) return { ok: true, value: {} }
  try {
    const parsed: unknown = JSON.parse(trimmed)
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed))
      return { ok: true, value: parsed as Record<string, any> }
    return { ok: false, error: 'Params must be a JSON object, e.g. {"host": "https://example.com"}' }
  } catch (e) {
    return { ok: false, error: `Invalid JSON: ${(e as Error).message}` }
  }
}

// Spring cron is 6 fields: second minute hour day-of-month month day-of-week
export function cronFieldCount(cron: string): number {
  return cron.trim().split(/\s+/).filter((f: string) => f.length > 0).length
}
