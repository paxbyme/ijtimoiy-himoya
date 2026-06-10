# mobile

Boss Manager — Flutter mobile app (staff + manager + developer).

## Build environments

The app reads runtime config from `--dart-define` flags. `EnvConfig`
(`lib/core/constants/env_config.dart`) is the single source of truth.

| Flag           | Values                     | Default | Notes                                  |
|----------------|----------------------------|---------|----------------------------------------|
| `ENV`          | `dev` / `staging` / `prod` | `dev`   | Drives `EnvConfig.isProduction` etc.   |
| `API_URL`      | full URL incl. `/api`      | per-env | Overrides the per-env default.         |
| `VERBOSE_LOGS` | `true` / `false`           | `false` | Extra logs in non-prod builds.         |

`API_BASE_URL` is still honored as a legacy alias for `API_URL`.

### Dev (local backend or default)
```bash
flutter run --dart-define=ENV=dev
# or against a custom backend:
flutter run --dart-define=ENV=dev --dart-define=API_URL=http://10.0.2.2:8080/api
```

### Staging build
```bash
flutter build apk \
  --dart-define=ENV=staging \
  --dart-define=API_URL=https://staging.example.com/api
```

### Production build
```bash
flutter build apk --release \
  --dart-define=ENV=prod \
  --dart-define=API_URL=https://manager-app-production-53c2.up.railway.app/api
```

## Project layout

```
lib/
├── core/       # cross-cutting primitives (constants, network, errors, utils)
├── config/     # thin facades (ApiConfig, AppTheme)
├── models/     # data classes grouped by domain (auth/, task/, chat/, kpi/, shared/)
├── data/       # repositories + datasources (remote = Dio REST, local = Firestore/cache)
├── providers/  # Riverpod providers (DI + state per feature slice)
├── router/     # GoRouter setup
├── l10n/       # ARB files + generated AppL10n (uz template, ru)
├── screens/    # UI by role (staff/, manager/, developer/, auth/)
└── widgets/    # shared widgets
```

## Common commands

```bash
flutter pub get
flutter analyze
flutter test
```
