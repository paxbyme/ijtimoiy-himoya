# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the Flutter mobile app of the Manager monorepo (see root `CLAUDE.md` for the backend API, Firestore collections, and overall system). This file is the authoritative description of the mobile architecture — it supersedes the "Mobile App Architecture" section in the root CLAUDE.md, which predates the MVVM migration.

## Commands

```bash
flutter pub get
flutter analyze                      # Lint
flutter test                         # All tests
flutter test test/unit/repositories/task_repository_test.dart   # Single file
flutter test --plain-name "returns NetworkFailure when offline" # Single test
flutter run --dart-define=ENV=dev    # Run on device/emulator
flutter gen-l10n                     # Regenerate localizations (also runs automatically on build)
```

### Build environments

Runtime config comes from `--dart-define` flags; `EnvConfig` (`lib/core/constants/env_config.dart`) is the single source of truth. Flags: `ENV` (`dev`/`staging`/`prod`), `API_URL` (full URL including `/api`; `API_BASE_URL` is a legacy alias), `VERBOSE_LOGS`. All environments currently default to the Railway production URL, so `flutter run` with no flags hits production — pass `--dart-define=API_URL=http://10.0.2.2:8080/api` to target a local backend from the Android emulator.

```bash
flutter build apk --dart-define=ENV=staging --dart-define=API_URL=https://staging.example.com/api
flutter build apk --release --dart-define=ENV=prod --dart-define=API_URL=https://manager-app-production-53c2.up.railway.app/api
```

Release signing: copy `android/key.properties.example` to `android/key.properties`.

## Architecture

Layered MVVM with a strict one-way dependency flow:

```
Screen (ConsumerWidget) → providers/ (Riverpod) → data/repositories/ → data/datasources/ → Dio | Firestore
```

- **`core/`** — cross-cutting primitives: `constants/` (`EnvConfig`, `AppConstants`, `Routes`), `network/` (`DioClient`, `NetworkInfo`), `error/` (`Failure` sealed class + `FailureMapper`, custom exceptions), `utils/`
- **`data/datasources/remote/`** — one class per feature (`TaskRemoteDataSource`, etc.) doing raw Dio calls; throw exceptions on failure
- **`data/datasources/local/`** — despite the name, `ChatLocalDataSource` is the Firestore SDK access for real-time chat streams (not a cache); `AuthLocalDataSource` is local persistence
- **`data/repositories/`** — one per feature; wrap every datasource call in a `_guard` that checks `NetworkInfo.isConnected` and returns `Either<Failure, T>` (dartz). Repositories never let Dio/Firebase exceptions escape — `FailureMapper.fromException()` converts them to `Failure` values
- **`providers/`** — one file per feature slice. Each declares the DI chain (datasource provider → repository provider) plus `FutureProvider`s for reads (which `fold` the Either and `throw` the Failure into `AsyncValue.error`), `StreamProvider`s for Firestore real-time data, and `Notifier` classes for mutations that invalidate related list providers on success and return `bool` (or an error-message `String?`). Shared infra (`dioProvider`, `networkInfoProvider`) lives in `auth_provider.dart`
- **`models/`** — grouped by domain (`auth/`, `task/`, `chat/`, `kpi/`, `shared/`) with `fromJson`/`toJson`
- **`config/`** — `AppTheme` (Material 3, Inter font, blue #2563EB) and `ApiConfig`, a thin facade over `EnvConfig` that also derives `wsBaseUrl` (http→ws); new code should read `EnvConfig` directly
- **`screens/`** — by role: `auth/`, `staff/`, `manager/`, `developer/`; **`widgets/`** — shared UI by domain

### Error-handling convention

User-facing `Failure.message` strings are in Uzbek. The chain is: datasource throws → repository returns `Left(Failure)` → provider throws the Failure into `AsyncValue` → screen renders via `.when(loading/error/data)`. Mutations in Notifiers `fold` instead and surface the message directly.

### Routing & roles

`routerProvider` (GoRouter) watches `authStateProvider` (Firebase auth stream) and `userProfileProvider` (backend profile fetch); the redirect holds users on `/splash` until both resolve, then routes by role: `DEVELOPER` → `/developer/*`, manager → `/manager/*`, staff → `/staff/*`. Each role has its own `ShellRoute` with bottom navigation using `NoTransitionPage`. Full-screen pushes (task detail, create task, live voice, manager↔staff chat) use `parentNavigatorKey: _rootNavigatorKey` to escape the shell.

### Network details

- `DioClient.create()` builds the single shared Dio with a Firebase ID-token interceptor (`Authorization: Bearer`); all remote datasources receive it via `dioProvider`
- AI chat streaming: `AiRemoteDataSource` consumes SSE from `POST /ai/chat/stream` with `ResponseType.stream`
- Live voice (`LiveVoiceScreen`): WebSocket to `${ApiConfig.wsBaseUrl}/ai/live` using `web_socket_channel`, audio via `record` + `flutter_pcm_sound`
- Manager↔staff chat is real-time via Firestore streams (`messages`, `conversations` collections), not the REST API

### Localization

`flutter gen-l10n` setup (`l10n.yaml`): ARB files in `lib/l10n/`, Uzbek (`app_uz.arb`) is the template, Russian is the other locale. Generated class is `AppL10n` (checked in, not in `.dart_tool`). Access strings via `AppL10n.of(context)`. Adding a UI string means editing both ARB files and regenerating.

## Testing

Unit tests live under `test/unit/` (repositories, providers) using `mocktail`. Shared `MockX` classes are in `test/helpers/mocks.dart` and JSON fixtures in `test/helpers/fixtures.dart` — extend those rather than defining mocks inline. Repository tests follow the pattern: stub `NetworkInfo.isConnected`, stub the remote datasource, assert on the returned `Either`.
