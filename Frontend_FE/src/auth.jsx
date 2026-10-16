import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, onUnauthorized, tokenStore } from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(Boolean(tokenStore.get()))

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    onUnauthorized(logout)
    if (!tokenStore.get()) return
    api('/auth/me')
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false))
  }, [logout])

  const acceptSession = useCallback((auth) => {
    tokenStore.set(auth.accessToken)
    setUser(auth.user)
    return auth.user
  }, [])

  const login = useCallback(
    (email, password) => api('/auth/login', { method: 'POST', body: { email, password } }).then(acceptSession),
    [acceptSession]
  )

  const registerOrganization = useCallback(
    (form) => api('/auth/register-organization', { method: 'POST', body: form }).then(acceptSession),
    [acceptSession]
  )

  const value = useMemo(() => {
    const role = user && user.role
    return {
      user,
      loading,
      login,
      logout,
      registerOrganization,
      isAdmin: role === 'ORG_ADMIN',
      isStaff: role === 'ORG_ADMIN' || role === 'AGENT',
      isCustomer: role === 'CUSTOMER'
    }
  }, [user, loading, login, logout, registerOrganization])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
