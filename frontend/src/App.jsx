import React from 'react'
import { HashRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { PartyProvider, useParty, KNOWN_PARTIES } from './context/PartyContext.jsx'
import KycPage from './pages/KycPage.jsx'
import WalletsPage from './pages/WalletsPage.jsx'
import AssetsPage from './pages/AssetsPage.jsx'
import TradingPage from './pages/TradingPage.jsx'
import RepoPage from './pages/RepoPage.jsx'
import SettlementMonitorPage from './pages/SettlementMonitorPage.jsx'

const NAV_ITEMS = [
  { to: '/kyc', label: 'KYC' },
  { to: '/wallets', label: 'Wallets' },
  { to: '/assets', label: 'Assets & Deposits' },
  { to: '/trading', label: 'Trading Desk' },
  { to: '/repo', label: 'Repo Desk' },
  { to: '/settlement', label: 'Settlement Monitor' }
]

function PartySwitcher() {
  const { party, setParty } = useParty()
  return (
    <div className="party-switcher">
      <label htmlFor="active-party">Acting as</label>
      <select id="active-party" value={party} onChange={(e) => setParty(e.target.value)}>
        {KNOWN_PARTIES.map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>
    </div>
  )
}

function Shell() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          Canton Tokenization &amp; Deposit Network
          <small>Institutional reference platform</small>
        </div>
        <nav className="nav-list">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <PartySwitcher />
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/kyc" replace />} />
          <Route path="/kyc" element={<KycPage />} />
          <Route path="/wallets" element={<WalletsPage />} />
          <Route path="/assets" element={<AssetsPage />} />
          <Route path="/trading" element={<TradingPage />} />
          <Route path="/repo" element={<RepoPage />} />
          <Route path="/settlement" element={<SettlementMonitorPage />} />
          <Route path="*" element={<Navigate to="/kyc" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <PartyProvider>
      <HashRouter>
        <Shell />
      </HashRouter>
    </PartyProvider>
  )
}
