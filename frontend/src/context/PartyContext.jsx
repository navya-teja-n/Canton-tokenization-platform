import React, { createContext, useContext, useEffect, useState } from 'react'

// The platform models a network of parties (banks, custodians, regulators,
// investors). The UI does not implement authentication -- instead the user
// picks "which party am I acting as" and the dashboard scopes all queries
// to that party's signatory/observer view, mirroring the privacy model
// enforced server-side by DAML signatories/observers.

const PartyContext = createContext(null)

export const KNOWN_PARTIES = [
  'BankA',
  'BankB',
  'CustodianX',
  'RegulatorFed',
  'KycProviderInc',
  'InvestorAlice',
  'InvestorBob'
]

const STORAGE_KEY = 'canton-platform.activeParty'

export function PartyProvider({ children }) {
  const [party, setParty] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) || KNOWN_PARTIES[0]
    } catch {
      return KNOWN_PARTIES[0]
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, party)
    } catch {
      // ignore storage errors (e.g. private browsing)
    }
  }, [party])

  return <PartyContext.Provider value={{ party, setParty }}>{children}</PartyContext.Provider>
}

export function useParty() {
  const ctx = useContext(PartyContext)
  if (!ctx) throw new Error('useParty must be used within a PartyProvider')
  return ctx
}
