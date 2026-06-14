import React, { useState } from 'react'
import api from '../api/client.js'
import { useParty, KNOWN_PARTIES } from '../context/PartyContext.jsx'
import { useFetch } from '../hooks/useFetch.js'
import StatusBadge from '../components/StatusBadge.jsx'
import { ErrorBanner, SuccessBanner } from '../components/Banner.jsx'
import RoleGlossary from '../components/RoleGlossary.jsx'

const RISK_RATINGS = ['LOW', 'MEDIUM', 'HIGH']

function emptyApproveForm(applicant) {
  return {
    applicant,
    kycProvider: 'KycProviderInc',
    regulator: 'RegulatorFed',
    legalName: '',
    jurisdiction: 'US',
    riskRating: 'LOW'
  }
}

export default function KycPage() {
  const { party } = useParty()
  const [lookupTarget, setLookupTarget] = useState(party)
  const [form, setForm] = useState(() => emptyApproveForm(party))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const { data: approval, error: lookupError, loading, reload } = useFetch(
    () => api.kyc.get(lookupTarget).catch((e) => (e.status === 404 ? null : Promise.reject(e))),
    [lookupTarget]
  )

  async function handleApprove(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      await api.kyc.approve(form)
      setSuccess(`KYC approved for ${form.applicant}`)
      setLookupTarget(form.applicant)
      reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleReject(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      await api.kyc.reject({ ...form, reason: reason || 'Failed compliance screening' })
      setSuccess(`KYC rejected for ${form.applicant}`)
      setLookupTarget(form.applicant)
      reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleRevoke(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      await api.kyc.revoke({ applicant: form.applicant, reason: reason || 'Revoked by regulator' })
      setSuccess(`KYC revoked for ${form.applicant}`)
      setLookupTarget(form.applicant)
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
        <h1>KYC Verification</h1>
        <p>
          Mirrors DAML <span className="mono">KYC.KycService</span>: every wallet, asset issuance, trade and repo
          requires the involved parties to hold an active <span className="mono">KycApproval</span>.
        </p>
      </div>

      <RoleGlossary />

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="grid grid-2">
        <div className="card">
          <h2>KYC status lookup</h2>
          <div className="form-row">
            <label>
              Applicant
              <select value={lookupTarget} onChange={(e) => setLookupTarget(e.target.value)}>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {loading && <p className="empty">Loading...</p>}
          {lookupError && lookupError.status !== 404 && <ErrorBanner error={lookupError} />}
          {!loading && !approval && <p className="empty">No KYC record for {lookupTarget} yet.</p>}
          {approval && (
            <table>
              <tbody>
                <tr>
                  <th>Status</th>
                  <td><StatusBadge status={approval.status} /></td>
                </tr>
                <tr>
                  <th>Legal name</th>
                  <td>{approval.legalName}</td>
                </tr>
                <tr>
                  <th>Jurisdiction</th>
                  <td>{approval.jurisdiction}</td>
                </tr>
                <tr>
                  <th>Risk rating</th>
                  <td>{approval.riskRating}</td>
                </tr>
                <tr>
                  <th>KYC provider</th>
                  <td>{approval.kycProvider}</td>
                </tr>
                <tr>
                  <th>Regulator</th>
                  <td>{approval.regulator}</td>
                </tr>
                <tr>
                  <th>Approved at</th>
                  <td className="mono">{approval.approvedAt}</td>
                </tr>
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2>Approve / reject / revoke KYC</h2>
          <form>
            <label>
              Applicant
              <select value={form.applicant} onChange={(e) => setForm((f) => ({ ...f, applicant: e.target.value }))}>
                {KNOWN_PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-row">
              <label>
                Legal name
                <input
                  value={form.legalName}
                  onChange={(e) => setForm((f) => ({ ...f, legalName: e.target.value }))}
                  placeholder="e.g. Alice Investments LLC"
                />
              </label>
              <label>
                Jurisdiction
                <input
                  value={form.jurisdiction}
                  onChange={(e) => setForm((f) => ({ ...f, jurisdiction: e.target.value }))}
                  placeholder="ISO country code"
                />
              </label>
            </div>
            <div className="form-row">
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
                Risk rating
                <select value={form.riskRating} onChange={(e) => setForm((f) => ({ ...f, riskRating: e.target.value }))}>
                  {RISK_RATINGS.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Rejection / revocation reason (optional)
              <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Used for reject/revoke" />
            </label>
            <div className="toolbar">
              <button onClick={handleApprove} disabled={busy}>
                Approve
              </button>
              <button onClick={handleReject} disabled={busy} className="secondary">
                Reject
              </button>
              <button onClick={handleRevoke} disabled={busy} className="danger">
                Revoke
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
