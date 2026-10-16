import React, { useEffect, useState } from 'react'
import { api, errorText } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ErrorBanner } from '../components/ui.jsx'

export default function EmailIntakePage() {
  const { user } = useAuth()
  const [intake, setIntake] = useState(null)
  const [visible, setVisible] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    api('/organization/email-intake')
      .then(setIntake)
      .catch((err) => setError(errorText(err)))
  }, [])

  const rotate = async () => {
    if (!window.confirm('Generate a new key? The old key stops working immediately, so the mailbox script must be updated.')) {
      return
    }
    setError(null)
    try {
      setIntake(await api('/organization/email-intake/rotate', { method: 'POST' }))
      setVisible(true)
      setNotice('A new key was generated. Update INBOUND_KEY in the mailbox script.')
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <div className="narrow">
      <h1>Email intake</h1>
      <p className="muted">
        Mails sent to {user.organizationName}’s helpdesk address become tickets. A mail from a registered customer’s
        address is linked to that customer automatically; any other address lands under <b>Needs linking</b> for an agent
        to map.
      </p>
      <ErrorBanner error={error} />
      {notice && <div className="notice">{notice}</div>}

      {intake && (
        <section className="card">
          <h2>Connection details for the mailbox script</h2>
          <dl className="details intake">
            <dt>Endpoint</dt>
            <dd>
              <code>POST {'<backend URL>'}{intake.endpointPath}</code>
            </dd>
            <dt>Header</dt>
            <dd>
              <code>{intake.headerName}</code>
            </dd>
            <dt>Inbound key</dt>
            <dd>
              <code className="key">{visible ? intake.inboundKey : '•'.repeat(24)}</code>
              <div className="key-actions">
                <button className="btn btn-ghost" onClick={() => setVisible(!visible)}>
                  {visible ? 'Hide' : 'Show'}
                </button>
                <button className="btn btn-danger" onClick={rotate}>
                  Generate new key
                </button>
              </div>
            </dd>
          </dl>
          <p className="muted small">
            The key decides which organization a mail belongs to, so keep it secret. The ready-made Google Apps Script
            and its setup steps are in <code>docs/apps-script/HelpdeskEmailIntake.gs</code>.
          </p>
        </section>
      )}
    </div>
  )
}
