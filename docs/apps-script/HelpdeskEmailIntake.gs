/**
 * Helpdesk email intake - Google Apps Script
 *
 * Runs inside the organization's helpdesk Gmail account. Every few minutes it sends
 * each new (unread) mail to the helpdesk backend, which opens a ticket, or adds a
 * comment when the mail's thread already has a ticket.
 *
 * Setup (once per organization mailbox):
 *   1. Log in to the helpdesk Gmail account, open https://script.google.com, create a
 *      project and paste this file.
 *   2. Fill CONFIG below: BACKEND_URL (must be reachable from the internet, HTTPS) and
 *      INBOUND_KEY (Org Admin > Email intake page in the helpdesk portal).
 *   3. Run installTrigger() once and accept the Gmail permissions.
 *
 * Safe to re-run: the backend ignores a message id it has already processed, and a mail
 * is only marked read after the backend accepted it.
 */

var CONFIG = {
  BACKEND_URL: 'https://your-helpdesk-backend.example.com',
  INBOUND_KEY: 'paste-the-key-from-the-email-intake-page',
  SEARCH_QUERY: 'in:inbox is:unread',
  MAX_THREADS_PER_RUN: 20,
  FAILED_LABEL: 'helpdesk/failed'
};

/** Entry point for the time trigger. */
function processHelpdeskInbox() {
  var mailbox = Session.getEffectiveUser().getEmail().toLowerCase();
  var threads = GmailApp.search(CONFIG.SEARCH_QUERY, 0, CONFIG.MAX_THREADS_PER_RUN);

  threads.forEach(function (thread) {
    thread.getMessages().forEach(function (message) {
      if (!message.isUnread()) {
        return;
      }
      var sender = parseSender(message.getFrom());

      // copies of our own replies are not customer mail
      if (sender.email === mailbox) {
        message.markRead();
        return;
      }

      var outcome = sendToHelpdesk({
        threadId: thread.getId(),
        messageId: message.getId(),
        fromEmail: sender.email,
        fromName: sender.name,
        subject: message.getSubject(),
        body: message.getPlainBody()
      });

      if (outcome === 'done') {
        message.markRead();
      } else if (outcome === 'rejected') {
        // the backend will never accept this mail (e.g. invalid sender): park it for a human
        thread.addLabel(getOrCreateLabel(CONFIG.FAILED_LABEL));
        message.markRead();
      }
      // 'retry': left unread, picked up again on the next run
    });
  });
}

/** @return {'done'|'retry'|'rejected'} */
function sendToHelpdesk(payload) {
  try {
    var response = UrlFetchApp.fetch(CONFIG.BACKEND_URL + '/api/inbound/email', {
      method: 'post',
      contentType: 'application/json',
      headers: { 'X-Inbound-Key': CONFIG.INBOUND_KEY },
      payload: JSON.stringify(payload),
      muteHttpExceptions: true
    });
    var code = response.getResponseCode();
    if (code === 200 || code === 201) {
      return 'done';
    }
    console.warn('Helpdesk answered ' + code + ' for message ' + payload.messageId + ': ' + response.getContentText());
    // 401 = wrong key, 409 = two runs raced on the same thread, 5xx = backend trouble: try again later
    if (code === 400 || code === 413 || code === 422) {
      return 'rejected';
    }
    return 'retry';
  } catch (e) {
    console.warn('Helpdesk unreachable: ' + e);
    return 'retry';
  }
}

/** "Riya Sharma <riya@gmail.com>" -> { name: 'Riya Sharma', email: 'riya@gmail.com' } */
function parseSender(from) {
  var match = /^\s*"?([^"<]*?)"?\s*<([^>]+)>\s*$/.exec(from || '');
  if (match) {
    return { name: match[1].trim(), email: match[2].trim().toLowerCase() };
  }
  return { name: '', email: String(from || '').trim().toLowerCase() };
}

function getOrCreateLabel(name) {
  return GmailApp.getUserLabelByName(name) || GmailApp.createLabel(name);
}

/** Run once: checks the inbox every 5 minutes. */
function installTrigger() {
  ScriptApp.getProjectTriggers()
    .filter(function (t) { return t.getHandlerFunction() === 'processHelpdeskInbox'; })
    .forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('processHelpdeskInbox').timeBased().everyMinutes(5).create();
}
