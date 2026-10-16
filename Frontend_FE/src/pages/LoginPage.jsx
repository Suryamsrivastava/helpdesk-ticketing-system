import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { errorText } from '../api.js'
import { ErrorBanner } from '../components/ui.jsx'

// Seeded demo accounts (see README); clicking one fills the form
const DEMO_ACCOUNTS = [
  { email: 'admin@acme.test', who: 'Acme · Org Admin' },
  { email: 'agent1@acme.test', who: 'Acme · Agent' },
  { email: 'customer1@acme.test', who: 'Acme · Customer' },
  { email: 'admin@globex.test', who: 'Globex · Org Admin' },
  { email: 'agent1@globex.test', who: 'Globex · Agent' }
]
const DEMO_PASSWORD = 'Password@123'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email, password)
      navigate('/tickets', { replace: true })
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Sign in</h1>
        <p className="muted">Multi-tenant helpdesk</p>
        <ErrorBanner error={error} />
        <form onSubmit={submit} className="form">
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="muted small">
          New organization? <Link to="/register">Register it</Link>
        </p>

        <div className="demo">
          <div className="demo-title">Demo accounts · password {DEMO_PASSWORD}</div>
          <div className="demo-list">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.email}
                type="button"
                className="demo-item"
                onClick={() => {
                  setEmail(a.email)
                  setPassword(DEMO_PASSWORD)
                }}
              >
                <span>{a.who}</span>
                <span className="muted small">{a.email}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
