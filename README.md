# Real-Time Collaborative Project Management Platform (Base Version)

A full-stack Kanban-style project management tool where teams see each
other's changes live: when one person moves a task, assigns it, or comments
on it, everyone else looking at that project sees it happen — no refresh
needed.

This is a **base version** deliberately kept simple and readable. It's meant
to be a solid foundation you extend yourself (see "Future improvements"),
not a finished product.

---

## 1. Project overview

Teams create projects, add members, create tasks, and track them through a
`TODO → IN_PROGRESS → DONE` board. Every meaningful change (task created,
status changed, assigned, commented on) is:

1. Persisted via a normal REST call.
2. Recorded in an activity log ("Riddhi moved *API Integration* from `TODO`
   to `IN_PROGRESS`.").
3. Broadcast over WebSocket to anyone else currently viewing that project.
4. Turned into a notification for anyone it directly concerns (e.g. the
   task's assignee).

The end-to-end workflow the app supports:

```
Register → Login → Create project → Add member → Create task
  → Assign task → Move task on the board → Add comment
  → Other user sees the update in real time
  → Activity history records it → Dashboard numbers update
```

---

## 2. Features

- **Auth**: registration, login, JWT-based sessions, password hashing (BCrypt).
- **Projects**: create/view/update/delete, with per-project membership.
- **Members**: add/remove by email, with per-project roles (`OWNER` / `ADMIN` / `MEMBER`).
- **Tasks**: title, description, status, priority, assignee, due date; full CRUD.
- **Kanban board**: drag a card between `TODO` / `IN_PROGRESS` / `DONE` columns.
- **Assignment**: assign/reassign tasks to any project member.
- **Comments**: threaded chronologically per task.
- **Activity history**: an audit trail of everything that happened in a project.
- **Real-time updates**: WebSocket (STOMP over SockJS) push for task changes,
  comments, membership changes, and personal notifications.
- **Notifications**: in-app, for assignment, status changes, and comments.
- **Dashboard**: task counts by status, overdue tasks, high-priority tasks,
  and tasks assigned to you — scoped to the project you're viewing.
- **Optimistic concurrency control**: two people editing the same task can't
  silently overwrite each other (see §6).

---

## 3. Architecture

Classic layered backend, talking to a React SPA over REST + one WebSocket connection:

```
┌──────────────┐        REST (JSON, JWT)        ┌───────────────────────────────┐
│              │ ─────────────────────────────► │  Controller layer              │
│  React SPA   │                                 │   ↓                            │
│ (Vite, JS)   │ ◄───── WebSocket (STOMP) ─────  │  Service layer (business logic)│
│              │        /topic/projects/{id}     │   ↓                            │
│              │        /topic/users/{id}/...    │  Repository layer (Spring Data)│
└──────────────┘                                 │   ↓                            │
                                                  │  PostgreSQL / H2               │
                                                  └───────────────────────────────┘
```

- **Controller** → **Service** → **Repository** → **Database**, with each
  layer only talking to the one below it.
- Services are where all authorization, validation-that-needs-the-DB, and
  cross-cutting concerns (activity logging, notifications, WebSocket
  publishing) happen — controllers stay thin.
- `WebSocketEventPublisher` is the single place that knows about STOMP
  destinations, so services don't need to know WebSocket details directly.

### Backend package layout

```
com.collabplatform
├── config          # SecurityConfig, WebSocketConfig
├── security        # JWT service/filter, UserPrincipal, UserDetailsService, WsSubscriptionAuthorizer
├── entity          # JPA entities + enums
├── repository      # Spring Data JPA repositories
├── dto             # Request/response DTOs, grouped by feature
├── service         # Business logic (one service per feature area)
├── event           # WsBroadcastEvent + its after-commit listener (see §8)
├── controller      # REST endpoints (thin, delegate to services)
└── exception       # Custom exceptions + @RestControllerAdvice handler
```

---

## 4. Technology stack

**Backend:** Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation,
WebSocket), Maven, PostgreSQL (H2 in-memory by default for zero-config
local dev), JJWT for JWT, Lombok.

**Frontend:** React 18, Vite, React Router, plain CSS (no framework),
`@stomp/stompjs` + `sockjs-client` for WebSocket.

**Real-time transport:** STOMP over SockJS (WebSocket with automatic fallback).

---

## 5. Database design

```
users
 ├─ id, name, email (unique), password_hash, role, created_at

projects
 ├─ id, name, description, owner_id → users.id, created_at, updated_at

project_members                       (join table: who's in which project, and as what)
 ├─ id, project_id → projects.id, user_id → users.id, role, joined_at
 └─ UNIQUE(project_id, user_id)

tasks
 ├─ id, project_id → projects.id, title, description, status, priority,
 │  assignee_id → users.id (nullable), creator_id → users.id,
 │  due_date, created_at, updated_at, version (optimistic lock)

comments
 ├─ id, task_id → tasks.id, author_id → users.id, content, created_at

activities                            (audit log, immutable)
 ├─ id, project_id → projects.id, actor_id → users.id, task_id (nullable),
 │  type, message, created_at

notifications
 ├─ id, recipient_id → users.id, project_id → projects.id, task_id (nullable),
 │  type, message, read, created_at
```

Design notes:
- `project_members` is the only place per-project **roles** live. `users.role`
  is a coarse, global Spring Security role (`USER` / `PROJECT_ADMIN`) — actual
  authorization for "can this user manage this project's members / edit its
  settings" is always decided by their `ProjectMember.role`
  (`OWNER` > `ADMIN` > `MEMBER`), not the global role. This keeps permissions
  meaningful per-project (you can be an ADMIN on one project and a plain
  MEMBER on another) instead of a single global flag.
- `tasks.version` backs optimistic locking (see §6).
- No `attachments`, `tags`, or `subtasks` tables — deliberately out of scope
  for the base version (see §15).

---

## 6. Concurrency / data consistency

Two people can be looking at the same task at once. To avoid one person's
change silently clobbering another's:

- Every `Task` has a `@Version` column (`version`).
- Every "write" endpoint that changes an existing task (`PUT /tasks/{id}`,
  `PATCH /tasks/{id}/status`) requires the client to send back the `version`
  it last saw.
- The service compares that to the task's current version in the database.
  If they don't match, it means someone else already changed the task since
  the client loaded it — the request is rejected with **409 Conflict** and a
  clear message, instead of overwriting the other person's change.
- The frontend reacts to a 409 by discarding its optimistic UI update and
  re-fetching the current task, so the person sees what actually happened
  and can retry.
- This is intentionally simple application-level optimistic concurrency
  control (an ETag-like version token), not distributed locking — appropriate
  for a base version. See §15 for where you'd go from here (e.g. real ETags,
  or CRDT-style field-level merging for text edits).

Project creation and member addition are wrapped in `@Transactional` service
methods so a project is never left half-created (e.g. project row without
its owner's membership row).

---

## 7. REST API

All endpoints are prefixed `/api` and (except `/api/auth/**`) require
`Authorization: Bearer <jwt>`.

### Auth
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account, returns `{ token, user }` |
| POST | `/api/auth/login` | Log in, returns `{ token, user }` |

### Users
| Method | Path | Description |
|---|---|---|
| GET | `/api/users/me` | Current user's profile |

### Projects
| Method | Path | Description |
|---|---|---|
| POST | `/api/projects` | Create a project (you become `OWNER`) |
| GET | `/api/projects` | List projects you're a member of |
| GET | `/api/projects/{id}` | Project details |
| PUT | `/api/projects/{id}` | Update name/description (`ADMIN`/`OWNER`) |
| DELETE | `/api/projects/{id}` | Delete project (`OWNER` only) |
| GET | `/api/projects/{id}/members` | List members |
| POST | `/api/projects/{id}/members` | Add a member by email (`ADMIN`/`OWNER`) |
| DELETE | `/api/projects/{id}/members/{userId}` | Remove a member (`ADMIN`/`OWNER`) |
| GET | `/api/projects/{id}/dashboard` | Dashboard stats for the project |
| GET | `/api/projects/{id}/activities` | Activity history, newest first |

### Tasks
| Method | Path | Description |
|---|---|---|
| POST | `/api/projects/{id}/tasks` | Create a task |
| GET | `/api/projects/{id}/tasks` | List tasks in the project |
| GET | `/api/tasks/{id}` | Task details |
| PUT | `/api/tasks/{id}` | Update title/description/priority/due date (needs `version`) |
| PATCH | `/api/tasks/{id}/status` | Move to a new status (needs `version`) |
| PATCH | `/api/tasks/{id}/assign` | Assign/unassign |
| DELETE | `/api/tasks/{id}` | Delete (creator, or `ADMIN`/`OWNER`) |
| GET | `/api/tasks/mine/assigned` | Tasks assigned to you, across all projects |
| GET | `/api/tasks/mine/created` | Tasks you created, across all projects |
| GET | `/api/tasks/mine/overdue` | Your overdue tasks, across all projects |

### Comments
| Method | Path | Description |
|---|---|---|
| POST | `/api/tasks/{id}/comments` | Add a comment |
| GET | `/api/tasks/{id}/comments` | List comments, oldest first |

### Notifications
| Method | Path | Description |
|---|---|---|
| GET | `/api/notifications` | List your notifications, newest first |
| GET | `/api/notifications/unread-count` | `{ count }` |
| PATCH | `/api/notifications/{id}/read` | Mark one as read |

All error responses share one shape:
```json
{
  "timestamp": "2026-09-04T10:15:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "This task was changed by someone else since you last loaded it. Please refresh and try again.",
  "details": null
}
```

---

## 8. WebSocket architecture

**Endpoint:** `ws://<host>/ws` (STOMP over SockJS, so it also works through
proxies that don't support raw WebSocket upgrades).

**Authentication:** the STOMP `CONNECT` frame carries an
`Authorization: Bearer <jwt>` STOMP header (not an HTTP header — WebSocket
handshakes don't carry your custom headers the same way, so the JS client
sets it via STOMP `connectHeaders`). A server-side channel interceptor
(`WebSocketConfig`) validates it and attaches the user's id as the session's
`Principal`.

**Authorization (per-subscription, not just per-connection):** authenticating
the CONNECT frame is not enough on its own — Spring's simple broker will
deliver messages to any destination a connected session subscribes to,
regardless of who they are. Every `SUBSCRIBE` frame is therefore separately
checked by `WsSubscriptionAuthorizer`:
- `/topic/projects/{projectId}` — rejected unless the caller is a member of that project.
- `/topic/users/{userId}/notifications` — rejected unless the caller *is* that user.
- anything else under `/topic/**` — rejected; the app never publishes there.

A rejected subscription gets a STOMP `ERROR` frame instead of being silently
granted. Without this check, any client that can open a WebSocket connection
to `/ws` could subscribe to an arbitrary project id (or another user's
notification feed) and receive its live data — an IDOR over WebSocket rather
than REST. `WsSubscriptionAuthorizer` is unit tested directly
(`WsSubscriptionAuthorizerTest`), independent of a running WebSocket server.

**Destinations:**
| Destination | Who subscribes | What's sent |
|---|---|---|
| `/topic/projects/{projectId}` | Anyone currently viewing that project | Every task/comment/member/activity event for that project |
| `/topic/users/{userId}/notifications` | Only that user (their own browser tab(s)) | Their personal notifications, as they're created |

**Event envelope** (`WsEvent`), the same shape for every message:
```json
{ "type": "TASK_STATUS_CHANGED", "payload": { /* TaskResponse */ }, "timestamp": "..." }
```

**Event catalog** (`WsEventType`):

| Event | Published by | Received by | Payload |
|---|---|---|---|
| `TASK_CREATED` | `TaskService.createTask` | project topic | `TaskResponse` |
| `TASK_UPDATED` | `TaskService.updateTaskDetails` | project topic | `TaskResponse` |
| `TASK_STATUS_CHANGED` | `TaskService.changeStatus` | project topic | `TaskResponse` |
| `TASK_ASSIGNED` | `TaskService.assignTask` | project topic | `TaskResponse` |
| `TASK_DELETED` | `TaskService.deleteTask` | project topic | task id (number) |
| `COMMENT_ADDED` | `CommentService.addComment` | project topic | `CommentResponse` |
| `ACTIVITY_CREATED` | `ActivityService.record` | project topic | `ActivityResponse` |
| `MEMBER_ADDED` | `ProjectService.addMember` | project topic | `ProjectMemberResponse` |
| `MEMBER_REMOVED` | `ProjectService.removeMember` | project topic | removed user's id (number) |
| `PROJECT_UPDATED` | `ProjectService.updateProject` | project topic | `ProjectResponse` |
| `NOTIFICATION_CREATED` | `NotificationService.notify` | personal topic | `NotificationResponse` |

**How the frontend updates its state:** each page that needs live data calls
the `useProjectSocket(projectId, onEvent)` hook (see
`frontend/src/context/useProjectSocket.js`), which subscribes for exactly as
long as that component is mounted (i.e. while the user is looking at that
project) and hands each parsed event to a callback. That callback does a
targeted, in-memory patch of local state — e.g. the Kanban board replaces the
one task that changed rather than re-fetching the whole board. The
dashboard, being cheaper to just recompute, re-fetches on any task-related
event instead. The always-mounted `AppLayout` separately subscribes to the
personal notifications topic via `useNotificationsSocket` to keep the
sidebar's unread badge live everywhere in the app.

**Important:** WebSockets are *only* used for these "something changed,
here's the new state" push notifications. Every actual write still goes
through a normal REST call first; the WebSocket message is sent by the
server only after that REST call's transaction has committed.

This ordering is enforced structurally, not just by convention: `*Service`
classes never talk to `SimpMessagingTemplate` directly. They call
`WebSocketEventPublisher`, which publishes a `WsBroadcastEvent` through
Spring's `ApplicationEventPublisher`; `WsBroadcastListener` is the only place
that actually sends over STOMP, and it does so via
`@TransactionalEventListener(phase = AFTER_COMMIT)`. That guarantees the
broadcast fires strictly after the enclosing `@Transactional` method commits
— and, importantly, *not at all* if that transaction rolls back — instead of
racing the commit or firing for a write that never actually happened.

---

## 9. Authentication

- Passwords are hashed with BCrypt (`spring-security-crypto`), never stored
  or logged in plaintext.
- On login/register, the server issues a JWT (HS256) containing the user's
  id and email, signed with `app.jwt.secret` and expiring after
  `app.jwt.expiration-ms` (24h by default).
- The frontend stores the token in `localStorage` and sends it as
  `Authorization: Bearer <token>` on every REST call and on the STOMP
  `CONNECT` frame.
- A custom `JwtAuthenticationFilter` validates the token on every request and
  populates Spring Security's context; unauthenticated requests to protected
  endpoints get a clean JSON 401 (not Spring's default HTML error page).
- **No secrets are hardcoded.** `app.jwt.secret`, the database URL/credentials,
  and the allowed CORS origin are all read from environment variables (with
  safe local-dev defaults) — see `application.yml`. Change `JWT_SECRET` before
  deploying anywhere real.

---

## 10. How to run the backend

Requirements: Java 17+, Maven 3.9+.

```bash
cd backend

# Uses an in-memory H2 database by default - no setup needed to try it out.
mvn spring-boot:run
```

The API is now at `http://localhost:8080/api`, and the WebSocket endpoint at
`ws://localhost:8080/ws`. An H2 web console is available at
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:collabplatform`,
user `sa`, empty password) if you want to poke at the data directly. A basic
liveness check is at `http://localhost:8080/actuator/health` (no other
actuator endpoints are exposed).

**To use PostgreSQL instead** (recommended before you build on top of this),
set these environment variables before running:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/collabplatform
export DB_DRIVER=org.postgresql.Driver
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_db_password
export JWT_SECRET=$(openssl rand -base64 48)   # or any long random string
mvn spring-boot:run
```

Create the database first (`createdb collabplatform` or equivalent) —
`ddl-auto: update` will create/adjust the tables for you on startup.

`backend/.env.example` documents every environment variable above (plus
`CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `LOG_LEVEL`, `DDL_AUTO`, `SHOW_SQL`)
with the same defaults that are inline in
`backend/src/main/resources/application.yml`. Spring Boot doesn't auto-load a
`.env` file, so either export the variables another way (shell, IDE run
config, your deployment platform's env var settings) or:

```bash
cp .env.example .env    # then edit it
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

---

## 11. How to run the frontend

Requirements: Node 18+.

```bash
cd frontend
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL / VITE_WS_URL if needed
npm run dev
```

Open `http://localhost:5173`. It expects the backend to be running at
`http://localhost:8080` (configurable via `.env`); the backend's default CORS
config (`CORS_ALLOWED_ORIGINS`) already allows `http://localhost:5173`.

---

## 12. Testing

### Backend (automated)

```bash
cd backend
mvn test
```

`AuthControllerTest` covers: registration (success + duplicate email +
invalid input), login (success + wrong password), and unauthenticated
access to a protected endpoint.

`WsSubscriptionAuthorizerTest` covers the WebSocket SUBSCRIBE-time
authorization rule (see §8): a project member can subscribe to that
project's topic; a non-member is rejected; an unauthenticated caller is
rejected; a user can subscribe to their own notifications topic but not
someone else's; an unrecognized `/topic/**` destination is rejected. This is
a plain Mockito unit test — no server or WebSocket connection needed.

`ProjectTaskFlowIntegrationTest` runs the whole workflow end to end against a
real (in-memory) database via `MockMvc`: register two users → create a
project → a non-member is blocked from viewing it → add a member → a plain
`MEMBER` is blocked from managing membership → create and assign a task →
change its status → **a second status change reusing the stale version is
rejected with 409** (the optimistic-locking guarantee from §6) → add a
comment → activity history contains the expected entries → the dashboard
counts reflect the board → the assignee has a notification → a `MEMBER`
cannot delete the project. A second test confirms invalid task input (blank
title) is rejected with 400.

### Manual WebSocket test (two browser sessions)

Automated tests exercise the REST + business logic side, but "does the
other person's browser update live" is easiest to confirm by hand:

1. Start the backend and frontend as above.
2. Open the app in two browsers (or one normal window + one incognito
   window, so they don't share `localStorage`/the session).
3. **Browser A:** register/log in as User A, create a project "Demo
   Project," and add User B's email as a `MEMBER`.
4. **Browser B:** register/log in as User B, open "Demo Project" from the
   project list, and go to its Board tab.
5. **Browser A:** on the same project's Board tab, click **New task**,
   fill in a title, and create it.
   → **Expect:** the new card appears on **Browser B's** board within a
   second or two, with no manual refresh.
6. **Browser A:** drag that task from `TODO` to `IN_PROGRESS`.
   → **Expect:** Browser B's board reflects the column move live, and if
   Browser B opens the task, its Activity/dashboard numbers update too.
7. **Browser A:** open the task and post a comment.
   → **Expect:** if Browser B has that task's detail page open, the comment
   appears live; either way, Browser B's Notifications page (and sidebar
   badge) picks up a new notification if they're the assignee or creator.
8. **Optimistic locking check:** open the *same task* in both browsers'
   detail pages. In Browser A, change its priority and save. In Browser B
   (which still has the old `version` loaded in its form), try to save an
   edit too. → **Expect:** Browser B gets a 409-driven "this task changed
   elsewhere" message instead of silently overwriting Browser A's edit.
9. **Subscription authorization check (optional, confirms §8's fix):** with
   Browser A logged in but *not* a member of some other project (e.g. one
   User B owns and hasn't shared), open that other project's board URL
   directly by editing the address bar to its id. Because the REST calls are
   still membership-checked, the page itself will show a 403 — but if you
   open the browser console and manually try
   `stompClient.subscribe('/topic/projects/<that-other-id>', console.log)`
   against an authenticated connection, the subscription should be rejected
   (a STOMP `ERROR` frame, visible in the console/network tab) rather than
   silently succeeding.

---

## 13. Example workflow (walkthrough)

1. Alice registers, creating her account and receiving a JWT.
2. Alice creates project **"Website Revamp"** — she's now its `OWNER`.
3. Alice adds Bob (`bob@example.com`) as a `MEMBER`.
4. Alice creates a task **"API Integration"** with priority `HIGH`, and
   assigns it to Bob. Bob gets a `TASK_ASSIGNED` notification, live.
5. Bob (viewing the board) drags "API Integration" from `TODO` to
   `IN_PROGRESS`. Alice, also viewing the board, sees the card move columns
   without refreshing. The activity log now reads *"Bob moved API
   Integration from TODO to IN_PROGRESS."*
6. Bob comments *"Started on this, should be done by Friday."* Alice — the
   task's creator — gets a `COMMENT_ADDED` notification.
7. Alice checks the project dashboard: 1 total task, 1 in progress, 1 high
   priority, 0 overdue.
8. Bob finishes and drags the card to `DONE`. The activity log records both
   the status change and a `TASK_COMPLETED` entry. Alice's dashboard now
   shows 1 done, 0 in progress.

---

## 14. Known limitations

Things that are true of the app *as it stands today* (as opposed to §15,
which is features deliberately not built yet):

- **List endpoints can issue N+1 queries.** `Task`'s `assignee`/`creator`/
  `project` associations (and the equivalent on `Comment`/`Activity`/
  `Notification`) are `FetchType.LAZY`, and the list-returning repository
  methods don't `JOIN FETCH` them. For a project's real task/member counts
  (tens, not thousands) this isn't noticeable, but it's the first thing to
  fix — with `@EntityGraph` or an explicit `JOIN FETCH` query — before
  putting real load on `GET /api/projects/{id}/tasks` or the dashboard.
- **Two moderate-severity `npm audit` findings** (`esbuild`'s dev-server
  origin check, `react-router`'s redirect handling) are only fixable by
  upgrading Vite to 8.x / React Router to 7.x, both breaking major-version
  changes. Not applied here since neither is exploitable in the production
  build this app ships (`esbuild`'s issue is dev-server-only) and a major
  router upgrade needs its own testing pass. Run `npm audit` in `frontend/`
  to see current details.
- **No frontend automated tests or linter.** The backend has JUnit coverage
  (§12); the frontend doesn't yet have Vitest/RTL or an ESLint config.
- **Building requires outbound access to Maven Central**
  (`repo.maven.apache.org`) to resolve backend dependencies, and npm's
  registry for the frontend. Behind a restrictive corporate proxy/firewall
  that blocks either, `mvn` / `npm install` will fail until it's allowlisted.
- **No rate limiting** on `/api/auth/login` or `/api/auth/register` — see
  §15 for what you'd add.

---

## 15. Future improvements

Deliberately left out of this base version, in roughly the order you'd
likely want to tackle them:

- **File attachments** on tasks/comments (would need object storage, e.g. S3).
- **Docker Compose** for one-command backend + Postgres + frontend startup.
- **Cloud deployment** configs (e.g. Fly.io/Render for the backend, static
  hosting for the frontend), plus a proper migration tool (Flyway/Liquibase)
  instead of `ddl-auto: update`.
- **Refresh tokens** and token revocation (current JWTs just expire; there's
  no logout-everywhere or refresh flow).
- **Rate limiting** on `/api/auth/login` and `/api/auth/register` (e.g. via
  Bucket4j or an API gateway) to slow down credential-stuffing/brute-force
  attempts.
- **Richer notifications**: due-soon/overdue reminders on a schedule (needs a
  scheduled job, not just event-driven notifications), email digests, push
  notifications.
- **Subtasks / checklists**, task tags/labels, custom board columns.
- **Drag-and-drop reordering within a column**, not just between columns.
- **More sophisticated concurrency**: field-level merge for concurrent text
  edits (e.g. two people editing a description at once), or real HTTP ETags
  instead of a body-level `version` field.
- **Analytics**: velocity/burndown charts, per-member workload view.
- **Pagination** for activities/notifications/comments (currently unbounded
  lists — fine for a base project, not for years of history).
- **Role management UI** for changing an existing member's role (currently
  only settable at add-time).
