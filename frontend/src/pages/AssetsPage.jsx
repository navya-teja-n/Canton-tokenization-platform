import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty, KNOWN_PARTIES } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import StatusBadge from '../components/StatusBadge.jsx'
import { ErrorBanner, SuccessBanner } from '../components/Banner.jsx'
import { formatQty } from '../components/Money.jsx'

const ASSET_CLASSES = ['GOVERNMENT_BOND', 'TREASURY_BILL']

function todayPlusYears(years) {
  const d = new Date()
  d.setFullYear(d.getFullYear() + years)
  return d.toISOString().slice(0, 10)
}

function emptyAssetForm(party) {
  return {
    issuer: 'BankA',
    owner: party,
    custodian: 'CustodianX',
    regulator: 'RegulatorFed',
    walletId: `WAL-${party}-01`,
    assetClass: 'GOVERNMENT_BOND',
    instrumentId: `BOND-${Math.floor(Math.random() * 10000)}`,
    isin: 'US0000000000',
    currency: 'USD',
    faceValuePerUnit: '1000',
    quantity: '100',
    couponRatePct: '4.50',
    purchasePricePerUnit: '',
    issueDate: new Date().toISOString().slice(0, 10),
    maturityDate: todayPlusYears(5)
  }
}

function emptyDepositForm(party) {
  return {
    bank: 'BankA',
    owner: party,
    regulator: 'RegulatorFed',
    walletId: `WAL-${party}-01`,
    depositId: `DEP-${Math.floor(Math.random() * 10000)}`,
    currency: 'USD',
    amount: '10000'
  }
}

export default function AssetsPage() {
  const { party } = useParty()
  const [assetForm, setAssetForm] = useState(() => emptyAssetForm(party))
  const [depositForm, setDepositForm] = useState(() => emptyDepositForm(party))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const { data: assets, error: assetsError, loading: assetsLoading, reload: reloadAssets } = useFetch(
    () => api.assets.forOwner(party),
    [party]
  )
  const { data: deposits, error: depositsError, loading: depositsLoading, reload: reloadDeposits } = useFetch(
    () => api.assets.depositsForOwner(party),
    [party]
  )

  async function handleIssueAsset(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const payload = {
        ...assetForm,
        faceValuePerUnit: Number(assetForm.faceValuePerUnit),
        quantity: Number(assetForm.quantity),
        couponRatePct: assetForm.couponRatePct === '' ? null : Number(assetForm.couponRatePct),
        purchasePricePerUnit: assetForm.purchasePricePerUnit === '' ? null : Number(assetForm.purchasePricePerUnit)
      }
      const asset = await api.assets.issue(payload)
      setSuccess(`Issued ${asset.instrumentId} (${asset.assetClass}) to ${asset.owner}`)
      reloadAssets()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleIssueDeposit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const payload = { ...depositForm, amount: Number(depositForm.amount) }
      const deposit = await api.assets.issueDeposit(payload)
      setSuccess(`Issued deposit ${deposit.depositId} (${deposit.currency} ${deposit.amount}) to ${deposit.owner}`)
      reloadDeposits()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Tokenized Assets &amp; Deposits</h1>
        <p>
          Mirrors DAML templates <span className="mono">Assets.Bond.GovernmentBond</span>,{' '}
          <span className="mono">Assets.Bond.TreasuryBill</span> and{' '}
          <span className="mono">Assets.Deposit.TokenizedDeposit</span>. Issuance credits the instrument directly
          into the recipient's custodial wallet.
        </p>
      </div>

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="grid grid-2">
        <div className="card">
          <h2>Issue government bond / treasury bill</h2>
          <form onSubmit={handleIssueAsset}>
            <div className="form-row">
              <label>
                Issuer
                <select value={assetForm.issuer} onChange={(e) => setAssetForm((f) => ({ ...f, issuer: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Owner
                <select value={assetForm.owner} onChange={(e) => setAssetForm((f) => ({ ...f, owner: e.target.value }))}>
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
                Custodian
                <select value={assetForm.custodian} onChange={(e) => setAssetForm((f) => ({ ...f, custodian: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Regulator
                <select value={assetForm.regulator} onChange={(e) => setAssetForm((f) => ({ ...f, regulator: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Recipient wallet id
              <input value={assetForm.walletId} onChange={(e) => setAssetForm((f) => ({ ...f, walletId: e.target.value }))} required />
            </label>
            <div className="form-row">
              <label>
                Asset class
                <select value={assetForm.assetClass} onChange={(e) => setAssetForm((f) => ({ ...f, assetClass: e.target.value }))}>
                  {ASSET_CLASSES.map((c) => (
                    <option key={c} value={c}>
                      {c.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Instrument id
                <input value={assetForm.instrumentId} onChange={(e) => setAssetForm((f) => ({ ...f, instrumentId: e.target.value }))} required />
              </label>
              <label>
                ISIN
                <input value={assetForm.isin} onChange={(e) => setAssetForm((f) => ({ ...f, isin: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Currency
                <input value={assetForm.currency} onChange={(e) => setAssetForm((f) => ({ ...f, currency: e.target.value }))} required />
              </label>
              <label>
                Face value / unit
                <input type="number" step="0.01" value={assetForm.faceValuePerUnit} onChange={(e) => setAssetForm((f) => ({ ...f, faceValuePerUnit: e.target.value }))} required />
              </label>
              <label>
                Quantity
                <input type="number" step="1" value={assetForm.quantity} onChange={(e) => setAssetForm((f) => ({ ...f, quantity: e.target.value }))} required />
              </label>
            </div>
            <div className="form-row">
              <label>
                Coupon rate % (bonds)
                <input type="number" step="0.01" value={assetForm.couponRatePct} onChange={(e) => setAssetForm((f) => ({ ...f, couponRatePct: e.target.value }))} placeholder="GOVERNMENT_BOND only" />
              </label>
              <label>
                Purchase price / unit (bills)
                <input type="number" step="0.01" value={assetForm.purchasePricePerUnit} onChange={(e) => setAssetForm((f) => ({ ...f, purchasePricePerUnit: e.target.value }))} placeholder="TREASURY_BILL only" />
              </label>
            </div>
            <div className="form-row">
              <label>
                Issue date
                <input type="date" value={assetForm.issueDate} onChange={(e) => setAssetForm((f) => ({ ...f, issueDate: e.target.value }))} required />
              </label>
              <label>
                Maturity date
                <input type="date" value={assetForm.maturityDate} onChange={(e) => setAssetForm((f) => ({ ...f, maturityDate: e.target.value }))} required />
              </label>
            </div>
            <button disabled={busy}>Issue instrument</button>
          </form>
        </div>

        <div className="card">
          <h2>Issue tokenized deposit</h2>
          <form onSubmit={handleIssueDeposit}>
            <div className="form-row">
              <label>
                Issuing bank
                <select value={depositForm.bank} onChange={(e) => setDepositForm((f) => ({ ...f, bank: e.target.value }))}>
                  {KNOWN_PARTIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Owner
                <select value={depositForm.owner} onChange={(e) => setDepositForm((f) => ({ ...f, owner: e.target.value }))}>
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
              <select value={depositForm.regulator} onChange={(e) => setDepositForm((f) => ({ ...f, regulator: e.target.value }))}>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Recipient wallet id
              <input value={depositForm.walletId} onChange={(e) => setDepositForm((f) => ({ ...f, walletId: e.target.value }))} required />
            </label>
            <div className="form-row">
              <label>
                Deposit id
                <input value={depositForm.depositId} onChange={(e) => setDepositForm((f) => ({ ...f, depositId: e.target.value }))} required />
              </label>
              <label>
                Currency
                <input value={depositForm.currency} onChange={(e) => setDepositForm((f) => ({ ...f, currency: e.target.value }))} required />
              </label>
              <label>
                Amount
                <input type="number" step="0.01" value={depositForm.amount} onChange={(e) => setDepositForm((f) => ({ ...f, amount: e.target.value }))} required />
              </label>
            </div>
            <button disabled={busy}>Issue deposit</button>
          </form>
        </div>
      </div>

      <div className="grid grid-2 section-gap">
        <div className="card">
          <h2>Instruments held by {party}</h2>
          {assetsLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={assetsError} />
          {!assetsLoading && (!assets || assets.length === 0) && <p className="empty">No instruments held.</p>}
          {assets && assets.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Instrument</th>
                  <th>Class</th>
                  <th>Qty</th>
                  <th>Currency</th>
                  <th>Face value</th>
                  <th>Maturity</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {assets.map((a) => (
                  <tr key={a.instrumentId}>
                    <td className="mono">{a.instrumentId}</td>
                    <td>{a.assetClass?.replaceAll('_', ' ')}</td>
                    <td>{formatQty(a.quantity)}</td>
                    <td>{a.currency}</td>
                    <td>{formatQty(a.faceValuePerUnit)}</td>
                    <td className="mono">{a.maturityDate}</td>
                    <td><StatusBadge status={a.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2>Deposits held by {party}</h2>
          {depositsLoading && <p className="empty">Loading...</p>}
          <ErrorBanner error={depositsError} />
          {!depositsLoading && (!deposits || deposits.length === 0) && <p className="empty">No deposits held.</p>}
          {deposits && deposits.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Deposit</th>
                  <th>Bank</th>
                  <th>Amount</th>
                  <th>Currency</th>
                  <th>Frozen</th>
                </tr>
              </thead>
              <tbody>
                {deposits.map((d) => (
                  <tr key={d.depositId}>
                    <td className="mono">{d.depositId}</td>
                    <td>{d.bank}</td>
                    <td>{formatQty(d.amount)}</td>
                    <td>{d.currency}</td>
                    <td>{d.frozen ? 'Yes' : 'No'}</td>
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
