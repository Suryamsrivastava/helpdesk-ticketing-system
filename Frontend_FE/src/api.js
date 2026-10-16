// Thin fetch wrapper: adds the Bearer token, parses JSON and turns API errors into ApiError.

const TOKEN_KEY = 'helpdesk.token'
const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch (e) {
      return null
    }
  },
  set(token) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch (e) {
      // storage blocked: the session just will not survive a reload
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch (e) {
      // ignore
    }
  }
}

export class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || `Request failed (${status})`)
    this.status = status
    this.fieldErrors = (body && body.fieldErrors) || []
  }
}

let unauthorizedHandler = () => {}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

export async function api(path, { method = 'GET', body, query } = {}) {
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, value)
  })
  const qs = params.toString()

  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${BASE_URL}/api${path}${qs ? `?${qs}` : ''}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined
    })
  } catch (e) {
    throw new ApiError(0, { message: 'Cannot reach the server. Is the backend running?' })
  }

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch (e) {
      data = { message: text }
    }
  }
  if (!response.ok) {
    if (response.status === 401 && token) unauthorizedHandler()
    throw new ApiError(response.status, data)
  }
  return data
}

/** Field errors as "field: message" lines, or the plain message. */
export function errorText(error) {
  if (error && error.fieldErrors && error.fieldErrors.length) {
    return error.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(' · ')
  }
  return (error && error.message) || 'Something went wrong'
}
