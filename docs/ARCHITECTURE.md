# AI-Powered Employee Management System - Architecture

## Overview
A monorepo containing three applications that work together to provide AI-powered employee management.

## Components

### Backend (`backend/`)
- **Tech**: Java 17 + Spring Boot 3.2 + Gradle
- **Package**: `com.manager`
- **Purpose**: REST API server handling business logic, AI processing, and Firebase Admin operations
- **Port**: 8080

### Web Dashboard (`web/`)
- **Tech**: Next.js 14 + TypeScript + Tailwind CSS + shadcn/ui
- **Purpose**: Manager portal for staff management, task assignment, KPI monitoring, and AI rule configuration
- **Port**: 3000

### Mobile App (`mobile/`)
- **Tech**: Flutter + Riverpod + Material 3
- **Purpose**: Staff app for AI chatbot, task management, KPI viewing, and real-time chat

### Scripts (`scripts/`)
- **Tech**: TypeScript + Firebase Admin SDK
- **Purpose**: Developer provisioning tools

## Data Flow

```
┌─────────┐     ┌──────────┐     ┌───────────────┐
│   Web   │────>│  Backend  │────>│   Firestore   │
│Dashboard│<────│  (REST)   │<────│   (Database)  │
└─────────┘     └──────────┘     └───────────────┘
                     │                    ^
┌─────────┐          │                    │
│  Mobile │──────────┘          (real-time listeners
│   App   │─────────────────────for chat messages)
└─────────┘

Backend also connects to:
- Gemini API (AI chat + text embeddings)
- Pinecone (vector similarity search for RAG)
```

## Authentication Flow
1. Developer creates Manager user via provisioning script
2. Manager logs into web dashboard
3. Manager creates Staff users via dashboard (backend creates Firebase Auth + Firestore user)
4. Staff logs into mobile app with phone + password
5. All API calls include Firebase ID token in Authorization header
6. Backend verifies token and extracts role/department from custom claims

## AI Architecture
```
User Message
     ↓
┌─────────────────────────────────────┐
│ Prompt Assembly:                     │
│  1. Golden Rules (hardcoded)         │
│  2. Department Rules (from Firestore)│
│  3. RAG Context (from Pinecone)      │
│  4. Conversation History             │
│  5. User Message                     │
└─────────────────────────────────────┘
     ↓
  Gemini 2.0 Flash
     ↓
  AI Response → saved to ai_conversations
```

## KPI Calculation
Auto-triggered when a task is marked complete:
- **Timeliness** (40pts): On-time completion rate × 40
- **Completion** (30pts): Completed / total tasks × 30
- **Efficiency** (30pts): Avg(min(deadline_duration / actual_duration, 1.0)) × 30

## Environment Variables

### Backend
| Variable | Description |
|---|---|
| FIREBASE_CREDENTIALS_PATH | Path to Firebase service account JSON |
| GEMINI_API_KEY | Google Gemini API key |
| PINECONE_API_KEY | Pinecone API key |
| PINECONE_INDEX_URL | Pinecone index endpoint URL |

### Web
| Variable | Description |
|---|---|
| NEXT_PUBLIC_FIREBASE_* | Firebase client config |
| NEXT_PUBLIC_API_URL | Backend API URL |

### Mobile
Configure in `lib/config/api_config.dart`
