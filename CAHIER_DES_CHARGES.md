# Cahier des Charges - Arpent.io

## 1. Présentation du Projet
**Arpent.io** est une application mobile ludique et sportive mêlant suivi d'activité physique (type Strava) et jeu de conquête de territoires (type Risk).
L'objectif est d'encourager la pratique sportive (course, marche) en permettant aux utilisateurs de "conquérir" des parcelles géographiques réelles. Lorsqu'un utilisateur réalise une activité et forme une boucle fermée avec son tracé GPS, la zone délimitée devient son territoire.

L'application intègre également une forte dimension sociale et d'équipe, permettant aux joueurs de se regrouper en **Guildes/Clans** pour conquérir des territoires collectivement, ainsi que des fonctionnalités communautaires (fil d'actualité, amis, commentaires et réactions).

## 2. Architecture Technique
Le projet est divisé en plusieurs composants :
- **Application Mobile (Client Principal) :**
  - Développée nativement pour Android en **Kotlin**.
  - Interface utilisateur moderne réalisée avec **Jetpack Compose**.
  - Intégration cartographique avec **Mapbox SDK** (et son extension Compose).
  - Gestion locale et cache avec **Room Database**.
- **Backend as a Service (BaaS) :**
  - **Supabase** sert de backend complet (Base de données, Authentification, Stockage de fichiers, Temps réel).
  - La base de données relationnelle est **PostgreSQL** enrichie de l'extension **PostGIS** pour effectuer tous les calculs spatiaux complexes (intersections, unions de polygones, calcul de surfaces).
- **Interface d'Administration (Web Admin) :**
  - Application web développée en **React / Next.js 16**.
  - Stylisée avec Tailwind CSS (implicite/habituel sur Next) et utilisation de **Lucide-react** et **Recharts**.
  - Permet la modération, la gestion des profils et des guildes, ainsi que l'accès aux statistiques globales.

## 3. Modèle de Données et Logique Spatiale (Supabase & PostGIS)
Le schéma de la base de données est riche et robuste, s'appuyant massivement sur les procédures stockées (RPC) et les Triggers pour assurer la cohérence et l'anti-triche.

### Principales Entités :
- **`profiles`** : Étend les utilisateurs authentifiés. Stocke l'expérience (XP), le niveau, la couleur de l'empire, la localisation, les statistiques d'activité et la superficie conquise.
- **`guildes`** : Représente les clans. Possède un chef, un nom, une couleur, un blason et un identifiant unique (Tag).
- **`courses`** : Enregistre les métadonnées d'une session sportive (distance, durée, vitesse, calories, dénivelé). Détermine si la course est une "boucle" valide.
- **`points_gps`** : Stocke l'historique détaillé des points (lat, long, vitesse, accélération) rattaché à une course.
- **`territoires`** : Stocke les polygones conquis (colonne `contour` de type PostGIS `geometry`). Les opérations de fusion (Union) et de vol de territoire (Différence) sont calculées ici.
- **Social** : Tables pour les **`amis`**, **`course_reactions`** (les likes, appelés "baamix"), **`course_commentaires`**, et **`notifications`**.

### Mécanique de Conquête :
Lorsqu'une course "bouclée" est enregistrée, une procédure stockée (`enregistrer_course`) convertit les points GPS en un polygone PostGIS. Elle gère alors les conflits :
- Si la boucle chevauche le territoire d'un adversaire, la partie superposée est soustraite à l'adversaire (vol de territoire) et une notification est envoyée.
- Si elle touche le propre territoire du joueur, les polygones sont fusionnés.
- Des vérifications anti-triche (vérification de vitesse max, corrélation entre durée et distance) empêchent la falsification des tracés.

## 4. Fonctionnalités Clés de l'Application Mobile
- **Suivi Sportif (Tracking) :** Service en arrière-plan (Foreground Service) pour relever précisément la position GPS pendant l'effort.
- **Carte et Conquête :** Visualisation en temps réel des territoires (les siens, ceux de sa guilde, ceux des adversaires) via Mapbox.
- **Système de Progression :** Gains d'XP proportionnels à la distance parcourue et à la surface conquise, calcul de niveaux, historique de variation de l'empire sur 24h.
- **Gestion des Clans :** Création, invitation de membres, rôles hiérarchiques (Chef, Adjoint, Membre). Le territoire d'un clan est la somme des territoires de ses membres.
- **Fil d'actualité (Feed) :** Parcours de l'activité des amis et des membres de sa guilde. Possibilité de commenter et d'envoyer un "baamix".
- **Classements (Leaderboards) :** Classements des joueurs et des guildes, filtrables par proximité géographique (local) ou de manière globale.

## 5. Interface Web d'Administration
Située dans le dossier `web-admin/`, elle est destinée à l'équipe gérant le jeu :
- **Gestion des Joueurs :** Visualisation des profils, bannissements potentiels, vérification de la légitimité des statistiques.
- **Gestion des Clans :** Suivi des guildes et des territoires.
- **Monitoring :** Accès au fil global des courses, statistiques sur la volumétrie de l'application (nombre de sessions, kilomètres parcourus).

## 6. Guide de Contribution & Setup
Pour collaborer efficacement sur ce projet, les développeurs doivent configurer leurs environnements locaux de la manière suivante.

### A. Environnement Supabase (Backend)
- Un projet Supabase doit être créé.
- Le fichier `supabase_schema.sql` (situé à la racine) doit être exécuté dans l'éditeur SQL de Supabase pour générer toutes les tables, triggers, politiques de sécurité (RLS) et fonctions RPC.
- Les identifiants Supabase (URL et clé publique anonyme) devront être récupérés.

### B. Application Mobile (Android)
- **Prérequis :** Android Studio (version récente supportant Kotlin 1.9+ / 2.0).
- **Fichier `.env` :** Créer un fichier `.env` à la racine du projet (au même niveau que `settings.gradle.kts`) contenant :
  ```properties
  SUPABASE_URL=votre_url_supabase
  SUPABASE_PUBLISHABLE_KEY=votre_cle_anon
  MAPBOX_PUBLIC_TOKEN=votre_token_public_mapbox
  MAPBOX_SECRET_TOKEN=votre_token_secret_pour_le_telechargement_des_dependances
  ```
- **Clé Mapbox :** Mapbox exige un token secret configuré avec l'étendue de téléchargement (`Downloads:Read`) pour résoudre ses SDK via Gradle. Ce token est lu automatiquement depuis le fichier `.env` dans le fichier `settings.gradle.kts`.
- **Lancement :** Synchroniser le projet Gradle et compiler (Build) puis déployer sur un émulateur ou appareil physique.

### C. Web Admin (Next.js)
- **Prérequis :** Node.js (v18+) et npm/yarn/bun.
- **Dépendances :** Se positionner dans le dossier `web-admin` et lancer `npm install`.
- **Variables d'environnement :** Créer un fichier `.env.local` dans `web-admin` :
  ```env
  NEXT_PUBLIC_SUPABASE_URL=votre_url_supabase
  NEXT_PUBLIC_SUPABASE_ANON_KEY=votre_cle_anon
  ```
- **Lancement :** Exécuter `npm run dev` et ouvrir `http://localhost:3000`.

## 7. Règles et Conventions
- **Base de données :** Toute modification de la logique de jeu impliquant la sécurité (anti-triche, validation géospatiale) doit idéalement être ajoutée dans les RPC et Triggers Supabase, limitant ainsi la confiance accordée au client mobile.
- **RLS (Row Level Security) :** Toujours vérifier les politiques RLS lors de l'ajout de nouvelles tables pour assurer la confidentialité des données entre les utilisateurs.
- **Performance spatiale :** L'indexation spatiale (`USING gist (contour)`) est cruciale. Veiller à ne pas dégrader les requêtes Bounding Box (`&&`) utilisées pour l'affichage de la carte Mapbox.