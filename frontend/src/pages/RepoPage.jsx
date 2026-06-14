import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty, KNOWN_PARTIES } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import StatusBadge from '../components/StatusBadge.jsx'
import { ErrorBanner, SuccessBanner } from '../components/Banner.jsx'
import { formatMoney, formatQty } from '../components/Money.jsx'

const DIRECTIONS = ['REPO', 'REVERSE_REPO']

function today() {
  return new Date().toISOString().slice(0, 10)
}

function todayPlusDays(days) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function emptyProposeForm(party) {
  return {
    borrower: party,
    lender: 'BankB',
    regulator: 'RegulatorFed',
    repoId: `REPO-${Math.floor(Math.random() * 10000)}`,
    direction: 'REPO',
    collateralInstrumentId: '',
    collateralQty: '100',
    principalCurrency: 'USD',
    principalAmount: '95000',
    repoRatePct: '5.00',
    startDate: today(),
    maturityDate: todayPlusDays(30)
  }
}

function emptyAcceptForm() {
  return {
    repoId: '',
    accepter: '',
    borrowerWalletId: '',
    lenderWalletId: '',
    settledCollateralInstrumentId: '',
    settledCashDepositId: ''
  }
}

function emptyLifecycleForm() {
  return {
    repoId: '',
    asOfDate: today(),
    repaymentDepositId: '',
    returnedInstrumentId: '',
    releasedInstrumentId: '',
    borrowerWalletId: '',
    lenderWalletId: '',
    actor: ''
  }
}

export default function RepoPage() {
  const { party } = useParty()
  const [proposeForm, setProposeForm] = useState(() => emptyProposeForm(party))
  const [acceptForm, setAcceptForm] = useState(emptyAcceptForm)
  const [lifecycleForm, setLifecycleForm] = useState(emptyLifecycleForm)
  const [quoteResult, setQuoteResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const { data: proposals, error: proposalsError, loading: proposalsLoading, reload: reloadProposals } = useFetch(
    () => api.repos.proposalsForParty(party),
    [party]
  )
  const { data: agreements, error: agreementsError, loading: agreementsLoading, reload: reloadAgreements } = useFetch(
    () => api.repos.agreementsForParty(party),
    [party]
  )
  const { data: closed, error: closedError, loading: closedLoading, reload: reloadClosed } = useFetch(
    () => api.repos.closedForParty(party),
    [party]
  )

  function reloadAll() {
    reloadProposals()
    reloadAgreements()
    reloadClosed()
  }

  async function handlePropose(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const payload = {
        borrower: proposeForm.borrower,
        lender: proposeForm.lender,
        regulator: proposeForm.regulator,
        repoId: proposeForm.repoId,
        direction: proposeForm.direction,
        collateralInstrumentId: proposeForm.collateralInstrumentId,
        collateralQty: Number(proposeForm.collateralQty),
        principal: { currency: proposeForm.principalCurrency, amount: Number(proposeForm.principalAmount) },
        repoRatePct: Number(proposeForm.repoRatePct),
        startDate: proposeForm.startDate,
        maturityDate: proposeForm.maturityDate
      }
      const proposal = await api.repos.propose(payload)
      setSuccess(`Repo ${proposal.repoId} proposed (${proposal.direction}): borrower=${proposal.borrower}, lender=${proposal.lender}`)
      setAcceptForm((f) => ({ ...f, repoId: proposal.repoId, accepter: proposal.direction === 'REPO' ? proposal.lender : proposal.borrower }))
      setLifecycleForm((f) => ({ ...f, repoId: proposal.repoId }))
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleAccept(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const { repoId, ...body } = acceptForm
      const agreement = await api.repos.accept(repoId, body)
      setSuccess(`Repo ${repoId} accepted: collateral locked, principal disbursed. Status=${agreement.status}`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleQuote() {
    setBusy(true)
    setError(null)
    setQuoteResult(null)
    try {
      const result = await api.repos.quote(lifecycleForm.repoId, lifecycleForm.asOfDate)
      setQuoteResult(result)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleMature() {
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const agreement = await api.repos.mature(lifecycleForm.repoId, { asOfDate: lifecycleForm.asOfDate })
      setSuccess(`Repo ${lifecycleForm.repoId} marked matured. Status=${agreement.status}`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleRepurchase() {
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const { repoId, asOfDate, repaymentDepositId, returnedInstrumentId, borrowerWalletId, lenderWalletId } = lifecycleForm
      const record = await api.repos.repurchase(repoId, {
        borrower: party,
        asOfDate,
        repaymentDepositId,
        returnedInstrumentId,
        borrowerWalletId,
        lenderWalletId
      })
      setSuccess(`Repo ${repoId} repurchased. Interest paid: ${formatMoney({ currency: record.principal.currency, amount: record.interestPaid })}`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleDefault() {
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const { repoId, asOfDate, releasedInstrumentId, lenderWalletId } = lifecycleForm
      const record = await api.repos.declareDefault(repoId, {
        lender: party,
        asOfDate,
        releasedInstrumentId,
        lenderWalletId
      })
      setSuccess(`Repo ${repoId} closed as defaulted. Outcome=${record.outcome}`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Repo Desk</h1>
        <p>
          Mirrors DAML <span className="mono">Repo.Repo</span>: <span className="mono">propose -&gt; accept</span>{' '}
          (atomic DvP: collateral locked with the lender, principal cash delivered to the borrower) {' '}
          <span className="mono">-&gt; [mature] -&gt; repurchase | default</span>.
        </p>
      </div>

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="grid grid-2">
        <div className="card">
          <h2>Propose repo / reverse repo</h2>
          <form onSubmit={handlePropose}>
            <div className="form-row">
              <label>
                Direction
                <select value={proposeForm.direction} onChange={(e) => setProposeForm((f) => ({ ...f, direction: e.target.value }))}>
                  {DIRECTIONS.map((d) => (
                    <option key={d} value={d}>
                      {d.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Repo id
                <input value={proposeForm.repoId} onChange={(e) => setProposeForm((f) => ({ ...f, repoId: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Borrower (posts collateral, receives cash)
                <select value={proposeForm.borrower} onChange={(e) => setProposeForm((f) => ({ ...f, borrower: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Lender (receives collateral, posts cash)
                <select value={proposeForm.lender} onChange={(e) => setProposeForm((f) => ({ ...f, lender: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Regulator
              <select value={proposeForm.regulator} onChange={(e) => setProposeForm((f) => ({ ...f, regulator: e.target.value }))}>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Collateral instrument id (must be owned by borrower)
              <input value={proposeForm.collateralInstrumentId} onChange={(e) => setProposeForm((f) => ({ ...f, collateralInstrumentId: e.target.value }))} required placeholder="e.g. BOND-1234" />
            </label>
            <div className="form-row">
              <label>
                Collateral quantity
                <input type="number" step="1" value={proposeForm.collateralQty} onChange={(e) => setProposeForm((f) => ({ ...f, collateralQty: e.target.value }))} required />
              </label>
              <label>
                Repo rate % (annual)
                <input type="number" step="0.01" value={proposeForm.repoRatePct} onChange={(e) => setProposeForm((f) => ({ ...f, repoRatePct: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Principal currency
                <input value={proposeForm.principalCurrency} onChange={(e) => setProposeForm((f) => ({ ...f, principalCurrency: e.target.value }))} required />
              </label>
              <label>
                Principal amount
                <input type="number" step="0.01" value={proposeForm.principalAmount} onChange={(e) => setProposeForm((f) => ({ ...f, principalAmount: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Start date
                <input type="date" value={proposeForm.startDate} onChange={(e) => setProposeForm((f) => ({ ...f, startDate: e.target.value }))} required />
              </label>
              <label>
                Maturity date
                <input type="date" value={proposeForm.maturityDate} onChange={(e) => setProposeForm((f) => ({ ...f, maturityDate: e.target.value }))} required />
              </label>
            </div>
            <button disabled={busy}>Propose</button>
          </form>
        </div>

        <div className="card">
          <h2>Accept repo (atomic DvP)</h2>
          <form onSubmit={handleAccept}>
            <label>
              Repo id
              <input value={acceptForm.repoId} onChange={(e) => setAcceptForm((f) => ({ ...f, repoId: e.target.value }))} required />
            </label>
            <label>
              Accepter (counterparty to the proposer)
              <select value={acceptForm.accepter} onChange={(e) => setAcceptForm((f) => ({ ...f, accepter: e.target.value }))}>
                <option value="">Select...</option>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-row">
              <label>
                Borrower wallet id
                <input value={acceptForm.borrowerWalletId} onChange={(e) => setAcceptForm((f) => ({ ...f, borrowerWalletId: e.target.value }))} required />
              </label>
              <label>
                Lender wallet id
                <input value={acceptForm.lenderWalletId} onChange={(e) => setAcceptForm((f) => ({ ...f, lenderWalletId: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Settled collateral instrument id
                <input value={acceptForm.settledCollateralInstrumentId} onChange={(e) => setAcceptForm((f) => ({ ...f, settledCollateralInstrumentId: e.target.value }))} required placeholder="new instrument id for lender's locked collateral" />
              </label>
              <label>
                Settled cash deposit id
                <input value={acceptForm.settledCashDepositId} onChange={(e) => setAcceptForm((f) => ({ ...f, settledCashDepositId: e.target.value }))} required placeholder="new deposit id for borrower's principal" />
              </label>
            </div>
            <button disabled={busy}>Accept &amp; settle</button>
          </form>
        </div>
      </div>

      <div className="card section-gap">
        <h2>Lifecycle: quote / mature / repurchase / default</h2>
        <form onSubmit={(e) => e.preventDefault()}>
          <div className="form-row">
            <label>
              Repo id
              <input value={lifecycleForm.repoId} onChange={(e) => setLifecycleForm((f) => ({ ...f, repoId: e.target.value }))} required />
            </label>
            <label>
              As-of date
              <input type="date" value={lifecycleForm.asOfDate} onChange={(e) => setLifecycleForm((f) => ({ ...f, asOfDate: e.target.value }))} required />
            </label>
          </div>
          <div className="form-row">
            <label>
              Repayment deposit id (repurchase)
              <input value={lifecycleForm.repaymentDepositId} onChange={(e) => setLifecycleForm((f) => ({ ...f, repaymentDepositId: e.target.value }))} />
            </label>
            <label>
              Returned instrument id (repurchase)
              <input value={lifecycleForm.returnedInstrumentId} onChange={(e) => setLifecycleForm((f) => ({ ...f, returnedInstrumentId: e.target.value }))} />
            </label>
            <label>
              Released instrument id (default)
              <input value={lifecycleForm.releasedInstrumentId} onChange={(e) => setLifecycleForm((f) => ({ ...f, releasedInstrumentId: e.target.value }))} />
            </label>
          </div>
          <div className="form-row">
            <label>
              Borrower wallet id (repurchase)
              <input value={lifecycleForm.borrowerWalletId} onChange={(e) => setLifecycleForm((f) => ({ ...f, borrowerWalletId: e.target.value }))} />
            </label>
            <label>
              Lender wallet id (repurchase / default)
              <input value={lifecycleForm.lenderWalletId} onChange={(e) => setLifecycleForm((f) => ({ ...f, lenderWalletId: e.target.value }))} />
            </label>
          </div>
          <p className="muted" style={{ fontSize: 12 }}>
            Repurchase is submitted as the current party (<strong>{party}</strong>, must be the repo's borrower).
            Default is submitted as the current party (<strong>{party}</strong>, must be the repo's lender).
          </p>
          <div className="toolbar">
            <button className="secondary" disabled={busy} onClick={handleQuote}>
              Quote
            </button>
            <button className="secondary" disabled={busy} onClick={handleMature}>
              Mark matured
            </button>
            <button disabled={busy} onClick={handleRepurchase}>
              Repurchase
            </button>
            <button className="danger" disabled={busy} onClick={handleDefault}>
              Declare default
            </button>
          </div>
          {quoteResult && (
            <p className="muted">
              Repurchase amount for {quoteResult.repoId} as of {quoteResult.asOfDate}:{' '}
              <span className="mono">{quoteResult.repurchaseAmount}</span>
            </p>
          )}
        </form>
      </div>

      <div className="grid grid-2 section-gap">
        <div className="card">
          <h2>Open proposals visible to {party}</h2>
          {proposalsLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={proposalsError} />
          {!proposalsLoading && (!proposals || proposals.length === 0) && <p className="empty">No open repo proposals.</p>}
          {proposals && proposals.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Repo</th>
                  <th>Direction</th>
                  <th>Borrower</th>
                  <th>Lender</th>
                  <th>Collateral</th>
                  <th>Principal</th>
                  <th>Rate %</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {proposals.map((r) => (
                  <tr key={r.repoId}>
                    <td className="mono">{r.repoId}</td>
                    <td>{r.direction?.replaceAll('_', ' ')}</td>
                    <td>{r.borrower}</td>
                    <td>{r.lender}</td>
                    <td className="mono">{r.collateralInstrumentId} x{formatQty(r.collateralQty)}</td>
                    <td>{formatMoney(r.principal)}</td>
                    <td>{formatQty(r.repoRatePct)}</td>
                    <td><StatusBadge status={r.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2>Open agreements visible to {party}</h2>
          {agreementsLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={agreementsError} />
          {!agreementsLoading && (!agreements || agreements.length === 0) && <p className="empty">No open repo agreements.</p>}
          {agreements && agreements.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Repo</th>
                  <th>Borrower</th>
                  <th>Lender</th>
                  <th>Collateral</th>
                  <th>Principal</th>
                  <th>Maturity</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {agreements.map((r) => (
                  <tr key={r.repoId}>
                    <td className="mono">{r.repoId}</td>
                    <td>{r.borrower}</td>
                    <td>{r.lender}</td>
                    <td className="mono">{r.collateralInstrumentId} x{formatQty(r.collateralQty)}</td>
                    <td>{formatMoney(r.principal)}</td>
                    <td className="mono">{r.maturityDate}</td>
                    <td><StatusBadge status={r.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <div className="card section-gap">
        <h2>Closed repo records visible to {party}</h2>
        {closedLoading && <p className="empty">Loading...</p>}
        <ErrorBanner error={closedError} />
        {!closedLoading && (!closed || closed.length === 0) && <p className="empty">No closed repo records.</p>}
        {closed && closed.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Repo</th>
                <th>Borrower</th>
                <th>Lender</th>
                <th>Principal</th>
                <th>Interest paid</th>
                <th>Closed at</th>
                <th>Outcome</th>
              </tr>
            </thead>
            <tbody>
              {closed.map((r) => (
                <tr key={r.repoId}>
                  <td className="mono">{r.repoId}</td>
                  <td>{r.borrower}</td>
                  <td>{r.lender}</td>
                  <td>{formatMoney(r.principal)}</td>
                  <td>{formatQty(r.interestPaid)}</td>
                  <td className="mono">{r.closedAt}</td>
                  <td><StatusBadge status={r.outcome} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
