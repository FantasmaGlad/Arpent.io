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

## Base de données

Le fichier [`supabase_schema.sql`](supabase_schema.sql) contient l'intégralité du schéma : définitions de tables, contraintes, index, fonctions PL/pgSQL et politiques de sécurité au niveau ligne (RLS). Il ne contient aucune donnée réelle ni identifiant — c'est un schéma pur, exportable et rejouable sur un projet Supabase neuf.

Tables principales : `profiles`, `courses`, `territoires`, `points_gps`, `guildes`, `guilde_invitations`, `amis`, `notifications`, `course_reactions`, `course_commentaires`, `admins`.

## Licence

Projet propriétaire — tous droits réservés. Aucune licence d'utilisation, de modification ou de redistribution n'est accordée sans autorisation explicite de l'auteur.
