# Multi-Tenant Helpdesk & Ticketing System

A web application where several organizations manage support tickets side by side. Each organization has its own users and tickets, and **no user can read or change another organization's data**. Isolation is enforced in the backend on every request, not just hidden in the UI.

## Live demo

**https://helpdesk.manageone.me** runs the production build: one Spring Boot jar serving UI + API, MySQL, published through a Cloudflare Tunnel.

- **Create your own tenant.** Click **Register organization** on the login page; you become that organization's Org Admin and can add agents and customers. Several organizations live on the same server and database, and none of them can see yours. (The demo accounts in [Quick start](#1-quick-start) exist only in a local run; the live site seeds no demo data.)
- **Try email intake in your organization.** As Org Admin open **Email intake**, copy your organization's key, then:

  ```bash
  curl -X POST https://helpdesk.manageone.me/api/inbound/email \
    -H "X-Inbound-Key: <your key>" -H "Content-Type: application/json" \
    -d '{"threadId":"t-1","messageId":"t-1-m1","fromEmail":"someone@example.com","fromName":"Someone","subject":"Printer not working","body":"Hello"}'
  ```

  The ticket appears under **Needs linking**. Send the same `threadId` with a new `messageId` and it is added as a comment. Use the email of a customer you created and the ticket is linked to them automatically.
- **A real mailbox is connected too.** A mail to **support@manageone.me** becomes a ticket in the ManageOne organization (Cloudflare Email Routing → Gmail → Apps Script → `POST /api/inbound/email`, see [section 4](#4-email-intake-extra)).

| Part | Stack |
|---|---|
| Backend (`Backend_BE`) | Java 17+, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Bean Validation, springdoc OpenAPI |
| Database | MySQL 8 (tests run on in-memory H2) |
| Frontend (`Frontend_FE`) | React 18, React Router 6, Vite 4 |
| Tests | JUnit 5, Mockito, Spring MockMvc |

---

## 1. Quick start

### Prerequisites

- **JDK 17 or newer** (`java -version`). Maven is **not** required; the Maven wrapper (`mvnw`) is included.
- **Node.js 14.18 or newer** (18+ recommended) and npm.
- **MySQL 8**, either from Docker (below) or any local MySQL with a `root` user and an empty password.
  Other credentials can be passed in environment variables; see [Configuration](#6-configuration).

### Step 1: start MySQL

```bash
docker compose up -d mysql
```

That starts MySQL 8.4 on port 3306 with the `helpdesk` database and an empty root password, which matches the backend defaults. If you already run MySQL locally, skip this step: the backend creates the `helpdesk` database itself (`createDatabaseIfNotExist=true`).

### Step 2: start the backend (port 8080)

```bash
cd Backend_BE
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

On first start the schema is created and **demo data is seeded** (2 organizations, 8 users, 8 tickets, including two that arrived by email).

### Step 3: start the frontend (port 5173)

```bash
cd Frontend_FE
npm install
npm run dev
```

Open **http://localhost:5173**. The dev server proxies `/api` to `http://localhost:8080`.

### Step 4: sign in with a demo account

All demo accounts use the password **`Password@123`**. The login page also lists them as one-click buttons.

| Organization | Role | Email |
|---|---|---|
| Acme Corp | Org Admin | `admin@acme.test` |
| Acme Corp | Support Agent | `agent1@acme.test`, `agent2@acme.test` |
| Acme Corp | Customer | `customer1@acme.test`, `customer2@acme.test` |
| Globex Inc | Org Admin | `admin@globex.test` |
| Globex Inc | Support Agent | `agent1@globex.test` |
| Globex Inc | Customer | `customer1@globex.test` |

You can also register a brand-new organization from the login page.

### Production deployment

For running it on a server (one jar serving UI + API, `prod` profile with startup safety checks, Windows auto-start, Cloudflare Tunnel), see **[DEPLOY.md](DEPLOY.md)**.

### API documentation

- Swagger UI: **http://localhost:8080/swagger-ui.html** (log in with `POST /api/auth/login`, then click **Authorize** and paste the `accessToken`)
- OpenAPI JSON: http://localhost:8080/v3/api-docs (a snapshot is committed at [`docs/openapi.json`](docs/openapi.json))

### Run the tests

```bash
cd Backend_BE
./mvnw test
```

No database or Docker is needed: tests use in-memory H2. **61 tests** run, all passing.

---

## 2. Features

| Requirement | How it is covered |
|---|---|
| Login and roles | JWT login; roles **Org Admin**, **Support Agent**, **Customer** |
| Organization management | Public "register organization" creates a tenant and its first admin; admins add agents, customers and other admins |
| Ticket management | Create, view, update (subject, description, priority), assign or unassign, change status, close or reopen. Each ticket has subject, description, priority, status, requester, assigned agent and created/updated/resolved/closed timestamps |
| Search and filters | Filter by status, priority and assigned agent (or unassigned), plus a text search on subject or ticket id; paginated and sortable |
| Comments and history | Public replies and **internal notes**; full history of creation, status, assignment, priority and edit changes |
| Internal notes hidden | Customers never receive internal notes: they are excluded in the database query, and customers get 403 if they try to write one |
| REST API | Bean Validation, consistent JSON error body, correct status codes (201, 400, 401, 403, 404, 409) |
| Frontend | Login, register organization, ticket list with filters, ticket creation, ticket detail (conversation, actions, history), user management |
| **Extra: email intake** | Mails to an organization's helpdesk address become tickets. A registered customer's mail is linked to them automatically; any other address opens an unlinked ticket that an agent maps to the right customer ([section 4](#4-email-intake-extra)) |

### What each role can do

| Action | Org Admin | Support Agent | Customer |
|---|---|---|---|
| See tickets | all in own org | all in own org | **only own** |
| Create ticket | yes (for self or a customer) | yes (for self or a customer) | yes (for self) |
| Edit subject / description | yes | yes | own tickets |
| Change priority | yes | yes | no |
| Assign / unassign | yes | yes | no |
| Change status | any allowed move | any allowed move | close, or reopen a resolved/closed ticket |
| Link an email ticket to a customer | yes | yes | no |
| View / rotate the inbound email key | yes | no | no |
| Public comment | yes | yes | own tickets |
| Internal note (write and read) | yes | yes | **never** |
| Manage users | yes (own org) | read-only list | no |

### Ticket status flow

```
OPEN ⇄ IN_PROGRESS → RESOLVED → CLOSED
 ↑         ↑            │          │
 └─────────┴── reopen ──┴──────────┘
```

| From | Allowed moves |
|---|---|
| OPEN | IN_PROGRESS, RESOLVED, CLOSED |
| IN_PROGRESS | OPEN, RESOLVED, CLOSED |
| RESOLVED | IN_PROGRESS, OPEN (reopen), CLOSED |
| CLOSED | OPEN (reopen) |

Any other move returns **409 Conflict**. Assigning an agent to an `OPEN` ticket moves it to `IN_PROGRESS` automatically. A `CLOSED` ticket cannot be edited or reassigned until it is reopened.

---

## 3. How tenant isolation works

This is the core of the design:

1. **The tenant comes from the server, never from the client.** The JWT holds the user id and organization id. On every request `JwtAuthenticationFilter` loads that user from the database and checks that it still exists, is active and still belongs to the same organization. The resulting `AuthUser` (with `organizationId`) is the **only** tenant id the services use. No endpoint accepts an organization id as input.
2. **Every query is scoped.** Single tickets and users are loaded with `findByIdAndOrganizationId(id, me.organizationId())`. Ticket search always starts from `TicketSpecifications.inOrganization(...)`, and for customers it also adds `requestedBy(me.id())`, whatever filters the client sends.
3. **Out-of-scope rows are 404, not 403.** A ticket from another organization (or another customer's ticket) answers `404 Not Found`, so the API does not even reveal that the id exists.
4. **Cross-tenant references are rejected.** Assigning an agent, or raising a ticket on behalf of a requester, looks the user up inside the caller's organization, so an id from another tenant is a `400`.
5. **Role checks run in two layers.** `@PreAuthorize` on the controllers, plus explicit checks in the services. The service checks are what the unit tests cover.
6. **Role changes and deactivation take effect immediately.** The role used for authorization is read from the database on each request, not trusted from the token.

`TenantIsolationIntegrationTest` exercises this end to end over HTTP: an agent of organization B gets 404 on organization A's ticket, its comments, history, status and assignment endpoints; a customer cannot see another customer's ticket even with `?requesterId=`; customers never receive internal notes.

---

## 4. Email intake (extra)

Not required by the assignment, but a common helpdesk need: a customer writes to the organization's support mailbox instead of using the portal.

```
customer mails support@acme ──► Gmail (Acme's helpdesk mailbox)
                                   │  Google Apps Script, every 5 min (docs/apps-script/HelpdeskEmailIntake.gs)
                                   ▼
            POST /api/inbound/email   header X-Inbound-Key: <Acme's key>
                                   │
   key → organization (the ONLY way the tenant is chosen; nothing in the mail can change it)
   sender = active customer of THIS organization? → ticket linked to them
   any other address                             → ticket with no requester, in "Needs linking"
   thread already has a ticket?                  → the mail is added as a comment instead
   message id seen before?                       → ignored (safe retries)
```

- **Customers may write from any address.** If it is their registered email, the ticket is theirs immediately. Otherwise the ticket keeps the sender's address and name, shows up for agents under the **Needs linking** filter, and an agent links it to the right customer (`PUT /api/tickets/{id}/requester`). From then on the customer sees it in their portal. A wrong link can be changed, and the history keeps the original sender address.
- **Unlinked tickets are invisible to customers.** A customer sees a ticket only once it is linked to them.
- **One key per organization.** The Org Admin sees it on the **Email intake** page and can rotate it; the old key stops working at once. A key belongs to exactly one tenant, so a mail can never land in another organization, and a customer of organization B mailing organization A is treated as an unknown sender there.
- **Replies on the same thread** become comments marked "via email". The same thread id in two organizations gives two separate tickets.

**Try it without Gmail** (demo keys exist only on a freshly seeded database):

```bash
# a registered customer -> linked automatically (201, "linkedToCustomer": true)
curl -X POST http://localhost:8080/api/inbound/email \
  -H "X-Inbound-Key: demo-acme-inbound-key-change-me" -H "Content-Type: application/json" \
  -d '{"threadId":"t-100","messageId":"t-100-m1","fromEmail":"customer1@acme.test","fromName":"Carlos","subject":"Refund not received","body":"I returned the item 10 days ago."}'

# any other address -> unlinked, appears under "Needs linking" for agents
curl -X POST http://localhost:8080/api/inbound/email \
  -H "X-Inbound-Key: demo-acme-inbound-key-change-me" -H "Content-Type: application/json" \
  -d '{"threadId":"t-101","messageId":"t-101-m1","fromEmail":"carlos.home@gmail.com","fromName":"Carlos","subject":"Wrong size delivered","body":"Ordered L, got M."}'
```

Then log in as `agent1@acme.test`, tick **Needs linking**, open the ticket and link it to Carlos Customer.

**Using a real mailbox:** paste [`docs/apps-script/HelpdeskEmailIntake.gs`](docs/apps-script/HelpdeskEmailIntake.gs) into a Google Apps Script project owned by the helpdesk Gmail account, set `BACKEND_URL` and `INBOUND_KEY`, and run `installTrigger()` once. Apps Script runs on Google's servers, so the backend must be reachable from the internet (a deployed URL, or a tunnel such as ngrok for a local demo).

**Limits.** A mail's `From` address can be forged. Gmail's SPF/DKIM checks reduce this; matching a sender only ever links them to a customer of the same organization, and agents can relink. Agent replies are not mailed back to the customer yet (see [next steps](#10-not-included--possible-next-steps)).

---

## 5. API overview

All endpoints are under `/api` and, except login, registration and email intake (which uses the organization's inbound key), need `Authorization: Bearer <token>`. Ticket search also takes `unlinked=true` (staff) for email tickets not linked to a customer yet.

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/auth/register-organization` | public | Create organization + first Org Admin, returns token |
| POST | `/auth/login` | public | Log in, returns token |
| GET | `/auth/me` | any | Current user and organization |
| GET | `/organization` | any | Current organization |
| GET | `/tickets` | any | Search: `status`, `priority`, `assigneeId`, `unassigned`, `requesterId` (staff), `q`, `page`, `size`, `sort` |
| POST | `/tickets` | any | Create a ticket (`requesterId` for staff only) |
| GET | `/tickets/{id}` | any | Ticket details |
| PATCH | `/tickets/{id}` | any | Edit subject / description / priority |
| PUT | `/tickets/{id}/assignee` | admin, agent | Assign (`{"assigneeId": 3}`) or unassign (`null`) |
| PUT | `/tickets/{id}/status` | any | Change status, e.g. `{"status": "CLOSED"}` |
| PUT | `/tickets/{id}/requester` | admin, agent | Link a ticket (e.g. from an unknown email address) to a customer |
| GET | `/tickets/{id}/history` | any | Change history |
| GET | `/tickets/{id}/comments` | any | Comments (customers: public only) |
| POST | `/tickets/{id}/comments` | any | Add a reply, or `"internal": true` note (staff) |
| GET | `/users` | admin, agent | Users of my organization (`role`, paging) |
| GET | `/users/agents` | admin, agent | Active agents, for assignment |
| POST | `/users` | admin | Create admin / agent / customer |
| PATCH | `/users/{id}` | admin | Rename, change role, activate / deactivate |
| GET | `/organization/email-intake` | admin | Inbound key and endpoint for the mailbox script |
| POST | `/organization/email-intake/rotate` | admin | Replace the inbound key |
| POST | `/inbound/email` | inbound key | Mail → ticket, or comment on an existing thread (201 created, 200 comment/duplicate, 401 bad key) |

**Paging.** `?page=0&size=20&sort=createdAt,desc`. Max page size is 100. Tickets sort by `createdAt`, `updatedAt`, `id`, `subject`; an unknown sort field is a 400. Responses use one envelope: `{ content, page, size, totalElements, totalPages, last }`.

**Errors** always have this shape:

```json
{
  "timestamp": "2026-10-16T04:37:02Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tickets",
  "fieldErrors": [{ "field": "subject", "message": "must not be blank" }]
}
```

| Status | When |
|---|---|
| 400 | Validation error, malformed JSON, bad enum or sort value, invalid assignee or requester |
| 401 | Missing, invalid or expired token; wrong email or password |
| 403 | Role not allowed (customer assigning, writing an internal note, managing users…); deactivated account at login |
| 404 | Not found **or** belongs to another organization / another customer |
| 409 | Invalid status move, editing a closed ticket, duplicate email, concurrent edit (optimistic locking) |

---

## 6. Configuration

All settings have working defaults for local runs and can be overridden with environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/helpdesk?createDatabaseIfNotExist=true&...` | JDBC URL |
| `DB_USERNAME` | `root` | DB user |
| `DB_PASSWORD` | *(empty)* | DB password |
| `JWT_SECRET` | a dev-only value | HMAC secret, at least 32 characters. **Set this outside local development.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed origins |
| `SEED_DEMO_DATA` | `true` | Seed the demo organizations on an empty database |
| `SERVER_PORT` | `8080` | Backend port |

Example with a password-protected MySQL:

```bash
DB_USERNAME=helpdesk DB_PASSWORD=secret ./mvnw spring-boot:run
```

---

## 7. Data model

```
organizations ──< users            (organization_id, role, unique email; org has a unique inbound_key)
organizations ──< tickets >── users (requester_id, assignee_id; channel WEB/EMAIL, sender_email, email_thread_id)
tickets ──< ticket_comments >── users (author_id, internal flag; external_author_email for mail replies)
tickets ──< ticket_history  >── users (actor_id, type, from_value, to_value)
```

- `requester_id` is null only for an email ticket from an unregistered address, until an agent links it. `(organization_id, email_thread_id)` is unique, so one thread is one ticket per tenant.

- Every ticket carries `organization_id`, and every query filters on it. Composite indexes start with `organization_id` (`(organization_id, status)`, `(organization_id, assignee_id)`, …) because every query is tenant-scoped.
- `tickets.version` gives optimistic locking: two people saving the same ticket at once get a 409 instead of a silent overwrite.
- Timestamps are stored in UTC (`Instant`).

---

## 8. Project structure

```
Backend_BE/src/main/java/com/helpdesk
├── auth/           login, organization registration, /me
├── organization/   tenant entity
├── user/           users, roles, user management (admin)
├── ticket/         tickets, search specifications, status policy, service, controller
├── comment/        replies and internal notes
├── history/        audit trail entity
├── inbound/        email intake: inbound key → tenant, sender matching, thread → comment
├── security/       JWT service, auth filter, AuthUser principal, JSON 401/403
├── config/         security, OpenAPI, demo data seeder, inbound-key backfill
└── common/         error body, global exception handler, paging, sort whitelist

Backend_BE/src/test/java/com/helpdesk
├── ticket/TicketServiceTest          tenant/ownership, create, assign, status, priority rules (Mockito)
├── ticket/TicketStatusPolicyTest     status state machine
├── comment/CommentServiceTest        internal notes never reach customers (Mockito)
├── security/JwtServiceTest           token round trip, tampering, expiry
├── TenantIsolationIntegrationTest    full HTTP stack on H2: isolation, roles, validation, codes
└── EmailIntakeIntegrationTest        inbound key, auto-link, unknown sender, linking, threads, retries

docs/
├── openapi.json                      API spec snapshot
└── apps-script/HelpdeskEmailIntake.gs  mailbox script for email intake

Frontend_FE/src
├── api.js, auth.jsx                  fetch wrapper with token, auth context
├── components/                       layout, badges, pagination
└── pages/                            login, register, ticket list, new ticket, ticket detail, users, email intake
```

---

## 9. Assumptions and design decisions

- **Shared database, shared schema, `organization_id` on every row.** This is the simplest model that is easy to reason about and test. Separate schemas or databases per tenant would add isolation at an operational cost the assignment does not need.
- **Email is globally unique**, so login needs only email and password; the organization is taken from the user.
- **Anyone can register a new organization** and becomes its Org Admin. Agents and customers are created by an admin (there is no customer self-signup).
- **Out-of-scope resources return 404** rather than 403, to avoid leaking whether an id exists in another tenant.
- **Agents can raise tickets on behalf of customers** (for example after a phone call) and can assign tickets to any active agent of their organization. Only users with the Agent role can be assignees.
- **History is visible to everyone who can see the ticket.** It contains status, assignment, priority and "details edited" events, never note text.
- **Schema is managed by Hibernate (`ddl-auto=update`)** to keep the setup to one command. For production I would switch to Flyway migrations.
- **JWT is stateless with no refresh token.** Logout happens on the client. Because the user is re-checked on every request, deactivating a user blocks their existing token immediately.
- **MariaDB:** the app also runs on MariaDB (for example XAMPP). Hibernate logs a dialect version warning there; MySQL 8 is the target.
- **Email intake auto-links only customers.** A mail from a staff address, or from a customer of another organization, opens an unlinked ticket. Email message ids must be unique per mailbox (Gmail's are) because they are used to ignore retries.
- **Upgrading an existing database:** `ddl-auto=update` adds new columns but does not relax NOT NULL. A database created before email intake needs `requester_id`, `actor_id` and `author_id` made nullable once; a fresh database needs nothing.

## 10. Not included / possible next steps

- Mailing agent replies back into the customer's email thread, and other email notifications
- Auto-reopening a resolved ticket when the customer replies by email
- Attachments on tickets and comments
- SLA timers and reporting dashboards
- Password reset and refresh tokens
- Flyway migrations, Docker image for the app itself, CI pipeline
- Frontend tests (only the backend has automated tests)
