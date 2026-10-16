import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { RoleBadge } from './ui.jsx'

export default function Layout({ children }) {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const signOut = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brand">
            <span className="brand-mark">HD</span>
            <span className="brand-org">{user.organizationName}</span>
          </div>
          <nav className="nav">
            <NavLink to="/tickets" end>
              Tickets
            </NavLink>
            <NavLink to="/tickets/new">New ticket</NavLink>
            {isAdmin && <NavLink to="/users">Users</NavLink>}
            {isAdmin && <NavLink to="/email-intake">Email intake</NavLink>}
          </nav>
          <div className="whoami">
            <span className="whoami-name">{user.fullName}</span>
            <RoleBadge role={user.role} />
            <button className="btn btn-ghost" onClick={signOut}>
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  )
}
