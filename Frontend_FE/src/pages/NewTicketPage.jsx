import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorText } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ErrorBanner, PRIORITIES, label } from '../components/ui.jsx'

export default function NewTicketPage() {
  const { isStaff } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ subject: '', description: '', priority: 'MEDIUM', requesterId: '' })
  const [customers, setCustomers] = useState([])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // staff can raise a ticket on behalf of a customer
  useEffect(() => {
    if (!isStaff) return
    api('/users', { query: { role: 'CUSTOMER', size: 100, sort: 'fullName,asc' } })
      .then((page) => setCustomers(page.content.filter((c) => c.active)))
      .catch(() => setCustomers([]))
  }, [isStaff])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const created = await api('/tickets', {
        method: 'POST',
        body: {
          subject: form.subject,
          description: form.description,
          priority: form.priority,
          requesterId: form.requesterId ? Number(form.requesterId) : null
        }
      })
      navigate(`/tickets/${created.id}`)
    } catch (err) {
      setError(errorText(err))
      setBusy(false)
    }
  }

  return (
    <div className="narrow">
      <h1>New ticket</h1>
      <ErrorBanner error={error} />
      <form onSubmit={submit} className="form card">
        <label>
          Subject
          <input value={form.subject} onChange={set('subject')} required maxLength={200} autoFocus />
        </label>
        <label>
          Description
          <textarea rows={7} value={form.description} onChange={set('description')} required maxLength={10000} />
        </label>
        <div className="form-row">
          <label>
            Priority
            <select value={form.priority} onChange={set('priority')}>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {label(p)}
                </option>
              ))}
            </select>
          </label>
          {isStaff && (
            <label>
              Requester
              <select value={form.requesterId} onChange={set('requesterId')}>
                <option value="">Me</option>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.fullName} ({c.email})
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create ticket'}
          </button>
        </div>
      </form>
    </div>
  )
}
