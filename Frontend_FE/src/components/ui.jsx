import React from 'react'

export const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']
export const ROLES = ['ORG_ADMIN', 'AGENT', 'CUSTOMER']

export function label(value) {
  if (!value) return ''
  if (value === 'ORG_ADMIN') return 'Org Admin'
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

export function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function StatusBadge({ status }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{label(status)}</span>
}

export function PriorityBadge({ priority }) {
  return <span className={`badge priority-${priority.toLowerCase()}`}>{label(priority)}</span>
}

export function RoleBadge({ role }) {
  return <span className={`badge role-${role.toLowerCase()}`}>{label(role)}</span>
}

export function ErrorBanner({ error }) {
  if (!error) return null
  return (
    <div className="alert" role="alert">
      {error}
    </div>
  )
}

export function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalPages <= 1) {
    return <div className="pagination muted">{totalElements} result{totalElements === 1 ? '' : 's'}</div>
  }
  return (
    <div className="pagination">
      <span className="muted">
        Page {page + 1} of {totalPages} · {totalElements} results
      </span>
      <div className="pagination-buttons">
        <button className="btn btn-ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>
          ← Previous
        </button>
        <button className="btn btn-ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Next →
        </button>
      </div>
    </div>
  )
}
