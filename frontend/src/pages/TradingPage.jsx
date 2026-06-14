import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty, KNOWN_PARTIES } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import StatusBadge from '../components/StatusBadge.jsx'
import { ErrorBanner, SuccessBanner } from '../components/Banner.jsx'
import { formatMoney, formatQty } from '../components/Money.jsx'

const ASSET_CLASSES = ['GOVERNMENT_BOND', 'TREASURY_BILL']

function emptyProposeForm(party) {
  return {
    seller: party,
    buyer: 'InvestorBob',
    regulator: 'RegulatorFed',
    tradeId: `TRD-${Math.floor(Math.random() * 10000)}`,
    assetClass: 'GOVERNMENT_BOND',
    instrumentId: '',
    quantity: '10',
    priceCurrency: 'USD',
    priceAmount: '10000'
  }
}

function emptySettleForm(party) {
  return {
    tradeId: '',
    buyer: party,
    sellerWalletId: '',
    buyerWalletId: '',
    settledInstrumentId: '',
    settledDepositId: ''
  }
}

export default function TradingPage() {
  const { party } = useParty()
  const [proposeForm, setProposeForm] = useState(() => emptyProposeForm(party))
  const [settleForm, setSettleForm] = useState(() => emptySettleForm(party))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const { data: proposals, error: proposalsError, loading: proposalsLoading, reload: reloadProposals } = useFetch(
    () => api.trades.proposalsForParty(party),
    [party]
  )
  const { data: executed, error: executedError, loading: executedLoading, reload: reloadExecuted } = useFetch(
    () => api.trades.executedForParty(party),
    [party]
  )

  function reloadAll() {
    reloadProposals()
    reloadExecuted()
  }

  async function handlePropose(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const payload = {
        seller: proposeForm.seller,
        buyer: proposeForm.buyer,
        regulator: proposeForm.regulator,
        tradeId: proposeForm.tradeId,
        assetClass: proposeForm.assetClass,
        instrumentId: proposeForm.instrumentId,
        quantity: Number(proposeForm.quantity),
        price: { currency: proposeForm.priceCurrency, amount: Number(proposeForm.priceAmount) }
      }
      const trade = await api.trades.propose(payload)
      setSuccess(`Trade ${trade.tradeId} proposed: ${trade.seller} -> ${trade.buyer}`)
      setSettleForm((f) => ({ ...f, tradeId: trade.tradeId }))
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleSettle(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const { tradeId, ...body } = settleForm
      const result = await api.trades.settle(tradeId, body)
      setSuccess(`Trade ${tradeId} settled at ${result.settledAt}`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleReject(tradeId, buyer) {
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      await api.trades.reject(tradeId, { buyer, reason: 'Rejected from trading desk UI' })
      setSuccess(`Trade ${tradeId} rejected`)
      reloadAll()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleCancel(tradeId, seller) {
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      await api.trades.cancel(tradeId, { seller })
      setSuccess(`Trade ${tradeId} cancelled`)
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
        <h1>Trading Desk (DvP)</h1>
        <p>
          Mirrors DAML <span className="mono">Trading.Trade</span>: a seller proposes a trade, the buyer accepts and
          the settlement leg (asset transfer + cash payment) executes atomically, producing an{' '}
          <span className="mono">ExecutedTrade</span>.
        </p>
      </div>

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="grid grid-2">
        <div className="card">
          <h2>Propose a trade (seller side)</h2>
          <form onSubmit={handlePropose}>
            <div className="form-row">
              <label>
                Seller
                <select value={proposeForm.seller} onChange={(e) => setProposeForm((f) => ({ ...f, seller: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Buyer
                <select value={proposeForm.buyer} onChange={(e) => setProposeForm((f) => ({ ...f, buyer: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
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
            </div>
            <div className="form-row">
              <label>
                Trade id
                <input value={proposeForm.tradeId} onChange={(e) => setProposeForm((f) => ({ ...f, tradeId: e.target.value }))} required />
              </label>
              <label>
                Asset class
                <select value={proposeForm.assetClass} onChange={(e) => setProposeForm((f) => ({ ...f, assetClass: e.target.value }))}>
                  {ASSET_CLASSES.map((c) => (
                    <option key={c} value={c}>
                      {c.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Instrument id (must be owned by seller, see Assets page)
              <input value={proposeForm.instrumentId} onChange={(e) => setProposeForm((f) => ({ ...f, instrumentId: e.target.value }))} required placeholder="e.g. BOND-1234" />
            </label>
            <div className="form-row">
              <label>
                Quantity
                <input type="number" step="1" value={proposeForm.quantity} onChange={(e) => setProposeForm((f) => ({ ...f, quantity: e.target.value }))} required />
              </label>
              <label>
                Price currency
                <input value={proposeForm.priceCurrency} onChange={(e) => setProposeForm((f) => ({ ...f, priceCurrency: e.target.value }))} required />
              </label>
              <label>
                Price amount (total)
                <input type="number" step="0.01" value={proposeForm.priceAmount} onChange={(e) => setProposeForm((f) => ({ ...f, priceAmount: e.target.value }))} required />
              </label>
            </div>
            <button disabled={busy}>Propose trade</button>
          </form>
        </div>

        <div className="card">
          <h2>Settle a trade (buyer side, atomic DvP)</h2>
          <form onSubmit={handleSettle}>
            <label>
              Trade id
              <input value={settleForm.tradeId} onChange={(e) => setSettleForm((f) => ({ ...f, tradeId: e.target.value }))} required />
            </label>
            <label>
              Buyer
              <select value={settleForm.buyer} onChange={(e) => setSettleForm((f) => ({ ...f, buyer: e.target.value }))}>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-row">
              <label>
                Seller wallet id
                <input value={settleForm.sellerWalletId} onChange={(e) => setSettleForm((f) => ({ ...f, sellerWalletId: e.target.value }))} required placeholder="seller's WAL-..." />
              </label>
              <label>
                Buyer wallet id
                <input value={settleForm.buyerWalletId} onChange={(e) => setSettleForm((f) => ({ ...f, buyerWalletId: e.target.value }))} required placeholder="buyer's WAL-..." />
              </label>
            </div>
            <div className="form-row">
              <label>
                Settled instrument id
                <input value={settleForm.settledInstrumentId} onChange={(e) => setSettleForm((f) => ({ ...f, settledInstrumentId: e.target.value }))} required placeholder="new instrument id for buyer's lot" />
              </label>
              <label>
                Settled deposit id
                <input value={settleForm.settledDepositId} onChange={(e) => setSettleForm((f) => ({ ...f, settledDepositId: e.target.value }))} required placeholder="new deposit id for seller's payment" />
              </label>
            </div>
            <button disabled={busy}>Settle (DvP)</button>
          </form>
        </div>
      </div>

      <div className="grid grid-2 section-gap">
        <div className="card">
          <h2>Open proposals visible to {party}</h2>
          {proposalsLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={proposalsError} />
          {!proposalsLoading && (!proposals || proposals.length === 0) && <p className="empty">No open trade proposals.</p>}
          {proposals && proposals.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Trade</th>
                  <th>Seller</th>
                  <th>Buyer</th>
                  <th>Instrument</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {proposals.map((t) => (
                  <tr key={t.tradeId}>
                    <td className="mono">{t.tradeId}</td>
                    <td>{t.seller}</td>
                    <td>{t.buyer}</td>
                    <td className="mono">{t.instrumentId}</td>
                    <td>{formatQty(t.quantity)}</td>
                    <td>{formatMoney(t.price)}</td>
                    <td><StatusBadge status={t.status} /></td>
                    <td>
                      {t.status === 'PROPOSED' && (
                        <div className="toolbar">
                          <button className="secondary" disabled={busy} onClick={() => handleReject(t.tradeId, t.buyer)}>
                            Reject
                          </button>
                          <button className="danger" disabled={busy} onClick={() => handleCancel(t.tradeId, t.seller)}>
                            Cancel
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2>Settled trades visible to {party}</h2>
          {executedLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={executedError} />
          {!executedLoading && (!executed || executed.length === 0) && <p className="empty">No settled trades.</p>}
          {executed && executed.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Trade</th>
                  <th>Seller</th>
                  <th>Buyer</th>
                  <th>Instrument</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Settled at</th>
                </tr>
              </thead>
              <tbody>
                {executed.map((t) => (
                  <tr key={t.tradeId}>
                    <td className="mono">{t.tradeId}</td>
                    <td>{t.seller}</td>
                    <td>{t.buyer}</td>
                    <td className="mono">{t.instrumentId}</td>
                    <td>{formatQty(t.quantity)}</td>
                    <td>{formatMoney(t.price)}</td>
                    <td className="mono">{t.settledAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
