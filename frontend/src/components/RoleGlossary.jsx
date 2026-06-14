import React from 'react'

// Short, professional explanations of each network participant. Used to
// orient first-time viewers (e.g. on the KYC and Wallets pages) without
// resorting to a "demo app" style banner.
const ROLES = [
  {
    name: 'Banks — BankA, BankB',
    desc: 'Regulated credit institutions. They issue tokenized bonds, treasury bills, and deposits, and provide cash accounts to their clients.'
  },
  {
    name: 'Custodian — CustodianX',
    desc: 'Holds custodial wallets on behalf of banks and investors, providing safekeeping for tokenized assets and cash balances.'
  },
  {
    name: 'Regulator — RegulatorFed',
    desc: 'Holds observer rights on KYC records, wallets, instruments, trades, and repos for oversight and audit, without participating in transactions.'
  },
  {
    name: 'KYC Provider — KycProviderInc',
    desc: 'Performs identity verification and AML checks. Only parties with an active KYC approval can open wallets, hold instruments, or transact.'
  },
  {
    name: 'Investors — InvestorAlice, InvestorBob',
    desc: 'Hold custodial wallets and transact in tokenized instruments and deposits with banks and other investors.'
  }
]

export default function RoleGlossary() {
  return (
    <details className="role-glossary">
      <summary>About the participants in this network</summary>
      <dl>
        {ROLES.map((role) => (
          <React.Fragment key={role.name}>
            <dt>{role.name}</dt>
            <dd>{role.desc}</dd>
          </React.Fragment>
        ))}
      </dl>
    </details>
  )
}
