# Cartographie du dépôt Arpent.io

> Miroir human-readable de [`project-structure.json`](project-structure.json). Les deux DOIVENT
> rester synchronisés : toute modification de structure (ajout/déplacement/suppression d'un
> fichier important) se fait dans le MÊME commit dans les deux fichiers. Pour l'architecture en
> prose (pourquoi, pas juste où), voir [README.md](README.md).
>
> `project-structure.json` est volontairement à la racine du dépôt (pas dans `.claude/`) : `.claude/`,
> `.agents/` et `.mcp.json` sont gitignorés dans ce projet (config d'outillage personnelle, jamais
> commitée). Un serveur MCP local (`tools/project-map-mcp/`) expose cette cartographie sous forme
> d'outils interrogeables par un agent — voir [AGENTS.md](AGENTS.md). Le même JSON contient aussi
> une clé `_deployment` (runbook de build/publication/synchronisation, outil MCP
> `get_deployment_runbook`) — jamais de secret dedans, uniquement des mécanismes et des chemins.

```
Arpent.io/
├── app/                                    Application Android (Kotlin, Compose)
│   └── src/main/java/com/fanta/androidsport/
│       ├── MainActivity.kt                 Unique Activity — pose le Compose racine (ArpentApp)
│       ├── SupabaseClient.kt               Client Supabase unique (Postgrest/Auth/Storage) partagé par toute l'app
│       ├── LocationTrackingService.kt      Service foreground — cœur du tracking GPS écran éteint
│       ├── LocationTrackerState.kt         État du tracking en cours (StateFlow), partagé Service <-> Composables
│       ├── MapPreloader.kt                 Préchargement du style/tuiles Mapbox
│       ├── NotificationScheduler.kt        Programmation des notifications locales
│       ├── NotificationReceiver.kt         BroadcastReceiver des alarmes programmées par NotificationScheduler
│       ├── PendingRunsQueue.kt             File d'attente courses hors-ligne — orchestre la sync réseau
│       ├── PendingRunDao.kt                DAO Room de la file hors-ligne
│       ├── PendingRunEntity.kt             Entité Room + Converters (sérialisation points GPS)
│       ├── AppDatabase.kt                  Base Room locale (file hors-ligne)
│       │
│       ├── ui/screens/                     Écrans Compose (un fichier = un ou plusieurs écrans liés)
│       │   ├── ArpentApp.kt                Racine de navigation — bascule Auth <-> Main selon session Supabase
│       │   ├── ArpentMainScreen.kt         Scaffold post-auth (bottom nav Carte/Courses/Guilde/Classement/Profil) + notifs in-app
│       │   ├── AuthScreen.kt               Connexion / inscription e-mail (Supabase Auth)
│       │   ├── ConquestMapScreen.kt        Carte de conquête (Mapbox) — le rendu, pas le calcul géométrique (~1550 lignes)
│       │   ├── CoursesScreen.kt            Feed de courses, aperçu de tracé — calculateDistanceMeters délègue à
│       │   │                               utils/GeoUtils.calculateDistance (~1900 lignes, le + gros fichier)
│       │   ├── GuildeScreen.kt             Guildes/clans — section Social masquée "en construction" (voir commits c6d121c/6eef6dd)
│       │   ├── LeaderboardScreen.kt        Classement joueurs/guildes — seul écran avec un ViewModel dédié
│       │   ├── ProfileScreen.kt            Profil joueur + PlayerProfileDialog (~1700 lignes)
│       │   ├── LoadingScreen.kt            Écran de chargement/splash
│       │   └── PermissionRequestScreen.kt  Demande des permissions runtime (localisation, etc.)
│       │
│       ├── ui/viewmodel/
│       │   └── LeaderboardViewModel.kt     Seul ViewModel du projet — les autres écrans gèrent leur état en Composable
│       │                                   (remember/LaunchedEffect) sans couche ViewModel : garder ce style par défaut
│       │
│       ├── ui/components/
│       │   ├── ColorWheel.kt               Sélecteur de couleur d'empire/territoire
│       │   ├── TerritoryMapBackground.kt   Fond de carte stylisé (prévisualisation territoire)
│       │   ├── AvatarImage.kt              Chargement avatar (Coil) avec fallback par défaut
│       │   └── CustomIcons.kt              Icônes générées (style Google) — NE PAS confondre avec ui/icons/CustomIcons.kt
│       │
│       ├── ui/icons/
│       │   └── CustomIcons.kt              Second fichier d'icônes générées (~780 lignes), package différent du précédent
│       │
│       ├── ui/theme/                       Theme.kt / Color.kt / Type.kt — thème Material 3 standard
│       │
│       ├── data/model/
│       │   ├── CourseModels.kt             DTOs courses/réactions/commentaires (kotlinx.serialization)
│       │   ├── GuildModels.kt              DTOs amis/guildes/invitations
│       │   └── LeaderboardModels.kt        DTOs classement (GuildRank, LeaderboardPlayer)
│       │
│       └── utils/
│           ├── GeoUtils.kt                 Géométrie GPS pure — distance Haversine, aire de polygone, splitIntoClosedPolygons
│           │                               (détecte la fermeture de boucle -> territoire, cœur du jeu), et
│           │                               groupRingsIntoPolygons (regroupe anneaux extérieurs/trous d'un territoire
│           │                               multi-anneaux avant rendu — voir README §Points chauds)
│           ├── StorageUtils.kt             Persistance locale territoires + saveRunToDatabase (écriture Supabase post-course)
│           ├── NetworkUtils.kt             Vérification connectivité (déclenche la sync de PendingRunsQueue)
│           └── ImageUtils.kt               Conversion image <-> base64 (upload avatar/bannière)
│
├── web-admin/                               Console d'administration Next.js (App Router, déployée sur Vercel)
│   └── src/
│       ├── app/
│       │   ├── layout.tsx                  Layout racine — englobe AuthWrapper + Sidebar
│       │   ├── page.tsx                    Dashboard / accueil admin
│       │   ├── clans/page.tsx              Modération guildes (~840 lignes)
│       │   ├── feed/page.tsx               Modération feed de courses (~630 lignes)
│       │   ├── profiles/page.tsx           Modération profils (~1600 lignes, le + gros fichier web-admin)
│       │   ├── stats/page.tsx              Statistiques globales (Recharts)
│       │   └── api/admin/
│       │       ├── courses/route.ts        PUT/DELETE courses — utilise supabaseAdmin (clé service)
│       │       ├── guilds/route.ts         PUT/DELETE guildes — utilise supabaseAdmin (clé service)
│       │       └── profiles/route.ts       PUT/DELETE profils — utilise supabaseAdmin (clé service)
│       ├── components/
│       │   ├── AuthWrapper.tsx             Garde d'authentification de la console admin
│       │   └── Sidebar.tsx                 Navigation latérale
│       └── lib/
│           ├── supabaseAdmin.ts            Client Supabase clé service — SERVER-ONLY, jamais côté client
│           └── supabase.ts                 Client Supabase clé publique — côté client
│
├── MapBoxStyle/
│   ├── style.json                          Style de carte Mapbox personnalisé, partagé app + web-admin
│   └── sprite_images/                      Sprites SVG du style
│
├── gradle/libs.versions.toml                Catalogue de versions Gradle — toute montée de version passe par ici
├── supabase_schema.sql                      Schéma complet (tables, RLS, triggers). Voir note ci-dessous sur le trigger
│                                             de recalcul de territoire (~ligne 605) : historique de bug corrigé, piège connu.
├── project-structure.json                   Cartographie machine-readable (source du serveur MCP)
├── structure.md                             Ce fichier
├── README.md                                Architecture technique en détail, stack, installation
├── LICENSE                                  PolyForm Noncommercial License 1.0.0 — usage/clonage libres, commercialisation exclue, attribution obligatoire
├── AGENTS.md                                Onboarding agent IA — règle d'or MCP-first
└── tools/project-map-mcp/                   Serveur MCP local exposant la cartographie (find_file, list_topics, ...)
```

## Sujets transversaux (topics)

Regroupements thématiques équivalents à ceux exposés par l'outil MCP `get_topic_files` /
`project-structure.json#topics` :

| Sujet | Fichiers clés |
|---|---|
| Authentification | `SupabaseClient.kt`, `AuthScreen.kt`, `ArpentApp.kt`, `web-admin/AuthWrapper.tsx`, `web-admin/supabaseAdmin.ts` |
| Territoire et conquête | `GeoUtils.kt`, `ConquestMapScreen.kt`, `TerritoryMapBackground.kt`, `StorageUtils.kt`, `supabase_schema.sql` |
| Guilde et clan | `GuildeScreen.kt`, `GuildModels.kt`, `web-admin/clans/page.tsx`, `web-admin/api/admin/guilds/route.ts` |
| Classement | `LeaderboardScreen.kt`, `LeaderboardViewModel.kt`, `LeaderboardModels.kt` |
| Tracking GPS et courses | `LocationTrackingService.kt`, `LocationTrackerState.kt`, `GeoUtils.kt`, `CoursesScreen.kt`, `ConquestMapScreen.kt` |
| File hors-ligne | `PendingRunsQueue.kt`, `PendingRunDao.kt`, `PendingRunEntity.kt`, `AppDatabase.kt`, `NetworkUtils.kt` |
| Profil et social | `ProfileScreen.kt`, `AvatarImage.kt`, `ColorWheel.kt`, `web-admin/profiles/page.tsx` |
| Administration et modération | `web-admin/api/admin/*`, `web-admin/clans`, `web-admin/feed`, `web-admin/profiles`, `web-admin/stats` |
| Base de données | `supabase_schema.sql` |

## Pièges connus (voir aussi README §Points chauds)

- **Rendu des territoires = anneaux extérieurs + trous, jamais un `PolygonAnnotation` par
  anneau brut.** Un territoire multi-anneaux (`territoires.points` aplati, voir
  `splitIntoClosedPolygons`) peut contenir un anneau intérieur (trou) issu d'un
  `ST_Difference` — un adversaire qui referme une boucle entièrement à l'intérieur d'un
  territoire ennemi perce un trou, pas une deuxième forme disjointe. Toujours passer par
  `GeoUtils.groupRingsIntoPolygons` (utilisé dans `ConquestMapScreen.kt` et
  `TerritoryMapBackground.kt`) avant d'appeler `PolygonAnnotation`, sinon le trou se
  dessine comme une « base » fantôme solide de la même couleur — c'était le bug derrière
  les rapports de « carte avec plusieurs bases / problèmes de soustraction ».
- **`CoursesScreen.kt#calculateDistanceMeters` délègue désormais à
  `utils/GeoUtils.calculateDistance`** (ancien piège : deux implémentations Haversine
  indépendantes qui pouvaient diverger) — ne pas réintroduire une copie locale.
- **Deux fichiers `CustomIcons.kt`** dans des packages différents (`ui/components` et `ui/icons`),
  générés, sans rapport de contenu — ne pas supposer qu'il y a doublon ou fusionner par erreur.
- **Section Social de `GuildeScreen.kt` masquée** derrière un placeholder « en construction » —
  vérifier l'état de ce flag avant de considérer les fonctionnalités sociales comme actives.
- **Trigger SQL de recalcul de territoire** (`supabase_schema.sql`, section
  `delete_course_territory_portion`) : ne pas revenir à un simple `ST_Difference` lors de
  la suppression d'une course, un bug déjà corrigé une fois pour cette raison précise
  (voir le commentaire en base).
- **`territoires.utilisateur_id` est maintenant `UNIQUE`** (contrainte
  `territoires_utilisateur_id_unique`) : le code (`enregistrer_course`,
  `delete_course_territory_portion`) a toujours supposé au plus une ligne par joueur sans
  que la base ne l'impose ; ne pas retirer cette contrainte sans revoir ce code.
- **`profiles.distance_totale` est une colonne cache** tenue à jour par
  `update_profile_stats_on_course`, consommée par les vues `leaderboard`/
  `clan_leaderboard` — ne pas revenir à un `SUM(courses.distance_totale)` en sous-requête
  corrélée dans ces vues (coût N+1 sur un classement chargé en entier à chaque ouverture
  de l'app, voir `LeaderboardViewModel`).
