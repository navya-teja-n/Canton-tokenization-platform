import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import { ErrorBanner } from '../components/Banner.jsx'

const EVENT_TYPE_COLOR = {
  CREATED: 'green',
  ARCHIVED: 'red'
}

function EventTypeBadge({ type }) {
  const color = EVENT_TYPE_COLOR[type] || 'grey'
  return <span className={`badge badge-${color}`}>{type}</span>
}

function shortContractId(id) {
  if (!id) return ''
  if (id.length <= 18) return id
  return `${id.slice(0, 8)}...${id.slice(-6)}`
}

function PayloadCell({ payload }) {
  const [open, setOpen] = useState(false)
  if (payload === undefined || payload === null) return <span className="muted">-</span>
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload)
  if (text.length <= 40 && !open) {
    return <span className="mono">{text}</span>
  }
  return (
    <div>
      <button type="button" className="secondary" style={{ padding: '2px 8px', fontSize: 12 }} onClick={() => setOpen((o) => !o)}>
        {open ? 'Hide' : 'View'} payload
      </button>
      {open && <pre className="mono" style={{ whiteSpace: 'pre-wrap', marginTop: 6 }}>{JSON.stringify(payload, null, 2)}</pre>}
    </div>
  )
}

function LedgerEventTable({ events }) {
  if (!events || events.length === 0) return <p className="empty">No ledger events.</p>
  return (
    <table>
      <thead>
        <tr>
          <th>Effective at</th>
          <th>Type</th>
          <th>Template</th>
          <th>Contract id</th>
          <th>Witnesses</th>
          <th>Payload</th>
        </tr>
      </thead>
      <tbody>
        {events.map((ev) => (
          <tr key={ev.eventId || `${ev.contractId}-${ev.effectiveAt}`}>
            <td className="mono">{ev.effectiveAt}</td>
            <td><EventTypeBadge type={ev.type} /></td>
            <td className="mono">{ev.templateId}</td>
            <td className="mono" title={ev.contractId}>{shortContractId(ev.contractId)}</td>
            <td>{Array.isArray(ev.witnessParties) ? ev.witnessParties.join(', ') : ''}</td>
            <td><PayloadCell payload={ev.payload} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function DomainEventTable({ events }) {
  if (!events || events.length === 0) return <p className="empty">No domain events.</p>
  return (
    <table>
      <thead>
        <tr>
          <th>Occurred at</th>
          <th>Topic</th>
          <th>Correlation id</th>
          <th>Details</th>
        </tr>
      </thead>
      <tbody>
        {events.map((ev, i) => {
          const { topic, occurredAt, correlationId, ...rest } = ev
          return (
            <tr key={correlationId ? `${correlationId}-${i}` : i}>
              <td className="mono">{occurredAt}</td>
              <td className="mono">{topic}</td>
              <td className="mono" title={correlationId}>{shortContractId(correlationId)}</td>
              <td><PayloadCell payload={rest} /></td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default function SettlementMonitorPage() {
  const { party } = useParty()
  const [recentLimit, setRecentLimit] = useState(50)

  const { data: partyTx, error: partyTxError, loading: partyTxLoading, reload: reloadPartyTx } = useFetch(
    () => api.transactions.forParty(party),
    [party]
  )
  const { data: auditTx, error: auditTxError, loading: auditTxLoading, reload: reloadAuditTx } = useFetch(
    () => api.transactions.audit(),
    []
  )
  const { data: recentEvents, error: recentEventsError, loading: recentEventsLoading, reload: reloadRecentEvents } = useFetch(
    () => api.transactions.recentEvents(recentLimit),
    [recentLimit]
  )

  function reloadAll() {
    reloadPartyTx()
    reloadAuditTx()
    reloadRecentEvents()
  }

  return (
    <div>
      <div className="page-header">
        <h1>Settlement Monitor</h1>
        <p>
          Reflects the in-memory Canton ledger simulator's Active Contract Set transitions (<span className="mono">CREATED</span> /{' '}
          <span className="mono">ARCHIVED</span> events from <span className="mono">LedgerEvent</span>) and the application
          event bus (<span className="mono">DomainEvent</span> topics published on KYC, wallet, asset, trade and repo
          lifecycle changes).
        </p>
      </div>

      <div className="toolbar section-gap">
        <button className="secondary" onClick={reloadAll}>
          Refresh all
        </button>
      </div>

      <div className="card section-gap">
        <h2>Ledger transactions visible to {party}</h2>
        {partyTxLoading && <p className="empty">Loading...</p>}
        <ErrorBanner error={partyTxError} />
        {!partyTxLoading && <LedgerEventTable events={partyTx} />}
      </div>

      <div className="card section-gap">
        <h2>Full audit stream (regulator view)</h2>
        <p className="muted" style={{ fontSize: 12 }}>
          Every CREATE/ARCHIVE event across all parties, as a regulator with full ledger visibility would observe it.
        </p>
        {auditTxLoading && <p className="empty">Loading...</p>}
        <ErrorBanner error={auditTxError} />
        {!auditTxLoading && <LedgerEventTable events={auditTx} />}
      </div>

      <div className="card section-gap">
        <h2>Recent domain events (event bus)</h2>
        <div className="form-row">
          <label>
            Limit
            <select value={recentLimit} onChange={(e) => setRecentLimit(Number(e.target.value))}>
              {[20, 50, 100, 200].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
        </div>
        {recentEventsLoading && <p className="empty">Loading...</p>}
        <ErrorBanner error={recentEventsError} />
        {!recentEventsLoading && <DomainEventTable events={recentEvents} />}
      </div>
    </div>
  )
}
