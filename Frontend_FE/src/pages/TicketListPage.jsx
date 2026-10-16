import React, { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, errorText } from '../api.js'
import { useAuth } from '../auth.jsx'
import {
  ErrorBanner,
  Pagination,
  PRIORITIES,
  PriorityBadge,
  STATUSES,
  StatusBadge,
  formatDate,
  label
} from '../components/ui.jsx'

const PAGE_SIZE = 10

// Filters live in the URL so a filtered list survives reload and the back button
export default function TicketListPage() {
  const { isStaff, user } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [result, setResult] = useState(null)
  const [agents, setAgents] = useState([])
  const [error, setError] = useState(null)
  const [search, setSearch] = useState(params.get('q') || '')

  const status = params.get('status') || ''
  const priority = params.get('priority') || ''
  const assignee = params.get('assignee') || ''
  const page = Number(params.get('page') || 0)
  const q = params.get('q') || ''
  const unlinked = params.get('unlinked') === '1'

  const update = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      if (value === '' || value === null || value === undefined) next.delete(key)
      else next.set(key, value)
    })
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  useEffect(() => {
    if (isStaff) api('/users/agents').then(setAgents).catch(() => setAgents([]))
  }, [isStaff])

  useEffect(() => {
    let cancelled = false
    setError(null)
    api('/tickets', {
      query: {
        status,
        priority,
        q,
        assigneeId: assignee === 'me' ? user.id : assignee !== 'none' ? assignee : '',
        unassigned: assignee === 'none' ? 'true' : '',
        unlinked: unlinked ? 'true' : '',
        page,
        size: PAGE_SIZE,
        sort: 'createdAt,desc'
      }
    })
      .then((data) => !cancelled && setResult(data))
      .catch((err) => !cancelled && setError(errorText(err)))
    return () => {
      cancelled = true
    }
  }, [status, priority, assignee, unlinked, page, q, user.id])

  const submitSearch = (e) => {
    e.preventDefault()
    update({ q: search.trim() })
  }

  const hasFilters = status || priority || assignee || q || unlinked

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{isStaff ? 'Tickets' : 'My tickets'}</h1>
          <p className="muted">
            {isStaff ? `All tickets of ${user.organizationName}` : 'Tickets you have raised'}
          </p>
        </div>
        <Link to="/tickets/new" className="btn btn-primary">
          + New ticket
        </Link>
      </div>

      <div className="filters card">
        <form onSubmit={submitSearch} className="filter-search">
          <input placeholder="Search subject or ticket #" value={search} onChange={(e) => setSearch(e.target.value)} />
          <button className="btn btn-ghost">Search</button>
        </form>
        <select value={status} onChange={(e) => update({ status: e.target.value })} aria-label="Status">
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {label(s)}
            </option>
          ))}
        </select>
        <select value={priority} onChange={(e) => update({ priority: e.target.value })} aria-label="Priority">
          <option value="">All priorities</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {label(p)}
            </option>
          ))}
        </select>
        {isStaff && (
          <select value={assignee} onChange={(e) => update({ assignee: e.target.value })} aria-label="Assigned agent">
            <option value="">Any agent</option>
            {user.role === 'AGENT' && <option value="me">Assigned to me</option>}
            <option value="none">Unassigned</option>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.fullName}
              </option>
            ))}
          </select>
        )}
        {isStaff && (
          <label className="toggle">
            <input
              type="checkbox"
              checked={unlinked}
              onChange={(e) => update({ unlinked: e.target.checked ? '1' : '' })}
            />
            Needs linking
          </label>
        )}
        {hasFilters && (
          <button
            className="btn btn-ghost"
            onClick={() => {
              setSearch('')
              setParams(new URLSearchParams())
            }}
          >
            Clear
          </button>
        )}
      </div>

      <ErrorBanner error={error} />

      <div className="card table-card">
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Subject</th>
              <th>Status</th>
              <th>Priority</th>
              {isStaff && <th>Requester</th>}
              <th>Assigned agent</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {result && result.content.length === 0 && (
              <tr>
                <td colSpan={isStaff ? 7 : 6} className="empty">
                  No tickets match these filters.
                </td>
              </tr>
            )}
            {result &&
              result.content.map((t) => (
                <tr key={t.id} className="row-link" onClick={() => navigate(`/tickets/${t.id}`)}>
                  <td className="muted">#{t.id}</td>
                  <td className="subject-cell">
                    {t.channel === 'EMAIL' && (
                      <span className="channel" title="Received by email">
                        ✉
                      </span>
                    )}
                    <Link to={`/tickets/${t.id}`} onClick={(e) => e.stopPropagation()}>
                      {t.subject}
                    </Link>
                  </td>
                  <td>
                    <StatusBadge status={t.status} />
                  </td>
                  <td>
                    <PriorityBadge priority={t.priority} />
                  </td>
                  {isStaff && (
                    <td>
                      {t.requester ? (
                        t.requester.fullName
                      ) : (
                        <>
                          <span className="badge unlinked-badge">Needs linking</span>
                          <div className="muted small">{t.senderEmail}</div>
                        </>
                      )}
                    </td>
                  )}
                  <td>{t.assignee ? t.assignee.fullName : <span className="muted">Unassigned</span>}</td>
                  <td className="muted nowrap">{formatDate(t.createdAt)}</td>
                </tr>
              ))}
          </tbody>
        </table>
        {!result && !error && <div className="empty">Loading…</div>}
      </div>

      {result && (
        <Pagination
          page={result.page}
          totalPages={result.totalPages}
          totalElements={result.totalElements}
          onChange={(p) => update({ page: p })}
        />
      )}
    </div>
  )
}
