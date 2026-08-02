# Arpent.io

Application Android de course à pied à mécanique de conquête territoriale : chaque sortie GPS dessine une boucle qui, une fois fermée, revendique la zone parcourue comme territoire. Les territoires s'accumulent, se disputent entre joueurs et entre clans sur une carte partagée.

---

## Table des matières

- [Présentation](#présentation)
- [Fonctionnalités](#fonctionnalités)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Architecture technique](#architecture-technique)
- [Stack technique](#stack-technique)
- [Structure du dépôt](#structure-du-dépôt)
- [Base de données](#base-de-données)
- [Points chauds et pièges connus](#points-chauds-et-pièges-connus)
- [Tests](#tests)
- [Licence](#licence)

---

## Présentation

Arpent.io transforme la course à pied en jeu de territoire. L'application enregistre le tracé GPS d'une course ; si le tracé se referme sur lui-même, la surface délimitée devient un territoire attribué au coureur. Les territoires des joueurs et des clans (« guildes ») s'affichent sur une carte commune, créant une compétition géographique en continu : défendre son terrain, empiéter sur celui des autres, agrandir la zone contrôlée par son clan.

Le projet est composé de deux applications :

- **Application mobile Android** — l'expérience joueur (suivi de course, carte de conquête, classement, guildes, profil).
- **Console d'administration web** — outil interne de modération et de supervision (gestion des courses, des guildes, des profils, statistiques).

## Fonctionnalités

### Suivi de course et territoire
- Enregistrement GPS en temps réel via un service de localisation en premier plan (fonctionne écran éteint / application en arrière-plan)
- Détection de pas et données de capteurs (accéléromètre, podomètre) pour enrichir les statistiques de course
- Calcul automatique de la surface conquise à la fermeture d'une boucle GPS
- File d'attente locale (Room) pour les courses enregistrées hors connexion, synchronisées dès le retour du réseau
- Historique des courses avec distance, durée, dénivelé et surface territoriale gagnée

### Carte de conquête
- Carte interactive (Mapbox) affichant les territoires de tous les joueurs, différenciés par couleur
- Chargement des territoires par zone visible (bounding box) pour rester performant à grande échelle
- Recherche et navigation sur la carte

### Guildes (clans)
- Création et gestion de guildes, invitations, rôles (chef, adjoint, membre)
- Territoire cumulé et statistiques de guilde
- Historique d'appartenance à une guilde

### Classement
- Classement des joueurs et des guildes par surface conquise
- Podium et mise en avant des meilleurs profils

### Profil et social
- Profil personnalisé (avatar, bannière, statistiques : XP, surface totale, historique)
- Notifications in-app et push (créations de territoire, activité de guilde, etc.)
- Système d'amis (relations, demandes)

### Authentification
- Inscription / connexion par e-mail via Supabase Auth
- Sessions persistées et sécurisées côté client

## Prérequis

Pour compiler et exécuter le projet, vous aurez besoin de :

| Outil | Version |
|---|---|
| [Android Studio](https://developer.android.com/studio) | Ladybug ou plus récent |
| JDK | 17 |
| Android SDK | `compileSdk` 36, `minSdk` 35 |
| Un projet [Supabase](https://supabase.com) | (base de données, auth, storage) |
| Un compte [Mapbox](https://www.mapbox.com) | token public + token secret (téléchargement du SDK) |
| Node.js | 20+ (uniquement pour la console d'administration `web-admin/`) |

## Installation

### 1. Cloner le dépôt

```bash
git clone git@github.com:FantasmaGlad/Arpent.io.git
cd Arpent.io
```

### 2. Configurer les variables d'environnement (application Android)

Copier le fichier d'exemple puis renseigner vos propres valeurs (`.env` n'est jamais versionné) :

```bash
cp .env.example .env
```

```bash
# Supabase
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_PUBLISHABLE_KEY=votre_cle_publique_supabase

# Mapbox
MAPBOX_PUBLIC_TOKEN=pk.votre_token_public_mapbox
MAPBOX_SECRET_TOKEN=sk.votre_token_secret_mapbox   # requis pour télécharger le SDK Mapbox

# Signature de build release (facultatif en développement)
KEYSTORE_PATH=/chemin/vers/votre.jks
KEYSTORE_PASSWORD=
KEY_ALIAS=
KEY_PASSWORD=
```

`MAPBOX_SECRET_TOKEN` doit disposer du scope `Downloads:Read` — il sert uniquement à authentifier le dépôt Maven privé de Mapbox lors du build, jamais embarqué dans l'application.

### 3. Initialiser le schéma de base de données

Le schéma complet (tables, politiques RLS, fonctions, déclencheurs) est fourni dans [`supabase_schema.sql`](supabase_schema.sql). Importez-le dans un projet Supabase neuf via l'éditeur SQL du dashboard ou la CLI Supabase.

### 4. Compiler et lancer l'application

```bash
./gradlew assembleDebug
```

Ou ouvrez simplement le projet dans Android Studio et lancez-le sur un émulateur / appareil physique (Android 15+, une localisation GPS réelle est nécessaire pour tester le suivi de course).

### 5. (Optionnel) Lancer la console d'administration

```bash
cd web-admin
npm install
cp .env.example .env.local   # renseigner les variables Supabase (voir plus bas)
npm run dev
```

## Architecture technique

```
┌───────────────────────────┐       ┌───────────────────────────┐
│  Application Android       │       │  Console admin              │
│  (Kotlin, Compose)         │       │  (Next.js / React)          │
│                             │       │                              │
│  - Service GPS foreground  │       │  - Pages de modération       │
│  - Room (file hors-ligne)  │       │  - Routes API (clé service)  │
└──────────────┬──────────────┘       └──────────────┬───────────────┘
               │                                       │
               │              Supabase (BaaS)          │
               │  ┌─────────────────────────────────┐  │
               └─▶│ PostgreSQL + PostGIS             │◀─┘
                  │ Auth · Row Level Security         │
                  │ Storage (avatars, bannières)      │
                  │ Realtime                          │
                  └──────────────────┬─────────────────┘
                                     │
                                     ▼
                          ┌───────────────────┐
                          │ Mapbox (SDK / API) │
                          └───────────────────┘
```

- L'application Android communique directement avec Supabase (Postgrest, Auth, Storage) via le SDK Kotlin officiel — pas de backend applicatif intermédiaire.
- Toute la logique d'autorisation est portée par les politiques **Row Level Security** de PostgreSQL, définies dans `supabase_schema.sql`.
- La console d'administration utilise une clé de service Supabase côté serveur uniquement (routes API Next.js), jamais exposée au client.

## Stack technique

### Application Android (`app/`)

| Domaine | Technologie |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Backend as a Service | [Supabase](https://supabase.com) — `postgrest-kt`, `auth-kt`, `storage-kt` (SDK Kotlin officiel, v3.6.0) |
| Réseau | Ktor Client |
| Sérialisation | kotlinx.serialization |
| Cartographie | Mapbox Maps SDK for Android + extension Compose (v11.8.0) |
| Persistance locale | Room (file d'attente de courses hors ligne) |
| Chargement d'images | Coil |
| Géolocalisation | Google Play Services Location (Fused Location Provider) |
| Build | Gradle Kotlin DSL, AGP 9.2.1, KSP |

### Console d'administration (`web-admin/`)

| Domaine | Technologie |
|---|---|
| Framework | Next.js 16 (App Router) |
| Langage | TypeScript |
| UI | React 19 |
| Cartographie | Mapbox GL JS |
| Graphiques | Recharts |
| Client Supabase | `@supabase/supabase-js` |
| Déploiement | Vercel |

### Infrastructure

| Service | Rôle |
|---|---|
| Supabase Auth | Authentification e-mail / mot de passe |
| Supabase PostgreSQL + PostGIS | Stockage relationnel et géospatial (profils, courses, territoires, guildes...) |
| Supabase Row Level Security | Contrôle d'accès aux données au niveau ligne |
| Supabase Storage | Avatars, bannières, photos de course |
| Supabase Realtime | Notifications en temps réel |
| Mapbox | Rendu cartographique, style personnalisé (`MapBoxStyle/`) |

## Structure du dépôt

```
Arpent.io/
├── app/                        # Application Android
│   └── src/main/java/com/fanta/androidsport/
│       ├── ui/screens/         # Écrans Compose (carte, courses, guilde, classement, profil, auth)
│       ├── data/model/         # Modèles de données
│       ├── utils/              # Géométrie GPS, images, réseau, stockage
│       └── *.kt                # Service de localisation, base Room, client Supabase
├── web-admin/                  # Console d'administration Next.js
│   └── src/app/                # Pages et routes API
├── MapBoxStyle/                # Style de carte personnalisé (JSON + sprites)
├── supabase_schema.sql         # Schéma complet de la base (tables, RLS, fonctions)
└── build.gradle.kts            # Configuration Gradle racine
```

Cartographie fichier par fichier (rôle de chaque fichier notable, mots-clés de recherche, sujets
transversaux) : voir [`structure.md`](structure.md) et sa version machine-readable
[`project-structure.json`](project-structure.json), consultables aussi via le serveur MCP local
décrit dans [`AGENTS.md`](AGENTS.md).

## Base de données

Le fichier [`supabase_schema.sql`](supabase_schema.sql) contient l'intégralité du schéma : définitions de tables, contraintes, index, fonctions PL/pgSQL et politiques de sécurité au niveau ligne (RLS). Il ne contient aucune donnée réelle ni identifiant — c'est un schéma pur, exportable et rejouable sur un projet Supabase neuf.

Tables principales : `profiles`, `courses`, `territoires`, `points_gps`, `guildes`, `guilde_invitations`, `amis`, `notifications`, `course_reactions`, `course_commentaires`, `admins`.

## Points chauds et pièges connus

- **Rendu de territoires troués sur la carte (corrigé).** Un `territoires.points` peut
  encoder plusieurs anneaux à plat (voir `splitIntoClosedPolygons`) : soit des morceaux de
  territoire disjoints, soit — cas auparavant mal géré — un anneau **intérieur** (un trou)
  quand `ST_Difference` retire une zone entièrement enclavée au milieu d'un territoire
  existant. `ConquestMapScreen.kt` et `TerritoryMapBackground.kt` dessinaient chaque anneau
  comme son propre `PolygonAnnotation` plein, donc un trou apparaissait comme une « base »
  fantôme solide au lieu d'un vide — c'était la cause du bug « carte avec plusieurs bases /
  problèmes de soustraction ». Fixé via `GeoUtils.groupRingsIntoPolygons`, qui régroupe les
  anneaux en `[extérieur, trou...]` par signe d'aire (convention PostGIS : extérieur CCW,
  trou CW) avant rendu. Tout nouveau code qui dessine un territoire doit passer par cette
  fonction plutôt que d'itérer sur les anneaux bruts.
- **Désynchronisation carte après course hors-ligne (corrigé).** Une course enregistrée
  hors connexion ajoutait sa boucle brute (non fusionnée) à `completedPolygons` en local, et
  `PendingRunsQueue.syncPendingRuns` (au retour réseau) poussait la course vers
  `enregistrer_course` côté serveur sans jamais rappeler `syncTerritoriesFromDatabase` — la
  carte affichait donc indéfiniment (jusqu'au redémarrage de l'app) les boucles non
  fusionnées au lieu du territoire réellement fusionné en base. `ArpentMainScreen.kt`
  rappelle maintenant `syncTerritoriesFromDatabase` juste après chaque `syncPendingRuns`.
- **Calcul de distance dupliqué (corrigé).** `CoursesScreen.kt#calculateDistanceMeters`
  délègue maintenant à `GeoUtils.calculateDistance` au lieu de garder une seconde
  implémentation Haversine indépendante qui pouvait diverger silencieusement.
- **Recalcul de territoire à la suppression d'une course.** Le trigger PL/pgSQL correspondant
  (`supabase_schema.sql`, section « Retrait de la portion de territoire lors de la suppression
  d'une course ») a déjà été corrigé une fois : un simple `ST_Difference` entre le territoire fusionné
  et le polygone de la course supprimée retire à tort une zone encore couverte par une autre course
  existante. Le calcul recompose désormais le territoire depuis zéro (union des polygones des
  courses restantes). Ne pas revenir à l'implémentation naïve.
- **Un seul territoire par joueur, maintenant garanti en base.** Le code a toujours supposé
  au plus une ligne `territoires` par `utilisateur_id` (`SELECT ... WHERE utilisateur_id = …`
  sans filtre additionnel dans `enregistrer_course` et `delete_course_territory_portion`),
  mais rien ne l'imposait en base — un historique de doublons avait déjà nécessité un script
  de fusion ponctuel (voir « SCRIPT DE NETTOYAGE ET FUSION INITIALE DES DOUBLONS »). Une
  contrainte `UNIQUE (utilisateur_id)` a été ajoutée après ce nettoyage pour empêcher qu'une
  ligne fantôme ne réapparaisse et ne s'affiche comme une « base » fantôme sur la carte.
- **`territoires.guilde_id` peut se désynchroniser (partiellement corrigé).** Cette colonne
  n'est écrite qu'à la création de la ligne (ou remise à `NULL` à la dissolution d'un clan) ;
  elle ne suivait pas un joueur qui change de guilde. `get_territoires_geojson` (web-admin)
  la lisait directement et affichait donc un ancien clan — corrigé pour joindre sur
  `profiles.guilde_id` (source vivante, comme `get_territoires_in_bbox` et
  `clan_leaderboard` le faisaient déjà). `enregistrer_course` rafraîchit maintenant aussi
  `territoires.guilde_id` à chaque conquête pour que la colonne se corrige d'elle-même.
- **`profiles.distance_totale` : colonne cache (nouvelle).** Les vues `leaderboard` et
  `clan_leaderboard` recalculaient `SUM(courses.distance_totale)` via une sous-requête
  corrélée par ligne, alors que `LeaderboardViewModel` charge le classement complet, sans
  filtre ni pagination, à chaque ouverture de l'app — un coût qui grossit avec l'historique
  de courses de toute la base. `update_profile_stats_on_course` maintient maintenant
  `profiles.distance_totale` en cache (même principe que `total_area_m2`), et les vues
  lisent directement cette colonne.
- **Fichiers d'icônes homonymes.** `ui/components/CustomIcons.kt` et `ui/icons/CustomIcons.kt` sont
  deux fichiers générés distincts (packages et jeux d'icônes différents) qui portent le même nom —
  vérifier le package avant de modifier ou de supposer un doublon.
- **Section Social masquée.** `GuildeScreen.kt` affiche actuellement un placeholder « en
  construction » sur toute la section Social (voir historique récent de commits) ; les
  fonctionnalités sociales (amis, invitations) existent en base et dans les modèles mais ne sont
  pas exposées à l'utilisateur final tant que ce flag n'est pas levé.
- **Architecture d'état hétérogène.** `LeaderboardViewModel` est le seul ViewModel du projet ; tous
  les autres écrans gèrent leur état directement dans le Composable (`remember`, `LaunchedEffect`).
  Un nouvel écran doit suivre le style dominant plutôt que d'introduire un deuxième pattern isolé,
  sauf décision explicite de migrer progressivement l'ensemble des écrans.
- **Clé de service Supabase.** `web-admin/src/lib/supabaseAdmin.ts` utilise la clé `service_role`
  et ne doit jamais être importé depuis un composant client — uniquement depuis les routes API
  (`src/app/api/admin/*`).
- **`courses.superficie_conquise` ≠ gain net de territoire (piège non corrigé, voir audit).**
  Cette valeur, affichée dans le feed et utilisée pour l'XP, est l'aire brute de la boucle de
  la course. Si la boucle recoupe le territoire déjà possédé par le même joueur,
  `profiles.total_area_m2` (source du classement) n'augmente que de la surface réellement
  nouvelle après `ST_Union` côté serveur — strictement inférieure à `superficie_conquise`
  dans ce cas précis. Le feed peut donc annoncer un gain supérieur à ce que le classement
  reflète ensuite. Non corrigé ici : cela suppose une décision produit (afficher l'aire
  brute de la boucle, ou le gain net de territoire) plutôt qu'un simple bug de calcul.
- **`get_feed_courses` et le classement global ne sont pas paginés.** `get_feed_courses`
  renvoie tout l'historique visible (amis + guilde + proximité) avec le tracé GPS complet de
  chaque course en un seul appel, et `LeaderboardViewModel` charge `SELECT * FROM
  leaderboard` sans filtre ni `LIMIT` à chaque ouverture de l'app pour trier côté client. Les
  deux passent à l'échelle avec le nombre total de joueurs/courses plutôt qu'avec ce que
  l'utilisateur voit réellement à l'écran — à surveiller si la base grossit.

## Tests

Le projet ne contient actuellement aucune suite de tests automatisés (ni tests unitaires, ni tests
instrumentés Android, ni tests web-admin) — seuls les gabarits par défaut d'Android Studio
(`androidx-junit`, `espresso-core`) sont présents dans le catalogue de dépendances sans test
associé. La validation se fait manuellement (build + usage réel avec une localisation GPS pour la
partie tracking). Si des tests sont ajoutés, documenter ici la commande de lancement et la
stratégie retenue.

## Licence

Distribué sous [PolyForm Noncommercial License 1.0.0](LICENSE) : le code est librement consultable, clonable et modifiable à des fins non commerciales, à condition de conserver l'attribution (voir la mention `Required Notice` dans le fichier [LICENSE](LICENSE)). Toute utilisation commerciale est exclue sans autorisation explicite de l'auteur.
