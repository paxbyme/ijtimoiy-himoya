# Ijtimoiy Yordamchi

AI-powered employee-management platform for managers and staff. The system combines a **Next.js dashboard**, a **Flutter mobile application**, and a **Spring Boot API** with RAG, real-time voice, role-based access, and provider-switchable AI services.

## Product overview

Managers can manage employees, assign tasks, monitor KPIs, configure AI rules, and upload internal documents. Staff use the mobile app to complete work, communicate with managers, and ask an AI assistant questions grounded in company knowledge.

## Architecture

```text
Next.js manager dashboard ─┐
                          ├── Spring Boot API ── PostgreSQL / Firebase
Flutter staff application ┘          │
                                     ├── Gemini or OpenAI
                                     └── Embeddings + Pinecone RAG
```

## Key capabilities

- Multi-role manager and employee workflows
- Firebase authentication with custom role claims
- Task assignment, status tracking, and KPI monitoring
- AI chat with streaming responses
- Runtime-switchable Gemini and OpenAI providers
- Document ingestion, embeddings, and Pinecone vector search
- Real-time voice sessions with WebSocket audio transport
- Uzbek and Russian mobile localization
- Layered Flutter architecture using Riverpod, repositories, and data sources
- Automated web, backend, and mobile CI checks

## Technology stack

| Layer | Technologies |
|---|---|
| Mobile | Flutter, Dart, Riverpod, Dio, GoRouter, Firebase |
| Web | Next.js, React, TypeScript |
| Backend | Java 17, Spring Boot |
| AI | Gemini, OpenAI, Whisper, embeddings, RAG |
| Vector search | Pinecone |
| Authentication | Firebase Auth |
| Quality | Flutter Test, Mocktail, Playwright, GitHub Actions |
| Deployment | Docker-based services and environment-driven configuration |

## AI provider design

AI functionality is exposed through a provider-neutral service layer. Chat, streaming, embeddings, transcription, OCR, and live voice can be routed through the configured provider without changing application consumers.

This makes provider migration reversible and allows the platform to respond to quota, availability, and model-lifecycle changes.

## Mobile architecture

```text
Presentation / Riverpod providers
              ↓
         Repositories
              ↓
          Data sources
              ↓
       API and Firebase
```

## Continuous integration

GitHub Actions currently validates:

- Spring Boot backend compilation
- Next.js linting and production build
- Flutter dependency resolution and static analysis
- Flutter automated tests

## Local development

```bash
# Backend
cd backend
./gradlew bootRun

# Web dashboard
cd web
npm install
npm run dev

# Mobile
cd mobile
flutter pub get
flutter run
```

Configure Firebase and AI credentials locally. Never commit secrets or production credentials.

## Roadmap

- Expand backend test coverage and enable tests in every CI build
- Add observable AI usage and latency metrics
- Strengthen document-ingestion evaluation and retrieval benchmarks
- Add deployment and architecture decision records
- Publish product screenshots and a short demo

## Author

Built by [Bekzod Sirojiddinov](https://github.com/paxbyme).

[LinkedIn](https://www.linkedin.com/in/paxbyme) · [Telegram](https://t.me/lazyswe) · [Email](mailto:contact@paxbyme.dev)
