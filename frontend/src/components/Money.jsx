import React from 'react'

export function formatMoney(money) {
  if (!money) return '—'
  const { currency, amount } = money
  const n = Number(amount)
  if (Number.isNaN(n)) return `${amount} ${currency}`
  return `${n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`
}

export function formatQty(value) {
  if (value === null || value === undefined) return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return n.toLocaleString(undefined, { maximumFractionDigits: 6 })
}

export default function Money({ value }) {
  return <span className="mono">{formatMoney(value)}</span>
}
