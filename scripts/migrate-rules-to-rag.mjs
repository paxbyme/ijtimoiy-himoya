#!/usr/bin/env node
/**
 * migrate-rules-to-rag.mjs
 *
 * One-off migration: the manager's `ai_rules` collection was stuffed with ~100
 * full legal documents (up to 375k chars each). AiController injects EVERY active
 * rule's full content into the system prompt, producing ~2.17M-token prompts that
 * NO model (Gemini 1M, gpt-4o-mini 128k) can accept -> all AI chat returns 429.
 *
 * Fix: move those documents into the proper RAG pipeline. For each large rule we
 *   1. upload its text as a .txt to POST /api/documents/upload
 *      (backend chunks -> embeds via the active provider -> upserts to Pinecone),
 *   2. deactivate the rule (isActive=false) so it stops bloating the prompt.
 * Genuine short rules (below --min-chars) are left untouched.
 *
 * Reversible: rules are DEACTIVATED, not deleted (unless --delete). RAG docs can
 * be removed from the dashboard if you want to redo it.
 *
 * Usage:
 *   node migrate-rules-to-rag.mjs                 # DRY RUN — shows the plan, changes nothing
 *   node migrate-rules-to-rag.mjs --execute       # perform the migration (deactivate rules)
 *   node migrate-rules-to-rag.mjs --execute --delete   # hard-delete migrated rules instead
 *
 * Config via env (sensible defaults baked in):
 *   BACKEND_URL, FIREBASE_API_KEY, MANAGER_PHONE, MANAGER_PASSWORD, MIN_CHARS
 */

const API = (process.env.BACKEND_URL || 'https://manager-app-production-53c2.up.railway.app/api').replace(/\/$/, '');
const FIREBASE_KEY = process.env.FIREBASE_API_KEY || 'AIzaSyBD9W6_d_xPe-RAZnS9LP1BzNzCglYPGx4';
const PHONE = process.env.MANAGER_PHONE || '998901234567';
const PASSWORD = process.env.MANAGER_PASSWORD || 'SecurePass123';
const MIN_CHARS = Number(process.env.MIN_CHARS || 2000);

const EXECUTE = process.argv.includes('--execute');
const HARD_DELETE = process.argv.includes('--delete');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login() {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: `${PHONE}@manager.local`, password: PASSWORD, returnSecureToken: true }),
    }
  );
  const data = await res.json();
  if (!data.idToken) throw new Error('Login failed: ' + JSON.stringify(data.error || data));
  return data.idToken;
}

async function listRules(token) {
  const res = await fetch(`${API}/ai-rules`, { headers: { Authorization: `Bearer ${token}` } });
  const json = await res.json();
  const rules = json.data ?? json;
  if (!Array.isArray(rules)) throw new Error('Unexpected /ai-rules response: ' + JSON.stringify(json).slice(0, 200));
  return rules;
}

async function listDocuments(token) {
  const res = await fetch(`${API}/documents`, { headers: { Authorization: `Bearer ${token}` } });
  const json = await res.json();
  const docs = json.data ?? json;
  return Array.isArray(docs) ? docs : [];
}

/** title -> the .txt filename we upload it under (so we can detect already-migrated rules). */
function docFilename(title) {
  return String(title || 'rule').replace(/\.(docx?|pdf|txt)$/i, '') + '.txt';
}

/** fetch with timeout + retries — the backend's caller-runs pool can stall under load. */
async function fetchRetry(url, opts, { tries = 3, timeoutMs = 90000 } = {}) {
  let lastErr;
  for (let attempt = 1; attempt <= tries; attempt++) {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), timeoutMs);
    try {
      return await fetch(url, { ...opts, signal: ac.signal });
    } catch (e) {
      lastErr = e;
      await sleep(1000 * attempt);
    } finally {
      clearTimeout(t);
    }
  }
  throw lastErr;
}

/** Upload a rule's text as a .txt document so the backend runs the full RAG pipeline. */
async function uploadAsDocument(token, rule) {
  const title = String(rule.title || 'rule');
  const base = title.replace(/\.(docx?|pdf|txt)$/i, '');
  const filename = `${base}.txt`;
  // Prepend the title as a heading so it stays searchable inside the indexed text.
  const text = `${title}\n\n${rule.content || ''}`;

  const fd = new FormData();
  fd.append('file', new Blob([text], { type: 'text/plain; charset=utf-8' }), filename);

  const res = await fetchRetry(`${API}/documents/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.success === false) {
    throw new Error(`upload HTTP ${res.status}: ${json.message || JSON.stringify(json).slice(0, 160)}`);
  }
  return json.data ?? json;
}

async function deactivateRule(token, id) {
  const res = await fetch(`${API}/ai-rules/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ isActive: false }),
  });
  if (!res.ok) throw new Error(`deactivate HTTP ${res.status}`);
}

async function deleteRule(token, id) {
  const res = await fetch(`${API}/ai-rules/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`delete HTTP ${res.status}`);
}

function fmt(n) {
  return n.toLocaleString('en-US');
}

async function main() {
  console.log(`\n=== Rules → RAG migration ===`);
  console.log(`Backend:   ${API}`);
  console.log(`Manager:   ${PHONE}`);
  console.log(`Min chars: ${MIN_CHARS} (rules >= this are treated as documents)`);
  console.log(`Mode:      ${EXECUTE ? (HARD_DELETE ? 'EXECUTE (delete rules)' : 'EXECUTE (deactivate rules)') : 'DRY RUN (no changes)'}\n`);

  const token = await login();
  const rules = await listRules(token);
  const docs = await listDocuments(token);

  // Already in RAG = a COMPLETED document exists under the rule's .txt filename.
  const completedDocs = new Set(docs.filter((d) => d.status === 'COMPLETED').map((d) => d.title));

  // Idempotent: pick large rules (regardless of isActive) that are NOT already in RAG.
  const big = rules.filter((r) => (r.content || '').length >= MIN_CHARS);
  const toMigrate = big.filter((r) => !completedDocs.has(docFilename(r.title)));
  const alreadyDone = big.length - toMigrate.length;
  const keep = rules.filter((r) => (r.content || '').length < MIN_CHARS);

  const totalChars = toMigrate.reduce((s, r) => s + (r.content || '').length, 0);

  console.log(`Total rules:          ${rules.length}`);
  console.log(`Large rules (≥${MIN_CHARS}):   ${big.length}  (already in RAG: ${alreadyDone})`);
  console.log(`→ migrate now:        ${toMigrate.length}  (${fmt(totalChars)} chars ≈ ${fmt(Math.round(totalChars / 4))} tokens)`);
  console.log(`→ keep as rules:      ${keep.length}\n`);

  if (!EXECUTE) {
    console.log('Will migrate (first 10 shown):');
    for (const r of toMigrate.slice(0, 10)) console.log(`  • [${fmt((r.content || '').length)} ch] ${r.title}`);
    if (toMigrate.length > 10) console.log(`  … and ${toMigrate.length - 10} more`);
    console.log(`\nDRY RUN — nothing changed. Re-run with --execute to perform the migration.\n`);
    return;
  }

  let ok = 0;
  const failures = [];
  for (let i = 0; i < toMigrate.length; i++) {
    const r = toMigrate[i];
    const tag = `[${i + 1}/${toMigrate.length}]`;
    try {
      const doc = await uploadAsDocument(token, r);
      if (HARD_DELETE) await deleteRule(token, r.id);
      else await deactivateRule(token, r.id);
      ok++;
      console.log(`${tag} ✓ ${r.title}  → doc ${doc.id || '(created)'}`);
    } catch (e) {
      failures.push({ title: r.title, error: e.message });
      console.log(`${tag} ✗ ${r.title}  — ${e.message}`);
    }
    await sleep(500); // be gentle: backend caller-runs pool stalls under concurrent load
  }

  console.log(`\n=== Done: ${ok}/${toMigrate.length} migrated ===`);
  if (failures.length) {
    console.log(`Failures (${failures.length}) — rules left active, safe to re-run:`);
    for (const f of failures) console.log(`  ✗ ${f.title}: ${f.error}`);
  }
  console.log(`\nNOTE: documents process in the background (chunk → embed → Pinecone).`);
  console.log(`Give it a few minutes, then RAG search will use them. Chat works immediately`);
  console.log(`now that the giant rules are ${HARD_DELETE ? 'deleted' : 'deactivated'}.\n`);
}

main().catch((e) => {
  console.error('\nFATAL:', e.message, '\n');
  process.exit(1);
});
