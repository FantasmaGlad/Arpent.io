# Consignes pour l'agent IA — Arpent.io

## 1. Architecture (vue d'ensemble)

Arpent.io est composé de deux applications indépendantes qui partagent un seul backend Supabase :

- **`app/`** — application Android (Kotlin, Jetpack Compose). Parle directement à Supabase
  (Postgrest/Auth/Storage) via le SDK Kotlin officiel, aucun backend applicatif intermédiaire.
  Toute l'autorisation est portée par les policies **Row Level Security** de `supabase_schema.sql`.
- **`web-admin/`** — console d'administration Next.js (App Router), déployée sur Vercel. Utilise la
  clé `service_role` Supabase, uniquement côté serveur (routes `src/app/api/admin/*`).

Documentation détaillée : [README.md](README.md) (architecture, stack, pièges connus). Cartographie
fichier par fichier : [structure.md](structure.md) / [project-structure.json](project-structure.json).

## 2. RÈGLE D'OR : utiliser le serveur MCP avant d'explorer à l'aveugle

> Avant un `grep` large ou une exploration au hasard dans `app/` ou `web-admin/`, utilise le
> serveur MCP local `arpent-project-map` (enregistré dans `.mcp.json`) :
> `find_file(query)`, `list_topics()`, `get_topic_files(topic)`, `list_workspaces()`,
> `get_full_map()`.
>
> Si l'outil MCP n'est pas disponible dans la session, lis directement
> [structure.md](structure.md) ou [project-structure.json](project-structure.json).

## 3. Build, publication et synchronisation — `get_deployment_runbook`

Avant tout build de release, toute publication, ou toute synchronisation de schéma/env vars,
appelle l'outil MCP `get_deployment_runbook()` (ou lis la clé `_deployment` de
[project-structure.json](project-structure.json)). Résumé :

- **Android** — pas encore publié sur le Play Store (distribution APK directe). Le keystore de
  release et ses credentials sont lus depuis `.env` à la racine (gitignoré, jamais commité) par
  `app/build.gradle.kts` ; ne jamais imprimer/logger leur valeur. **`versionCode` doit être
  incrémenté à chaque build de release** (`app/build.gradle.kts`, bloc `defaultConfig`), sans quoi
  Android refuse l'installation de la mise à jour. Build : `./gradlew bundleRelease` /
  `assembleRelease`.
- **web-admin (Vercel)** — publication automatique à chaque `push` sur `main` (intégration GitHub
  Vercel). L'agent peut pousser sur `main` de façon autonome pour déclencher un déploiement de
  production, **mais seulement après avoir fait passer `npm run build` en local** — il n'y a pas de
  validation manuelle intermédiaire, un push cassé casse la prod immédiatement.
- **Synchronisation** — le schéma (`supabase_schema.sql`) est idempotent (`IF NOT EXISTS` partout)
  mais se rejoue actuellement à la main dans l'éditeur SQL du dashboard Supabase : la CLI Supabase
  installée sur ce poste n'est pas liée au projet de prod. Les variables d'environnement Vercel ne
  sont pas non plus synchronisées via CLI locale (`web-admin/.vercel/` absent) — elles vivent dans
  le dashboard Vercel. Ne jamais générer de `DROP TABLE`/`TRUNCATE`/suppression de colonne sans
  confirmation explicite de l'utilisateur.

## 4. Maintenance de la cartographie

Toute modification de structure (ajout/déplacement/suppression d'un fichier significatif) impose
une mise à jour **dans le même commit** de `structure.md` **ET** `project-structure.json`. Un
fichier qui existe dans l'un sans l'autre rend les deux trompeurs.

## 5. Où vit la configuration d'outillage — ne pas la déplacer par réflexe

`.claude/`, `.agents/` et `.mcp.json` sont **volontairement gitignorés** dans ce dépôt (config
d'outillage/permissions personnelle, jamais commitée). C'est pourquoi `project-structure.json`,
`structure.md`, `AGENTS.md` et le serveur MCP (`tools/project-map-mcp/`) vivent tous à la racine ou
dans `tools/`, en dehors de `.claude/` — pour rester versionnés avec le code. Ne pas les déplacer
dans `.claude/` en pensant "ranger", cela les rendrait invisibles à `git` et donc à toute session
future ou tout reclonage du dépôt.

## 6. Principes de développement propres au projet

- **Pas de couche ViewModel systématique.** Seul `LeaderboardViewModel` existe ; tous les autres
  écrans gèrent leur état directement dans le Composable (`remember`/`LaunchedEffect`). Suivre ce
  style par défaut pour un nouvel écran, sauf décision explicite de migration.
- **Géométrie GPS de référence : `utils/GeoUtils.kt`.** `CoursesScreen.kt` a sa propre implémentation
  de distance Haversine ; si un bug de calcul de distance/aire est corrigé dans un des deux
  fichiers, vérifier l'autre.
- **`web-admin/src/lib/supabaseAdmin.ts` (clé `service_role`) ne doit jamais être importé côté
  client** — uniquement depuis les routes API `src/app/api/admin/*`.
- **Aucune suite de tests automatisés n'existe actuellement** (voir README §Tests). Ne pas
  supposer qu'une commande `test` existe ; valider par build + usage réel.
- Pour tout ce qui touche au calcul de territoire (fermeture de boucle, superficie, trigger SQL de
  recalcul à la suppression d'une course), voir README §Points chauds et pièges connus avant de
  modifier — un bug de ce type a déjà été corrigé une fois pour une raison non évidente.
