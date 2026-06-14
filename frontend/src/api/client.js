// Thin REST client for the Spring Boot backend (com.canton.platform).
//
// Every mutating call accepts an optional `idempotencyKey`. If omitted, one
// is generated client-side so retries (e.g. a user double-clicking
// "Submit") are safe -- mirroring the Idempotency-Key contract documented
// in docs/API.md and implemented by `web.CommandExecutor` on the backend.

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function request(path, { method = 'GET', body, idempotent = false, query } = {}) {
  const url = new URL(BASE_URL + path)
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v)
    })
  }

  const headers = { 'Content-Type': 'application/json' }
  if (idempotent) headers['Idempotency-Key'] = newIdempotencyKey()

  const res = await fetch(url.toString(), {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })

  const correlationId = res.headers.get('X-Correlation-Id')

  if (!res.ok) {
    let detail
    try {
      detail = await res.json()
    } catch {
      detail = { message: await res.text() }
    }
    const err = new Error(detail?.message || `Request failed: ${res.status} ${res.statusText}`)
    err.status = res.status
    err.detail = detail
    err.correlationId = correlationId
    throw err
  }

  if (res.status === 204) return null
  return res.json()
}

const get = (path, query) => request(path, { method: 'GET', query })
const post = (path, body) => request(path, { method: 'POST', body, idempotent: true })

export const api = {
  baseUrl: BASE_URL,

  // -- KYC ------------------------------------------------------------------
  kyc: {
    get: (applicant) => get(`/api/kyc/${encodeURIComponent(applicant)}`),
    approve: (body) => post('/api/kyc/approve', body),
    reject: (body) => post('/api/kyc/reject', body),
    revoke: (body) => post('/api/kyc/revoke', body)
  },

  // -- Wallets ----------------------------------------------------------------
  wallets: {
    open: (body) => post('/api/wallets', body),
    get: (walletId) => get(`/api/wallets/${encodeURIComponent(walletId)}`),
    forParty: (party) => get(`/api/wallets/party/${encodeURIComponent(party)}`)
  },

  // -- Assets / Deposits --------------------------------------------------------
  assets: {
    issue: (body) => post('/api/assets/issue', body),
    get: (instrumentId) => get(`/api/assets/${encodeURIComponent(instrumentId)}`),
    forOwner: (owner) => get(`/api/assets/owner/${encodeURIComponent(owner)}`),
    issueDeposit: (body) => post('/api/assets/deposits/issue', body),
    getDeposit: (depositId) => get(`/api/assets/deposits/${encodeURIComponent(depositId)}`),
    depositsForOwner: (owner) => get(`/api/assets/deposits/owner/${encodeURIComponent(owner)}`)
  },

  // -- Trades ---------------------------------------------------------------
  trades: {
    propose: (body) => post('/api/trades/propose', body),
    settle: (tradeId, body) => post(`/api/trades/${encodeURIComponent(tradeId)}/settle`, body),
    reject: (tradeId, body) => post(`/api/trades/${encodeURIComponent(tradeId)}/reject`, body),
    cancel: (tradeId, body) => post(`/api/trades/${encodeURIComponent(tradeId)}/cancel`, body),
    getProposal: (tradeId) => get(`/api/trades/${encodeURIComponent(tradeId)}`),
    getExecuted: (tradeId) => get(`/api/trades/${encodeURIComponent(tradeId)}/executed`),
    proposalsForParty: (party) => get(`/api/trades/party/${encodeURIComponent(party)}/proposals`),
    executedForParty: (party) => get(`/api/trades/party/${encodeURIComponent(party)}/executed`)
  },

  // -- Repos ------------------------------------------------------------------
  repos: {
    propose: (body) => post('/api/repos/propose', body),
    accept: (repoId, body) => post(`/api/repos/${encodeURIComponent(repoId)}/accept`, body),
    mature: (repoId, body) => post(`/api/repos/${encodeURIComponent(repoId)}/mature`, body),
    repurchase: (repoId, body) => post(`/api/repos/${encodeURIComponent(repoId)}/repurchase`, body),
    declareDefault: (repoId, body) => post(`/api/repos/${encodeURIComponent(repoId)}/default`, body),
    quote: (repoId, asOfDate) => get(`/api/repos/${encodeURIComponent(repoId)}/quote`, { asOfDate }),
    getProposal: (repoId) => get(`/api/repos/${encodeURIComponent(repoId)}/proposal`),
    getAgreement: (repoId) => get(`/api/repos/${encodeURIComponent(repoId)}/agreement`),
    getClosed: (repoId) => get(`/api/repos/${encodeURIComponent(repoId)}/closed`),
    proposalsForParty: (party) => get(`/api/repos/party/${encodeURIComponent(party)}/proposals`),
    agreementsForParty: (party) => get(`/api/repos/party/${encodeURIComponent(party)}/agreements`),
    closedForParty: (party) => get(`/api/repos/party/${encodeURIComponent(party)}/closed`)
  },

  // -- Transactions / settlement monitor ---------------------------------------
  transactions: {
    forParty: (party) => get(`/api/transactions/party/${encodeURIComponent(party)}`),
    audit: () => get('/api/transactions/audit'),
    recentEvents: (limit = 50) => get('/api/transactions/events/recent', { limit })
  }
}

export default api
