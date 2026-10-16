import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { errorText } from '../api.js'
import { ErrorBanner } from '../components/ui.jsx'

export default function RegisterPage() {
  const { registerOrganization } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ organizationName: '', adminName: '', email: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await registerOrganization(form)
      navigate('/users', { replace: true })
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Register an organization</h1>
        <p className="muted">You become its Org Admin and can then add agents and customers.</p>
        <ErrorBanner error={error} />
        <form onSubmit={submit} className="form">
          <label>
            Organization name
            <input value={form.organizationName} onChange={set('organizationName')} required maxLength={120} />
          </label>
          <label>
            Your name
            <input value={form.adminName} onChange={set('adminName')} required maxLength={120} />
          </label>
          <label>
            Email
            <input type="email" value={form.email} onChange={set('email')} required />
          </label>
          <label>
            Password (min 8 characters)
            <input type="password" value={form.password} onChange={set('password')} required minLength={8} />
          </label>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create organization'}
          </button>
        </form>
        <p className="muted small">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
