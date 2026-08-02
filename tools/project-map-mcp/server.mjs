#!/usr/bin/env node
// Serveur MCP local qui expose la cartographie du dépôt (project-structure.json) — évite de
// refouiller tout le repo à chaque session pour retrouver "où est le fichier qui gère X".
// Relit les fichiers à chaque appel (pas de cache) : jamais périmé, même si la cartographie
// vient d'être modifiée dans la même session.

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, '..', '..');

function loadStructure() {
  const raw = readFileSync(join(REPO_ROOT, 'project-structure.json'), 'utf-8');
  return JSON.parse(raw);
}

function readRepoFile(relativePath) {
  return readFileSync(join(REPO_ROOT, relativePath), 'utf-8');
}

function tagMatches(tag, token) {
  // Bidirectionnel : couvre un tag abrégé ("auth") interrogé via un mot complet
  // ("authentification") et l'inverse (un tag long retrouvé via une requête abrégée).
  if (tag.length < 3 || token.length < 3) return tag === token;
  return tag.includes(token) || token.includes(tag);
}

function scoreEntry(entry, queryTokens) {
  const haystackPath = entry.path.toLowerCase();
  const haystackDesc = (entry.description || '').toLowerCase();
  const haystackTags = (entry.tags || []).map((t) => t.toLowerCase());
  let score = 0;
  for (const token of queryTokens) {
    if (haystackPath.includes(token)) score += 3;
    if (haystackTags.some((tag) => tagMatches(tag, token))) score += 4;
    if (haystackDesc.includes(token)) score += 1;
  }
  return score;
}

const server = new McpServer({ name: 'arpent-project-map', version: '1.0.0' });

server.registerTool(
  'find_file',
  {
    title: 'Trouver un fichier par mot-clé',
    description: "Cherche dans la cartographie du dépôt Arpent.io les fichiers/dossiers pertinents pour un sujet donné. À utiliser AVANT de grep/explorer le repo à l'aveugle.",
    inputSchema: {
      query: z.string().describe('Mots-clés libres (ex: "territoire", "guilde", "auth")'),
      limit: z.number().int().min(1).max(50).optional(),
    },
  },
  async ({ query, limit }) => {
    const structure = loadStructure();
    const queryTokens = query.toLowerCase().split(/\s+/).filter(Boolean);
    const scored = structure.entries
      .map((entry) => ({ entry, score: scoreEntry(entry, queryTokens) }))
      .filter((s) => s.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, limit ?? 10);
    if (scored.length === 0) {
      return { content: [{ type: 'text', text: `Aucune correspondance pour "${query}". Essaie list_topics ou get_full_map.` }] };
    }
    const text = scored.map(({ entry }) => `${entry.path} [${entry.workspace}]\n  tags: ${(entry.tags || []).join(', ')}\n  ${entry.description}`).join('\n\n');
    return { content: [{ type: 'text', text }] };
  },
);

server.registerTool('list_workspaces', {
  title: 'Lister les workspaces',
  description: 'Liste les paquets du dépôt (app Android, web-admin Next.js) avec leur rôle.',
  inputSchema: {},
}, async () => {
  const structure = loadStructure();
  const text = structure.workspaces
    .map((w) => `${w.name} (${w.path}) — dépend de: ${w.dependsOn.length ? w.dependsOn.join(', ') : 'aucun'}\n  ${w.description}`)
    .join('\n\n');
  return { content: [{ type: 'text', text }] };
});

server.registerTool('list_topics', {
  title: 'Lister les sujets connus',
  description: 'Liste les catégories thématiques pré-indexées (ex: territoire-et-conquete, guilde-et-clan).',
  inputSchema: {},
}, async () => {
  const structure = loadStructure();
  return { content: [{ type: 'text', text: Object.keys(structure.topics).sort().join('\n') }] };
});

server.registerTool('get_topic_files', {
  title: "Fichiers d'un sujet",
  description: 'Retourne la liste des fichiers pertinents pour un sujet pré-indexé.',
  inputSchema: { topic: z.string() },
}, async ({ topic }) => {
  const structure = loadStructure();
  const topics = structure.topics;
  let key = Object.keys(topics).find((k) => k.toLowerCase() === topic.toLowerCase());
  if (!key) {
    const needle = topic.toLowerCase();
    key = Object.keys(topics).find((k) => k.toLowerCase().includes(needle) || needle.includes(k.toLowerCase()));
  }
  if (!key) {
    return { content: [{ type: 'text', text: `Sujet "${topic}" introuvable. Sujets connus:\n${Object.keys(topics).sort().join('\n')}` }] };
  }
  const entryByPath = new Map(structure.entries.map((e) => [e.path, e]));
  const text = topics[key].map((p) => { const e = entryByPath.get(p); return e ? `${e.path}\n  ${e.description}` : p; }).join('\n\n');
  return { content: [{ type: 'text', text: `Sujet: ${key}\n\n${text}` }] };
});

server.registerTool('get_deployment_runbook', {
  title: 'Runbook de build / publication / synchronisation',
  description: "Retourne le bloc _deployment de project-structure.json : comment builder et signer l'app Android (et où sont les credentials), comment publier web-admin sur Vercel, et comment synchroniser le schéma Supabase et les variables d'environnement. À consulter avant tout build de release ou toute publication.",
  inputSchema: {},
}, async () => {
  const structure = loadStructure();
  return { content: [{ type: 'text', text: JSON.stringify(structure._deployment, null, 2) }] };
});

server.registerTool('get_full_map', {
  title: 'Cartographie complète (JSON brut)',
  description: "Retourne le contenu intégral de project-structure.json — seulement si find_file/get_topic_files ne suffisent pas.",
  inputSchema: {},
}, async () => {
  return { content: [{ type: 'text', text: JSON.stringify(loadStructure(), null, 2) }] };
});

server.registerResource('project-structure', 'projectmap://project-structure.json', {
  title: 'Cartographie structurée du dépôt (JSON)',
  mimeType: 'application/json',
}, async (uri) => ({ contents: [{ uri: uri.href, mimeType: 'application/json', text: readRepoFile('project-structure.json') }] }));

server.registerResource('readme', 'projectmap://README.md', {
  title: 'README.md',
  mimeType: 'text/markdown',
}, async (uri) => ({ contents: [{ uri: uri.href, mimeType: 'text/markdown', text: readRepoFile('README.md') }] }));

server.registerResource('structure-md', 'projectmap://structure.md', {
  title: 'structure.md',
  mimeType: 'text/markdown',
}, async (uri) => ({ contents: [{ uri: uri.href, mimeType: 'text/markdown', text: readRepoFile('structure.md') }] }));

const transport = new StdioServerTransport();
await server.connect(transport);
