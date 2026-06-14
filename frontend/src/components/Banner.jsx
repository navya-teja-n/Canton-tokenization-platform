import React from 'react'

export function ErrorBanner({ error }) {
  if (!error) return null
  const message = error.detail?.message || error.message || String(error)
  return (
    <div className="error-banner">
      {message}
      {error.correlationId && <span className="muted"> (correlation id: {error.correlationId})</span>}
    </div>
  )
}

export function SuccessBanner({ message }) {
  if (!message) return null
  return <div className="success-banner">{message}</div>
}
