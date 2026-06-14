import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty, KNOWN_PARTIES } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import { ErrorBanner, SuccessBanner } from '../components/Banner.jsx'
import { formatMoney, formatQty } from '../components/Money.jsx'
import RoleGlossary from '../components/RoleGlossary.jsx'

function emptyForm(party) {
  return {
    owner: party,
    custodian: 'CustodianX',
    regulator: 'RegulatorFed',
    kycProvider: 'KycProviderInc',
    walletId: `WAL-${party}-01`
  }
}

export default function WalletsPage() {
  const { party } = useParty()
  const [form, setForm] = useState(() => emptyForm(party))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const { data: wallets, error: listError, loading, reload } = useFetch(() => api.wallets.forParty(party), [party])

  async function handleOpen(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const wallet = await api.wallets.open(form)
      setSuccess(`Wallet ${wallet.walletId} opened for ${wallet.owner}`)
      reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Custodial Wallets</h1>
        <p>
          Mirrors DAML <span className="mono">Wallet.Wallet</span>: a KYC-gated custodial wallet jointly held by{' '}
          <span className="mono">owner</span> and <span className="mono">custodian</span>, observed by{' '}
          <span className="mono">regulator</span>. Opening a wallet requires the owner to hold an active KYC approval.
        </p>
      </div>

      <RoleGlossary />

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="grid grid-2">
        <div className="card">
          <h2>Open a wallet</h2>
          <form onSubmit={handleOpen}>
            <div className="form-row">
              <label>
                Owner
                <select value={form.owner} onChange={(e) => setForm((f) => ({ ...f, owner: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Custodian
                <select value={form.custodian} onChange={(e) => setForm((f) => ({ ...f, custodian: e.target.value }))}>
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
                Regulator
                <select value={form.regulator} onChange={(e) => setForm((f) => ({ ...f, regulator: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                KYC provider
                <select value={form.kycProvider} onChange={(e) => setForm((f) => ({ ...f, kycProvider: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Wallet id
              <input value={form.walletId} onChange={(e) => setForm((f) => ({ ...f, walletId: e.target.value }))} required />
            </label>
            <button disabled={busy}>Open wallet</button>
          </form>
        </div>

        <div className="card">
          <h2>Wallets visible to {party}</h2>
          {loading && <p className="empty">Loading...</p>}
          <ErrorBanner error={listError} />
          {!loading && (!wallets || wallets.length === 0) && <p className="empty">No wallets yet.</p>}
          {wallets && wallets.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Wallet</th>
                  <th>Owner</th>
                  <th>Custodian</th>
                  <th>Holdings</th>
                  <th>Cash</th>
                </tr>
              </thead>
              <tbody>
                {wallets.map((w) => (
                  <tr key={w.walletId}>
                    <td className="mono">{w.walletId}</td>
                    <td>{w.owner}</td>
                    <td>{w.custodian}</td>
                    <td>{w.holdings?.length || 0}</td>
                    <td>{w.cashBalances?.length || 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {wallets && wallets.length > 0 && (
        <div className="grid grid-2 section-gap">
          {wallets.map((w) => (
            <div className="card" key={w.walletId}>
              <h2 className="mono">{w.walletId}</h2>
              <h3>Holdings</h3>
              {(!w.holdings || w.holdings.length === 0) && <p className="empty">No instrument holdings.</p>}
              {w.holdings && w.holdings.length > 0 && (
                <table>
                  <thead>
                    <tr>
                      <th>Instrument</th>
                      <th>Class</th>
                      <th>Quantity</th>
                      <th>Locked</th>
                    </tr>
                  </thead>
                  <tbody>
                    {w.holdings.map((h, i) => (
                      <tr key={i}>
                        <td className="mono">{h.instrumentId}</td>
                        <td>{h.assetClass?.replaceAll('_', ' ')}</td>
                        <td>{formatQty(h.quantity)}</td>
                        <td>{h.locked ? 'Yes' : 'No'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <h3 className="section-gap">Cash balances</h3>
              {(!w.cashBalances || w.cashBalances.length === 0) && <p className="empty">No cash balances.</p>}
              {w.cashBalances && w.cashBalances.length > 0 && (
                <table>
                  <tbody>
                    {w.cashBalances.map((m, i) => (
                      <tr key={i}>
                        <th>{m.currency}</th>
                        <td>{formatMoney(m)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
