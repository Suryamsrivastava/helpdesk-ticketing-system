import React, { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorText } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ErrorBanner, PRIORITIES, PriorityBadge, StatusBadge, formatDate, label } from '../components/ui.jsx'

// Mirrors TicketStatusPolicy on the backend; the backend still has the final say
const TRANSITIONS = {
  OPEN: ['IN_PROGRESS', 'RESOLVED', 'CLOSED'],
  IN_PROGRESS: ['OPEN', 'RESOLVED', 'CLOSED'],
  RESOLVED: ['IN_PROGRESS', 'OPEN', 'CLOSED'],
  CLOSED: ['OPEN']
}

const ACTION_LABEL = {
  OPEN: 'Reopen',
  IN_PROGRESS: 'Start progress',
  RESOLVED: 'Mark resolved',
  CLOSED: 'Close ticket'
}

function allowedMoves(status, isCustomer) {
  const moves = TRANSITIONS[status] || []
  if (!isCustomer) return moves
  return moves.filter((to) => to === 'CLOSED' || (to === 'OPEN' && (status === 'RESOLVED' || status === 'CLOSED')))
}

function historyText(h) {
  switch (h.type) {
    case 'CREATED':
      return 'created the ticket'
    case 'STATUS_CHANGED':
      return `changed status from ${label(h.fromValue)} to ${label(h.toValue)}`
    case 'ASSIGNEE_CHANGED':
      if (!h.toValue) return `unassigned ${h.fromValue}`
      return h.fromValue ? `reassigned from ${h.fromValue} to ${h.toValue}` : `assigned to ${h.toValue}`
    case 'PRIORITY_CHANGED':
      return `changed priority from ${label(h.fromValue)} to ${label(h.toValue)}`
    case 'DETAILS_UPDATED':
      return 'edited the subject or description'
    case 'REQUESTER_CHANGED':
      return h.fromValue ? `linked the ticket from ${h.fromValue} to ${h.toValue}` : `linked the ticket to ${h.toValue}`
    default:
      return h.type
  }
}

export default function TicketDetailPage() {
  const { id } = useParams()
  const { isStaff, isCustomer } = useAuth()
  const [ticket, setTicket] = useState(null)
  const [comments, setComments] = useState([])
  const [history, setHistory] = useState([])
  const [agents, setAgents] = useState([])
  const [error, setError] = useState(null)
  const [notFound, setNotFound] = useState(false)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState({})
  const [reply, setReply] = useState('')
  const [internal, setInternal] = useState(false)
  const [busy, setBusy] = useState(false)
  const [customers, setCustomers] = useState([])
  const [linkTo, setLinkTo] = useState('')
  const [relinking, setRelinking] = useState(false)

  const load = useCallback(() => {
    setError(null)
    return Promise.all([
      api(`/tickets/${id}`),
      api(`/tickets/${id}/comments`),
      api(`/tickets/${id}/history`)
    ])
      .then(([t, c, h]) => {
        setTicket(t)
        setComments(c)
        setHistory(h)
      })
      .catch((err) => (err.status === 404 ? setNotFound(true) : setError(errorText(err))))
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (!isStaff) return
    api('/users/agents').then(setAgents).catch(() => setAgents([]))
    api('/users', { query: { role: 'CUSTOMER', size: 100, sort: 'fullName,asc' } })
      .then((page) => setCustomers(page.content.filter((c) => c.active)))
      .catch(() => setCustomers([]))
  }, [isStaff])

  // every mutation reloads the ticket and its history so both stay in sync
  const run = async (request) => {
    setBusy(true)
    setError(null)
    try {
      await request()
      await load()
      return true
    } catch (err) {
      setError(errorText(err))
      return false
    } finally {
      setBusy(false)
    }
  }

  const changeStatus = (status) => run(() => api(`/tickets/${id}/status`, { method: 'PUT', body: { status } }))

  const assign = (value) =>
    run(() => api(`/tickets/${id}/assignee`, { method: 'PUT', body: { assigneeId: value ? Number(value) : null } }))

  const linkCustomer = async (e) => {
    e.preventDefault()
    const ok = await run(() =>
      api(`/tickets/${id}/requester`, { method: 'PUT', body: { requesterId: Number(linkTo) } })
    )
    if (ok) {
      setLinkTo('')
      setRelinking(false)
    }
  }

  const saveEdit = async (e) => {
    e.preventDefault()
    const body = { subject: draft.subject, description: draft.description }
    if (isStaff) body.priority = draft.priority
    if (await run(() => api(`/tickets/${id}`, { method: 'PATCH', body }))) setEditing(false)
  }

  const addComment = async (e) => {
    e.preventDefault()
    const ok = await run(() =>
      api(`/tickets/${id}/comments`, { method: 'POST', body: { body: reply, internal: isStaff && internal } })
    )
    if (ok) {
      setReply('')
      setInternal(false)
    }
  }

  if (notFound) {
    return (
      <div className="card empty">
        Ticket not found. It may not exist or belong to another organization. <Link to="/tickets">Back to tickets</Link>
      </div>
    )
  }
  if (!ticket) return error ? <ErrorBanner error={error} /> : <div className="empty">Loading…</div>

  const closed = ticket.status === 'CLOSED'
  const moves = allowedMoves(ticket.status, isCustomer)

  return (
    <div>
      <Link to="/tickets" className="back">
        ← Tickets
      </Link>
      <div className="page-head">
        <div>
          <h1>
            <span className="muted">#{ticket.id}</span> {ticket.subject}
          </h1>
          <div className="badges">
            <StatusBadge status={ticket.status} />
            <PriorityBadge priority={ticket.priority} />
          </div>
        </div>
        {!closed && !editing && (
          <button
            className="btn btn-ghost"
            onClick={() => {
              setDraft({ subject: ticket.subject, description: ticket.description, priority: ticket.priority })
              setEditing(true)
            }}
          >
            Edit
          </button>
        )}
      </div>

      <ErrorBanner error={error} />

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card">
            {editing ? (
              <form onSubmit={saveEdit} className="form">
                <label>
                  Subject
                  <input
                    value={draft.subject}
                    onChange={(e) => setDraft({ ...draft, subject: e.target.value })}
                    required
                    maxLength={200}
                  />
                </label>
                <label>
                  Description
                  <textarea
                    rows={6}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                    required
                    maxLength={10000}
                  />
                </label>
                {isStaff && (
                  <label>
                    Priority
                    <select value={draft.priority} onChange={(e) => setDraft({ ...draft, priority: e.target.value })}>
                      {PRIORITIES.map((p) => (
                        <option key={p} value={p}>
                          {label(p)}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setEditing(false)}>
                    Cancel
                  </button>
                  <button className="btn btn-primary" disabled={busy}>
                    Save
                  </button>
                </div>
              </form>
            ) : (
              <p className="description">{ticket.description}</p>
            )}
          </section>

          <section className="card">
            <h2>Conversation</h2>
            {comments.length === 0 && <p className="muted">No comments yet.</p>}
            <ul className="comments">
              {comments.map((c) => (
                <li key={c.id} className={c.internal ? 'comment internal' : 'comment'}>
                  <div className="comment-head">
                    <strong>{c.author ? c.author.fullName : c.externalAuthorName || c.externalAuthorEmail}</strong>
                    <span className="muted small">
                      {c.author ? label(c.author.role) : c.externalAuthorEmail}
                    </span>
                    {c.viaEmail && <span className="badge email-badge">via email</span>}
                    {c.internal && <span className="badge internal-badge">Internal note</span>}
                    <span className="muted small push">{formatDate(c.createdAt)}</span>
                  </div>
                  <p>{c.body}</p>
                </li>
              ))}
            </ul>
            <form onSubmit={addComment} className="form reply">
              <textarea
                rows={3}
                placeholder={internal ? 'Internal note (only agents and admins see this)' : 'Write a reply…'}
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                required
                maxLength={5000}
              />
              <div className="form-actions">
                {isStaff && (
                  <label className="checkbox">
                    <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
                    Internal note
                  </label>
                )}
                <button className="btn btn-primary" disabled={busy || !reply.trim()}>
                  {internal ? 'Add note' : 'Send reply'}
                </button>
              </div>
            </form>
          </section>
        </div>

        <aside className="detail-side">
          <section className="card">
            <h2>Actions</h2>
            <div className="actions">
              {moves.map((to) => (
                <button
                  key={to}
                  className={to === 'CLOSED' ? 'btn btn-danger' : 'btn btn-ghost'}
                  disabled={busy}
                  onClick={() => changeStatus(to)}
                >
                  {ACTION_LABEL[to]}
                </button>
              ))}
              {moves.length === 0 && <span className="muted small">No actions available.</span>}
            </div>
            {isStaff && (
              <label className="assign">
                Assigned agent
                <select
                  value={ticket.assignee ? ticket.assignee.id : ''}
                  disabled={busy || closed}
                  onChange={(e) => assign(e.target.value)}
                >
                  <option value="">Unassigned</option>
                  {agents.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.fullName}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </section>

          {isStaff && (!ticket.requester || relinking) && (
            <section className="card link-card">
              <h2>{ticket.requester ? 'Change customer' : 'Link to a customer'}</h2>
              {!ticket.requester && (
                <p className="muted small">
                  This mail came from {ticket.senderEmail}, which no customer is registered with. Pick the customer it
                  belongs to; they will then see the ticket in their portal.
                </p>
              )}
              <form onSubmit={linkCustomer} className="form">
                <select value={linkTo} onChange={(e) => setLinkTo(e.target.value)} required>
                  <option value="">Select a customer…</option>
                  {customers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.fullName} ({c.email})
                    </option>
                  ))}
                </select>
                <div className="form-actions">
                  {relinking && (
                    <button type="button" className="btn btn-ghost" onClick={() => setRelinking(false)}>
                      Cancel
                    </button>
                  )}
                  <button className="btn btn-primary" disabled={busy || !linkTo}>
                    Link customer
                  </button>
                </div>
              </form>
            </section>
          )}

          <section className="card">
            <h2>Details</h2>
            <dl className="details">
              <dt>Requester</dt>
              <dd>
                {ticket.requester ? (
                  <>
                    {ticket.requester.fullName}
                    <div className="muted small">{ticket.requester.email}</div>
                  </>
                ) : (
                  <span className="badge unlinked-badge">Needs linking</span>
                )}
              </dd>
              {ticket.channel === 'EMAIL' && (
                <>
                  <dt>Received by</dt>
                  <dd>
                    Email from {ticket.senderName || ticket.senderEmail}
                    <div className="muted small">{ticket.senderEmail}</div>
                  </dd>
                </>
              )}
              <dt>Assigned agent</dt>
              <dd>{ticket.assignee ? ticket.assignee.fullName : <span className="muted">Unassigned</span>}</dd>
              {isStaff && ticket.requester && ticket.channel === 'EMAIL' && !relinking && (
                <>
                  <dt />
                  <dd>
                    <button className="link-button" onClick={() => setRelinking(true)}>
                      Linked to the wrong customer?
                    </button>
                  </dd>
                </>
              )}
              <dt>Created</dt>
              <dd>{formatDate(ticket.createdAt)}</dd>
              <dt>Last updated</dt>
              <dd>{formatDate(ticket.updatedAt)}</dd>
              {ticket.resolvedAt && (
                <>
                  <dt>Resolved</dt>
                  <dd>{formatDate(ticket.resolvedAt)}</dd>
                </>
              )}
              {ticket.closedAt && (
                <>
                  <dt>Closed</dt>
                  <dd>{formatDate(ticket.closedAt)}</dd>
                </>
              )}
            </dl>
          </section>

          <section className="card">
            <h2>History</h2>
            <ol className="history">
              {history.map((h) => (
                <li key={h.id}>
                  <strong>{h.actor ? h.actor.fullName : 'Email intake'}</strong> {historyText(h)}
                  <div className="muted small">{formatDate(h.createdAt)}</div>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </div>
  )
}
