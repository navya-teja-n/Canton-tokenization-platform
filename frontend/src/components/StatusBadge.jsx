import React from 'react'

// Maps backend enum values (InstrumentStatus, TradeStatus, RepoStatus,
// KycStatus) to a colour so the dashboard reads at a glance.
const COLOR_BY_STATUS = {
  // InstrumentStatus
  ACTIVE: 'green',
  COLLATERALIZED: 'amber',
  MATURED: 'blue',
  REDEEMED: 'grey',
  DEFAULTED: 'red',
  // TradeStatus
  PROPOSED: 'amber',
  ACCEPTED: 'blue',
  SETTLED: 'green',
  REJECTED: 'red',
  CANCELLED: 'grey',
  // RepoStatus
  REPO_OPEN: 'green',
  REPO_MATURED: 'amber',
  REPO_CLOSED: 'blue',
  REPO_DEFAULTED: 'red',
  // KycStatus
  KYC_PENDING: 'amber',
  KYC_APPROVED: 'green',
  KYC_REJECTED: 'red',
  KYC_REVOKED: 'grey'
}

export default function StatusBadge({ status }) {
  if (!status) return <span className="badge badge-grey">—</span>
  const color = COLOR_BY_STATUS[status] || 'grey'
  return <span className={`badge badge-${color}`}>{status.replaceAll('_', ' ')}</span>
}
