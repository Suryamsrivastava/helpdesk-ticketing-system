import React from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Layout from './components/Layout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import TicketListPage from './pages/TicketListPage.jsx'
import NewTicketPage from './pages/NewTicketPage.jsx'
import TicketDetailPage from './pages/TicketDetailPage.jsx'
import UsersPage from './pages/UsersPage.jsx'
import EmailIntakePage from './pages/EmailIntakePage.jsx'

function RequireAuth({ children, adminOnly = false }) {
  const { user, loading, isAdmin } = useAuth()
  if (loading) return <div className="page-loading">Loading…</div>
  if (!user) return <Navigate to="/login" replace />
  if (adminOnly && !isAdmin) return <Navigate to="/tickets" replace />
  return <Layout>{children}</Layout>
}

function GuestOnly({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-loading">Loading…</div>
  return user ? <Navigate to="/tickets" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/tickets" element={<RequireAuth><TicketListPage /></RequireAuth>} />
      <Route path="/tickets/new" element={<RequireAuth><NewTicketPage /></RequireAuth>} />
      <Route path="/tickets/:id" element={<RequireAuth><TicketDetailPage /></RequireAuth>} />
      <Route path="/users" element={<RequireAuth adminOnly><UsersPage /></RequireAuth>} />
      <Route path="/email-intake" element={<RequireAuth adminOnly><EmailIntakePage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/tickets" replace />} />
    </Routes>
  )
}
