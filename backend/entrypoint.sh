#!/bin/sh
# Write Firebase credentials from env var to temp file
if [ -n "$FIREBASE_CREDENTIALS_JSON" ]; then
  echo "$FIREBASE_CREDENTIALS_JSON" > /tmp/firebase-service-account.json
  export FIREBASE_CREDENTIALS_PATH=/tmp/firebase-service-account.json
fi
exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar
