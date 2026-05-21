# `core/`

App-wide primitives — used by every feature, depend on nothing in the project.

```
core/
├── constants/
│   ├── app_constants.dart    # Timeouts, page size, validation limits
│   └── route_names.dart      # GoRouter paths (wired in Step 1.2)
├── error/
│   ├── exceptions.dart       # Low-level — thrown by data sources
│   └── failures.dart         # User-facing — returned by repositories
└── utils/
    ├── validators.dart       # Form validators (UZ phone, password, email…)
    ├── formatters.dart       # Date/number/currency display
    └── extensions.dart       # String / DateTime / BuildContext helpers
```

**Rules of the road**

- `core/` must not import from `models/`, `services/`, `providers/`, `screens/`, or `widgets/`.
- New cross-cutting helpers go here. Feature-specific helpers stay in their feature folder.
- `Failure` is the only error type that should reach the UI. `Exception`s are caught and mapped inside repositories (Step 2).
