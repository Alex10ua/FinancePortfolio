#!/usr/bin/env node
/**
 * Register the MCP servers declared in mcp.json with Claude Code and/or Codex.
 *
 * One source of truth (mcp.json) fanned out to two clients that store their
 * config differently: Claude Code keeps JSON, Codex keeps TOML. This edits the
 * config files directly rather than shelling out to `claude mcp` / `codex mcp`,
 * because those CLIs ship inside the editor extensions and are frequently not
 * on PATH.
 *
 *   node register-mcp.mjs [options]
 *
 *   --file <path>    Source config          (default: mcp.json, then .mcp.json)
 *   --target <t>     claude | codex | both  (default: both)
 *   --scope <s>      Claude scope: user | project | local   (default: user)
 *   --name <server>  Register only this server (repeatable)
 *   --resolve-env    Expand ${VAR} in env values for Claude too
 *                    (always done for Codex, which cannot expand them itself)
 *   --force          Overwrite a server that is already registered
 *   --dry-run        Print what would change, write nothing
 *   --list           Show what is registered today, then exit
 *   -h, --help
 *
 * Env values may use ${VAR} or ${VAR:-default}. Keep real credentials in your
 * environment, not in mcp.json.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const HOME = os.homedir();
const CLAUDE_USER_CONFIG = path.join(HOME, '.claude.json');
const CODEX_CONFIG = path.join(HOME, '.codex', 'config.toml');

const die = (msg) => { console.error(`error: ${msg}`); process.exit(1); };
const info = (msg) => console.log(msg);

// ---------------------------------------------------------------- arguments

function parseArgs(argv) {
  const opts = {
    target: 'both', scope: 'user', names: [], file: null,
    force: false, dryRun: false, list: false, resolveEnv: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--file') opts.file = argv[++i];
    else if (a === '--target') opts.target = String(argv[++i]).toLowerCase();
    else if (a === '--scope') opts.scope = String(argv[++i]).toLowerCase();
    else if (a === '--name') opts.names.push(argv[++i]);
    else if (a === '--force') opts.force = true;
    else if (a === '--dry-run') opts.dryRun = true;
    else if (a === '--list') opts.list = true;
    else if (a === '--resolve-env') opts.resolveEnv = true;
    else if (a === '-h' || a === '--help') { usage(); process.exit(0); }
    else die(`Unknown option: ${a}  (try --help)`);
  }
  if (!['claude', 'codex', 'both'].includes(opts.target)) die('--target must be claude, codex or both');
  if (!['user', 'project', 'local'].includes(opts.scope)) die('--scope must be user, project or local');
  return opts;
}

function usage() {
  const src = fs.readFileSync(new URL(import.meta.url), 'utf8');
  const block = src.slice(src.indexOf('/**') + 3, src.indexOf('*/'));
  info(block.split('\n').map((l) => l.replace(/^\s*\*ce? ?/, '').replace(/^\s*\* ?/, '')).join('\n').trim());
}

// ---------------------------------------------------------------- helpers

/** Expand ${VAR} and ${VAR:-default} from process.env. */
function expand(value) {
  if (typeof value !== 'string') return value;
  return value.replace(/\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\}/g, (_m, name, fallback) => {
    const v = process.env[name];
    if (v !== undefined && v !== '') return v;
    return fallback !== undefined ? fallback : '';
  });
}

function expandEnvBlock(env) {
  const out = {};
  for (const [k, v] of Object.entries(env || {})) out[k] = expand(v);
  return out;
}

/** Catch placeholders and unresolved variables before they reach a client. */
function auditEnv(name, env, { resolved }) {
  for (const [k, v] of Object.entries(env || {})) {
    if (resolved && v === '') {
      console.warn(`  ! ${name}.env.${k} resolved to an empty string — set ${k} in your environment first`);
    } else if (!resolved && /\$\{/.test(v)) {
      // Fine: Claude Code expands these itself when it spawns the server.
    } else if (/^(test|changeme|placeholder|your-\w+)$/i.test(v)) {
      console.warn(`  ! ${name}.env.${k} is the placeholder "${v}" — the server will fail to authenticate`);
    }
  }
}

function backup(file) {
  if (!fs.existsSync(file)) return null;
  const dest = `${file}.bak-${new Date().toISOString().replace(/[:.]/g, '-')}`;
  fs.copyFileSync(file, dest);
  return dest;
}

function readJson(file, fallback) {
  if (!fs.existsSync(file)) return fallback;
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    return die(`${file} is not valid JSON: ${e.message}`);
  }
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

// ---------------------------------------------------------------- source

function loadSource(explicit) {
  const candidates = explicit ? [explicit] : ['mcp.json', '.mcp.json'];
  const found = candidates.find((f) => fs.existsSync(f));
  if (!found) die(`no config found (looked for ${candidates.join(', ')})`);
  const doc = readJson(found, null);
  const servers = doc.mcpServers || doc.servers;
  if (!servers || typeof servers !== 'object') die(`${found} has no "mcpServers" object`);
  return { file: found, servers };
}

// ---------------------------------------------------------------- claude

/**
 * Claude Code keys projects by absolute path, and on Windows the casing it
 * stored may not match what process.cwd() reports. Reuse an existing key when
 * one matches case-insensitively, or a second entry appears for the same
 * directory and the registration silently has no effect.
 */
function claudeProjectKey(config) {
  const cwd = process.cwd().replace(/\\/g, '/');
  const existing = Object.keys(config.projects || {});
  return existing.find((k) => k.toLowerCase() === cwd.toLowerCase()) || cwd;
}

function registerClaude(name, definition, opts) {
  const payload = { ...definition };
  if (payload.env) {
    payload.env = opts.resolveEnv ? expandEnvBlock(payload.env) : { ...payload.env };
    auditEnv(name, payload.env, { resolved: opts.resolveEnv });
  }

  if (opts.scope === 'project') {
    const file = '.mcp.json';
    const doc = readJson(file, { mcpServers: {} });
    doc.mcpServers = doc.mcpServers || {};
    if (doc.mcpServers[name] && !opts.force) {
      info(`  claude: "${name}" already in ${file} — use --force to replace`);
      return;
    }
    if (opts.dryRun) { info(`  claude: would write "${name}" to ${file}`); return; }
    const b = backup(file);
    doc.mcpServers[name] = payload;
    writeJson(file, doc);
    info(`  claude: wrote "${name}" to ${file}${b ? ` (backup ${path.basename(b)})` : ''}`);
    return;
  }

  // user and local scopes both live in ~/.claude.json
  const config = readJson(CLAUDE_USER_CONFIG, {});
  let container;
  let where;
  if (opts.scope === 'user') {
    config.mcpServers = config.mcpServers || {};
    container = config.mcpServers;
    where = `${CLAUDE_USER_CONFIG} (user scope)`;
  } else {
    config.projects = config.projects || {};
    const key = claudeProjectKey(config);
    config.projects[key] = config.projects[key] || {};
    config.projects[key].mcpServers = config.projects[key].mcpServers || {};
    container = config.projects[key].mcpServers;
    where = `${CLAUDE_USER_CONFIG} -> projects["${key}"] (local scope)`;
  }

  if (container[name] && !opts.force) {
    info(`  claude: "${name}" already registered in ${opts.scope} scope — use --force to replace`);
    return;
  }
  if (opts.dryRun) { info(`  claude: would write "${name}" to ${where}`); return; }
  const b = backup(CLAUDE_USER_CONFIG);
  container[name] = payload;
  writeJson(CLAUDE_USER_CONFIG, config);
  info(`  claude: wrote "${name}" to ${where}${b ? ` (backup ${path.basename(b)})` : ''}`);
}

// ---------------------------------------------------------------- codex

const tomlString = (s) => `"${String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;

function codexSection(name, definition) {
  const lines = [`[mcp_servers.${name}]`];
  lines.push(`command = ${tomlString(definition.command)}`);
  if (definition.args?.length) {
    lines.push(`args = [${definition.args.map(tomlString).join(', ')}]`);
  }
  // Codex does not expand ${VAR}, so values are resolved at registration time.
  const env = expandEnvBlock(definition.env);
  if (Object.keys(env).length) {
    lines.push(`[mcp_servers.${name}.env]`);
    for (const [k, v] of Object.entries(env)) lines.push(`${k} = ${tomlString(v)}`);
  }
  return `${lines.join('\n')}\n`;
}

/** Replace an existing [mcp_servers.<name>] block, or append a new one. */
function upsertTomlSection(text, name, block) {
  const header = new RegExp(`^\\[mcp_servers\\.${name}\\]\\s*$`, 'm');
  const m = header.exec(text);
  if (!m) {
    const sep = text.length === 0 ? '' : (text.endsWith('\n\n') ? '' : (text.endsWith('\n') ? '\n' : '\n\n'));
    return { text: text + sep + block, replaced: false };
  }
  // The section runs to the next top-level table that is not one of its own
  // sub-tables (e.g. [mcp_servers.<name>.env]).
  const from = m.index + m[0].length;
  const after = text.slice(from);
  const next = new RegExp(`^\\[(?!mcp_servers\\.${name}[.\\]])`, 'm').exec(after);
  const end = next ? from + next.index : text.length;
  return { text: text.slice(0, m.index) + block + (next ? '\n' : '') + text.slice(end), replaced: true };
}

function registerCodex(name, definition, opts) {
  auditEnv(name, expandEnvBlock(definition.env), { resolved: true });

  const existing = fs.existsSync(CODEX_CONFIG) ? fs.readFileSync(CODEX_CONFIG, 'utf8') : '';
  const already = new RegExp(`^\\[mcp_servers\\.${name}\\]\\s*$`, 'm').test(existing);
  if (already && !opts.force) {
    info(`  codex:  "${name}" already in ${CODEX_CONFIG} — use --force to replace`);
    return;
  }
  if (opts.dryRun) {
    info(`  codex:  would ${already ? 'replace' : 'add'} "${name}" in ${CODEX_CONFIG}`);
    return;
  }
  const b = backup(CODEX_CONFIG);
  const { text } = upsertTomlSection(existing, name, codexSection(name, definition));
  fs.mkdirSync(path.dirname(CODEX_CONFIG), { recursive: true });
  fs.writeFileSync(CODEX_CONFIG, text, 'utf8');
  info(`  codex:  ${already ? 'replaced' : 'added'} "${name}" in ${CODEX_CONFIG}${b ? ` (backup ${path.basename(b)})` : ''}`);
}

// ---------------------------------------------------------------- list

function listRegistrations() {
  const claude = readJson(CLAUDE_USER_CONFIG, {});
  const show = (names) => (names.length ? names.join(', ') : '(none)');

  info(`claude user scope   ${CLAUDE_USER_CONFIG}`);
  info(`                    ${show(Object.keys(claude.mcpServers || {}))}`);

  const key = claudeProjectKey(claude);
  info(`claude local scope  projects["${key}"]`);
  info(`                    ${show(Object.keys(claude.projects?.[key]?.mcpServers || {}))}`);

  info('claude project file .mcp.json');
  info(`                    ${fs.existsSync('.mcp.json')
    ? show(Object.keys(readJson('.mcp.json', {}).mcpServers || {}))
    : '(file absent)'}`);

  info(`codex               ${CODEX_CONFIG}`);
  if (fs.existsSync(CODEX_CONFIG)) {
    const names = [...fs.readFileSync(CODEX_CONFIG, 'utf8')
      .matchAll(/^\[mcp_servers\.([^.\]]+)\]/gm)].map((m) => m[1]);
    info(`                    ${show([...new Set(names)])}`);
  } else {
    info('                    (file absent)');
  }
}

// ---------------------------------------------------------------- main

const opts = parseArgs(process.argv.slice(2));

if (opts.list) {
  listRegistrations();
  process.exit(0);
}

const { file, servers } = loadSource(opts.file);
const selected = opts.names.length
  ? Object.fromEntries(Object.entries(servers).filter(([n]) => opts.names.includes(n)))
  : servers;

if (!Object.keys(selected).length) {
  die(`no matching servers in ${file} (available: ${Object.keys(servers).join(', ') || 'none'})`);
}

info(`source:  ${file}`);
info(`servers: ${Object.keys(selected).join(', ')}`);
info(opts.dryRun ? '(dry run — nothing will be written)\n' : '');

const doClaude = opts.target === 'claude' || opts.target === 'both';
const doCodex = opts.target === 'codex' || opts.target === 'both';

for (const [name, definition] of Object.entries(selected)) {
  const isHttp = Boolean(definition.url);
  if (!definition.command && !isHttp) {
    console.warn(`skipping "${name}": needs either "command" (stdio) or "url" (HTTP)`);
    continue;
  }
  info(`${name}:${isHttp ? '  (HTTP transport)' : ''}`);
  if (doClaude) registerClaude(name, definition, opts);
  if (doCodex) {
    if (isHttp) {
      // Codex's TOML config models a spawned process; an HTTP endpoint has no
      // command to put there.
      info(`  codex:  skipped "${name}" — Codex config takes stdio servers, not a url`);
    } else {
      registerCodex(name, definition, opts);
    }
  }
}

if (!opts.dryRun) {
  info('');
  info('Done. Restart the client to pick the server up:');
  if (doClaude) info('  Claude Code — restart, then /mcp to confirm it connects');
  if (doCodex) info('  Codex       — restart, then `codex mcp list`');
}
