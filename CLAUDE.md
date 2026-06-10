# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI-powered employee management system. Managers use the web dashboard to manage staff, assign tasks, monitor KPIs, configure AI rules, and upload documents for RAG. Staff use the mobile app to chat with an AI assistant, view tasks, check KPI scores, and message their manager.

## Monorepo Structure

- **`backend/`** — Java 17 + Spring Boot 3.2 + Gradle (REST API on port 8080)
- **`web/`** — Next.js 14 + TypeScript + Tailwind + shadcn/ui (manager dashboard on port 3000)
- **`mobile/`** — Flutter + Riverpod + Material 3 (staff app)
- **`scripts/`** — TypeScript provisioning tools (create managers, seed data)
- **`e2e/`** — Playwright end-to-end tests (critical-flow lifecycle tests)
- **`docs/`** — Architecture documentation (`ARCHITECTURE.md`)
- **`firestore.rules`** — Firestore security rules (all writes go through backend except chat messages)

## Build & Run Commands

### Backend
```bash
cd backend
./gradlew bootRun                    # Start dev server (port 8080)
./gradlew build                      # Build JAR
./gradlew test                       # Run tests (JUnit 5)
./gradlew test --tests "com.manager.SomeTest.methodName"  # Single test
```
Requires env vars: `FIREBASE_CREDENTIALS_PATH`, `GEMINI_API_KEY`, `PINECONE_API_KEY`, `PINECONE_INDEX_URL`

`StartupValidationConfig` validates all required env vars at boot and throws `IllegalStateException` if any are missing — the app will not start with a missing config.

**Note:** Test coverage is minimal — only a skipped context load test exists. All tests require Firebase credentials to run.

### Web
```bash
cd web
npm install
npm run dev                          # Start dev server (port 3000)
npm run build                        # Production build
npm run lint                         # ESLint
```
Copy `web/.env.example` to `web/.env.local` and fill in Firebase config + `NEXT_PUBLIC_API_URL`.

### Mobile
```bash
cd mobile
flutter pub get
flutter run                          # Run on connected device/emulator
flutter analyze                      # Lint
flutter test                         # Run tests
flutter build apk                    # Debug APK
flutter build apk --release          # Release APK (requires key.properties)
```
API base URL resolved by `mobile/lib/core/constants/env_config.dart` from `--dart-define` flags (`ENV`, `API_URL`; `API_BASE_URL` is a legacy alias). All environments currently default to the production Railway URL — pass `--dart-define=API_URL=http://10.0.2.2:8080/api` to target a local backend from the Android emulator.

Android release signing: copy `mobile/android/key.properties.example` to `mobile/android/key.properties` and fill in keystore details.

### Scripts
```bash
cd scripts
npm install
npm run create-manager -- --phone 1234567890 --password SecurePass123 --name "John" --department "Engineering"
npm run seed-data -- --managerId <UID>
```

### E2E Tests
```bash
cd e2e
npm install
npx playwright test                  # Run all tests (Chromium, sequential)
```
Requires env vars: `WEB_URL`, `MANAGER_PHONE`, `MANAGER_PASSWORD`, `BACKEND_URL`. Tests the full manager → staff lifecycle (auth, staff creation, task assignment, task completion, KPI updates).

### Firebase
```bash
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
firebase deploy --only hosting          # Deploys web/out (SPA with rewrites to /index.html)
```

## Architecture

### Authentication Flow
All users authenticate via Firebase Auth. Phone numbers are mapped to email format `{phone}@manager.local`. The backend's `FirebaseAuthFilter` validates Bearer tokens on every request and extracts `uid`, `role`, and `departmentId` from custom claims. Routes under `/api/auth/**` are public; all other `/api/**` routes require authentication. Role-based access uses `@PreAuthorize("hasRole('MANAGER')")` annotations. Controllers access user info via `httpRequest.getAttribute("uid")`, `getAttribute("role")`, `getAttribute("departmentId")`.

User provisioning flow: developer creates Manager via scripts → Manager creates Staff via dashboard → backend creates Firebase Auth user + Firestore doc + sets custom claims.

### Backend Package Layout (`com.manager`)
- `config/` — Spring beans: `SecurityConfig`, `FirebaseConfig`, `CorsConfig` (env-driven origins), `GeminiConfig`, `PineconeConfig`, `AsyncConfig` (doc-processor thread pool: core 2, max 5), `StartupValidationConfig`
- `security/` — `FirebaseAuthFilter` (extends `OncePerRequestFilter`)
- `filter/` — `RequestLoggingFilter` (logs all requests with requestId/userId in MDC)
- `exception/` — `GlobalExceptionHandler` (@ControllerAdvice, handles @Valid failures + unhandled exceptions, returns `ApiResponse` format)
- `controller/` — 9 REST controllers (including `HealthController`)
- `service/` — 12 service classes (business logic)
- `repository/` — 8 Firestore repositories (Firebase Admin SDK, no ORM)
- `dto/` — Request/response DTOs + `ApiResponse<T>` wrapper + `PageResponse<T>` pagination wrapper

### Backend REST API

| Controller | Base Path | Key Endpoints |
|---|---|---|
| Auth | `/api/auth` | `POST /verify` (public) |
| Health | `/api/health` | `GET /` (public, liveness check) |
| User | `/api/users` | `POST /staff`, `GET /staff` (paginated), `GET /staff/{id}`, `PUT /staff/{id}`, `DELETE /staff/{id}`, `GET /me` |
| Task | `/api/tasks` | CRUD (paginated list) + `PUT /{id}/complete` (STAFF only, triggers KPI recalculation) |
| Ai | `/api/ai` | `POST /chat`, `POST /chat/stream` (SSE), `POST /feedback`, `GET /conversations`, `GET /conversations/{id}`, `DELETE /conversations/{id}` |
| Chat | `/api/chat` | `POST /send`, `GET /conversations`, `GET /messages/{conversationId}` (paginated) |
| AiRules | `/api/ai-rules` | CRUD (MANAGER only) |
| Document | `/api/documents` | `POST /upload` (multipart), `GET /`, `DELETE /{id}` (MANAGER only) |
| Kpi | `/api/kpi` | `GET /me`, `GET /{staffId}`, `GET /rankings`, `POST /calculate/{staffId}` |

**Rate limiting:** AI chat endpoints (`/api/ai/chat` and `/api/ai/chat/stream`) are rate-limited to 20 requests/minute per user via `RateLimiterService` (Firestore-backed sliding window). Returns HTTP 429 if exceeded.

**Pagination:** `GET /staff`, `GET /tasks`, `GET /messages/{conversationId}` return `PageResponse<T>` with a `content` array. Clients read `.content` for the list items.

**AI streaming:** `POST /api/ai/chat/stream` returns `text/event-stream` (SSE). Events carry `type`, `token`, `conversationId`, `sources`, `done`, or `error` fields. Emitter timeout is 120s.

**AI feedback:** `POST /api/ai/feedback` accepts `{ conversationId, messageIndex, rating, comment }` and stores to `ai_feedback` collection.

### Backend Response Pattern
All endpoints return `ApiResponse<T>`:
```java
ApiResponse.ok(data)              // { success: true, message: "Success", data: T }
ApiResponse.ok("message", data)   // { success: true, message: "...", data: T }
ApiResponse.error("message")      // { success: false, message: "...", data: null }
```
Controllers use try-catch with `ResponseEntity.badRequest().body(ApiResponse.error(...))` for errors. Both web and mobile extract data via `response.data.data || response.data` to handle wrapped/unwrapped responses. Paginated endpoints return `PageResponse<T>` inside `data` — read `.content` for items.

### Service Dependency Chains
- **Task completion → KPI**: `TaskService.completeTask()` auto-triggers `KpiService.calculateKpi()` (best-effort, failure doesn't fail the task)
- **Document upload → RAG indexing**: `DocumentService` → `DocumentProcessorService` (with @Retryable: max 3 attempts, exponential backoff 2s→4s, capped 10s) → `EmbeddingService` → `GeminiService.embed()` → Pinecone upsert
- **AI chat**: `AiController` → `RateLimiterService.isRateLimited()` → fetches active AI rules + `RagService.query()` (Pinecone vector search → Firestore chunk lookup) → assembles system prompt → `GeminiService.chat()`
- **Gemini integration**: Direct HTTP via OkHttp (not Google SDK) — `GeminiService` handles both chat (gemini-2.5-flash) and embeddings (text-embedding-004)

### Backend Data Access (Firestore, not JPA)
All repositories use Firebase Admin SDK directly. Pattern: manual `toMap()`/`fromDoc()` conversions, `ApiFuture<QuerySnapshot>` with `.get()` blocking calls, no ORM/transactions. Document IDs are either Firebase UIDs or Firestore auto-generated. All repository methods throw `ExecutionException` and `InterruptedException`. Repositories handle Firestore `Timestamp` type in date fields alongside String/Long fallbacks.

### Web App Architecture
- App Router with all pages under `/dashboard/*` (protected by auth guard in dashboard layout)
- `src/lib/api.ts` — Axios instance with automatic Firebase token injection and 401 redirect
- `src/lib/utils.ts` — `cn()` utility for merging Tailwind classes
- `src/types/index.ts` — shared TypeScript type definitions
- `src/hooks/useAuth.ts` — auth hook
- `src/context/AuthContext.tsx` — Firebase auth state + user profile. `login()` converts phone to `{phone}@manager.local`
- State management: React Query (`useQuery`/`useMutation`, staleTime 60s, retry 1) for API data, `useState` for UI state
- Forms: react-hook-form + Zod validation
- UI: shadcn/ui components in `src/components/ui/`, feature components in `src/components/`
- Charts: recharts for KPI visualizations
- Toasts: sonner (via `toast.success()` / `toast.error()`)
- Dates: date-fns for formatting
- Real-time chat uses Firestore `onSnapshot` listeners directly (not through backend). Conversation ID: `[userId, staffId].sort().join("_")`
- Data extraction pattern: `res.data.data || res.data` (backend may wrap in `ApiResponse<T>`); for paginated endpoints read `.content`
- Query key convention: `["entity"]` for lists, `["entity", id]` for details, `["entity-relation", id]` for related data
- Mutation pattern: `useMutation` → `onSuccess` invalidates queries + shows toast → dialog closes

**Web routes (`/dashboard/*`):**
- `/` — home/overview
- `/employees` — staff list
- `/employees/[id]` — staff detail
- `/tasks` — task management
- `/kpi` — KPI rankings
- `/ai-rules` — AI rules CRUD
- `/documents` — document upload/list
- `/chat` — conversation list
- `/chat/[staffId]` — chat with staff member
- `/ai-chat` — AI chat interface

### Mobile App Architecture

See `mobile/CLAUDE.md` for the authoritative, detailed description. Summary: layered MVVM — screens (ConsumerWidget) → Riverpod providers → repositories (return `Either<Failure, T>` via dartz) → datasources (Dio REST / Firestore streams). Three role shells (staff, manager, developer) routed by GoRouter based on the user's role claim. Uzbek/Russian localization via `flutter gen-l10n` (Uzbek is the template). Real-time chat via Firestore streams; AI chat via SSE; live voice via WebSocket.

### AI/RAG Pipeline
1. Documents uploaded → PDF extracted (PDFBox) → chunked (500 chars, 100 overlap)
2. Chunks embedded via Gemini `text-embedding-004` → stored in Firestore `document_chunks` + upserted to Pinecone with `departmentId` metadata
3. AI chat: query embedded → Pinecone top-5 search filtered by `departmentId` → chunk content fetched from Firestore by `vectorId` → system prompt assembled (golden rules + department AI rules + RAG context + conversation history) → Gemini 2.5 Flash

### KPI Calculation
Auto-triggered on task completion. Scoped to current month (`yyyy-MM` period). Per-period scoring:
- Timeliness (40pts): on-time completion rate (completedAt ≤ deadline)
- Completion (30pts): completed/total tasks ratio
- Efficiency (30pts): avg(min(deadline_duration / actual_duration, 1.0)) × 30 — capped at 1.0, no bonus for early completion

## Firestore Collections
`users`, `departments`, `tasks`, `kpi_scores`, `conversations`, `messages`, `ai_conversations`, `ai_rules`, `documents`, `document_chunks`, `ai_feedback`, `rate_limits`

All client writes are blocked in Firestore rules except: `messages` (create by sender, update readAt by receiver). Everything else writes through the backend via Firebase Admin SDK. `document_chunks` is a top-level collection (not a subcollection), linked to documents via `documentId` field. `ai_feedback` and `rate_limits` are backend-only (no client access).

### Composite Indexes
Queries requiring composite indexes (defined in `firestore.indexes.json`):
- `tasks`: (departmentId, status, deadline) and (assignedTo, status, deadline)
- `messages`: (conversationId, createdAt)
- `kpi_scores`: (departmentId, period DESC, score DESC)
- `ai_rules`: (departmentId, isActive, priority)
- `document_chunks`: (documentId, chunkIndex)
- `conversations`: (participants CONTAINS, lastMessageAt DESC)
- `ai_conversations`: (staffId, updatedAt DESC)
- `ai_feedback`: (departmentId, createdAt DESC)

Any new multi-field Firestore query may need a new composite index added to `firestore.indexes.json` and deployed.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on push to `main` and pull requests:
- **backend** job: JDK 17, Gradle build (tests skipped in CI)
- **web** job: Node 20, lint + build (requires env vars for Firebase config)
- **mobile** job: Flutter stable 3.x, `flutter analyze` + `flutter test`

## Key Conventions
- Backend uses Lombok (`@Data`, `@Builder`, `@AllArgsConstructor`) — do not write manual getters/setters
- Backend soft-deletes staff (`isActive: false`), not hard deletes
- Backend CORS allowed origins configured via `cors.allowed.origins` env var (not hardcoded)
- Web uses `cn()` utility from `src/lib/utils.ts` for merging Tailwind classes
- Web uses shadcn/ui (base-nova style) — add new components with `npx shadcn@latest add <component>`
- Web path alias: `@/*` maps to `./src/*`
- Web feature components are embedded in page files, not separate component files
- Mobile uses `ConsumerWidget`/`ConsumerStatefulWidget` for Riverpod integration
- Mobile theme defined in `lib/config/app_theme.dart` using Material 3 with Inter font and blue primary (#2563EB)
- Status colors across apps: PENDING (yellow), IN_PROGRESS (blue), COMPLETED (green), CANCELLED (gray)
- Priority colors: LOW (gray/blue), MEDIUM (blue/orange), HIGH (orange/red), URGENT/CRITICAL (red)
