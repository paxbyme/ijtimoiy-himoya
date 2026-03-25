# Manager Scripts

Developer provisioning scripts for the Manager app.

## Prerequisites
- Node.js 18+
- Firebase service account key file

## Setup
1. Place your Firebase service account JSON at `../firebase-service-account.json` or set `FIREBASE_SERVICE_ACCOUNT_PATH`
2. Run `npm install`

## Usage

### Create a Manager
```bash
npm run create-manager -- --phone 1234567890 --password SecurePass123 --name "John Manager" --department "Engineering"
```

### Seed Sample Data
```bash
npm run seed-data -- --managerId <MANAGER_UID>
```
