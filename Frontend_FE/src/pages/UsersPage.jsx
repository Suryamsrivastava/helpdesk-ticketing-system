import React, { useCallback, useEffect, useState } from 'react'
import { api, errorText } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ErrorBanner, Pagination, ROLES, RoleBadge, formatDate, label } from '../components/ui.jsx'

const EMPTY = { fullName: '', email: '', password: '', role: 'AGENT' }

export default function UsersPage() {
  const { user: me } = useAuth()
  const [result, setResult] = useState(null)
  const [roleFilter, setRoleFilter] = useState('')
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    return api('/users', { query: { role: roleFilter, page, size: 10, sort: 'fullName,asc' } })
      .then(setResult)
      .catch((err) => setError(errorText(err)))
  }, [roleFilter, page])

  useEffect(() => {
    load()
  }, [load])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const create = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const created = await api('/users', { method: 'POST', body: form })
      setNotice(`${created.fullName} was added as ${label(created.role)}.`)
      setForm(EMPTY)
      await load()
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  const patch = async (id, body) => {
    setError(null)
    setNotice(null)
    try {
      await api(`/users/${id}`, { method: 'PATCH', body })
      await load()
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Users</h1>
          <p className="muted">People in {me.organizationName}. Only they can see this organization’s tickets.</p>
        </div>
      </div>

      <ErrorBanner error={error} />
      {notice && <div className="notice">{notice}</div>}

      <div className="users-grid">
        <div>
          <div className="filters card">
            <select
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value)
                setPage(0)
              }}
              aria-label="Role"
            >
              <option value="">All roles</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {label(r)}
                </option>
              ))}
            </select>
          </div>
          <div className="card table-card">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Added</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {result &&
                  result.content.map((u) => {
                    const self = u.id === me.id
                    return (
                      <tr key={u.id} className={u.active ? '' : 'inactive'}>
                        <td>
                          {u.fullName} {self && <span className="muted small">(you)</span>}
                          <div className="muted small">{u.email}</div>
                        </td>
                        <td>
                          {self ? (
                            <RoleBadge role={u.role} />
                          ) : (
                            <select value={u.role} onChange={(e) => patch(u.id, { role: e.target.value })}>
                              {ROLES.map((r) => (
                                <option key={r} value={r}>
                                  {label(r)}
                                </option>
                              ))}
                            </select>
                          )}
                        </td>
                        <td>{u.active ? 'Active' : <span className="muted">Deactivated</span>}</td>
                        <td className="muted nowrap">{formatDate(u.createdAt)}</td>
                        <td>
                          {!self && (
                            <button className="btn btn-ghost" onClick={() => patch(u.id, { active: !u.active })}>
                              {u.active ? 'Deactivate' : 'Activate'}
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
          {result && (
            <Pagination
              page={result.page}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              onChange={setPage}
            />
          )}
        </div>

        <form onSubmit={create} className="form card">
          <h2>Add a user</h2>
          <label>
            Full name
            <input value={form.fullName} onChange={set('fullName')} required maxLength={120} />
          </label>
          <label>
            Email
            <input type="email" value={form.email} onChange={set('email')} required />
          </label>
          <label>
            Temporary password
            <input type="password" value={form.password} onChange={set('password')} required minLength={8} />
          </label>
          <label>
            Role
            <select value={form.role} onChange={set('role')}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {label(r)}
                </option>
              ))}
            </select>
          </label>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Adding…' : 'Add user'}
          </button>
        </form>
      </div>
    </div>
  )
}
