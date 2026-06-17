# Audit de Sécurité et d'Optimisation - SportAndroid

Cet audit présente une analyse détaillée des failles identifiées au sein de l'application Android, du tableau de bord d'administration Web et de la base de données Supabase.

---

## 1. Failles de Calcul Spatial & Cohérence des Profils

### A. Calcul de Surface Flou en Degrés Carrés ($deg^2$) (Android)
*   **Statut :** 🟢 **Résolu**
*   **Fichier concerné :** [`MainActivity.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/MainActivity.kt#L1196-L1206) (déplacé dans [`GeoUtils.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/utils/GeoUtils.kt))
*   **Description :** La fonction `getPolygonArea` utilise la formule mathématique de Shoelace (formule des lacets) directement sur des coordonnées géographiques brutes (Latitude et Longitude).
*   **Correction apportée :** Remplacement par la formule de calcul de surface sphérique sur l'ellipsoïde WGS84 ($R = 6\,378\,137.0\text{ m}$) pour obtenir une aire exacte en mètres carrés ($m^2$).

### B. Écarts de Mesure de Surface (Local vs Base de données)
*   **Statut :** 🟢 **Résolu**
*   **Fichier concerné :** [`MainActivity.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/MainActivity.kt#L1127-L1144) (déplacé dans [`GeoUtils.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/utils/GeoUtils.kt))
*   **Description :** L'application estime localement l'aire conquise via une projection plane simplifiée (`estimateAreaKm2` avec une constante fixe de $1^\circ \approx 111\,000\text{ m}$), tandis que la base de données effectue un calcul précis basé sur l'ellipsoïde de référence géodésique via PostGIS (`ST_Area(geom::geography)`).
*   **Correction apportée :** Alignement de `estimateAreaKm2` sur le calcul de surface sphérique WGS84 utilisé par `getPolygonArea`, garantissant la cohérence stricte avec PostGIS.

### C. Lag de Fusion et Affichage Superposé (Android)
*   **Statut :** 🟢 **Résolu**
*   **Fichiers concernés :** [`StorageUtils.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/utils/StorageUtils.kt) et [`ConquestMapScreen.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/ui/screens/ConquestMapScreen.kt)
*   **Description :** Lors de la validation d'une course, le client ajoute instantanément les points bruts à sa liste locale `completedPolygons`. En revanche, en base de données, la fonction `enregistrer_course` fusionne ce tracé avec le territoire global existant du joueur et en soustrait les chevauchements avec les alliés ou les ennemis.
*   **Correction apportée :** Modification de `saveRunToDatabase` pour recharger dynamiquement les polygones nettoyés et fusionnés depuis la base de données (`syncTerritoriesFromDatabase`) immédiatement après le succès de la synchronisation en arrière-plan.

### D. Fusion des Statistiques "All-Time" et "Actuel"
*   **Statut :** 🟢 **Résolu**
*   **Fichiers concernés :** [`ArpentMainScreen.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/ui/screens/ArpentMainScreen.kt) et [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql)
*   **Description :** Dans les statistiques du profil, les variables d'empire cumulé historique ("Empire All-Time") et d'empire possédé actuellement ("Empire Actuel") pointent toutes les deux sur le même champ calculé `profiles.total_area_m2`.
*   **Correction apportée :** 
    1. Ajout d'une colonne `all_time_area_m2` dans la table `profiles`.
    2. Mise à jour de la fonction trigger `update_profile_cached_area` pour calculer le cumul des gains de superficie positifs uniquement (ignorant les pertes territoriales).
    3. Optimisation de l'affichage mobile pour charger directement les champs en cache `total_area_m2` et `all_time_area_m2`.

---

## 2. Failles de Base de Données, Indexation & Sécurité SQL

### A. Faille de Contournement par Masquage (Exploit d'Invisibilité)
*   **Fichiers concernés :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L213-L221) et [`get_territoires_in_bbox`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L599)
*   **Description :** La politique de sécurité RLS de sélection sur la table `territoires` ainsi que la fonction RPC `get_territoires_in_bbox` excluent les enregistrements des utilisateurs ayant désactivé le partage de position (`share_location = false`).
*   **Impact :** Cette implémentation introduit une faille majeure dans les règles du jeu. Un joueur peut conquérir de vastes zones de la ville, puis désactiver `share_location` dans ses paramètres de profil. Ses territoires deviennent instantanément invisibles et impossibles à cibler pour les autres joueurs sur la carte, tout en lui permettant de continuer à jouer et à étendre son empire en toute discrétion.
*   **Correction recommandée :** Dissocier la confidentialité de la position en temps réel (champ `latitude`/`longitude` du profil, qui doit effectivement respecter `share_location`) de la visibilité des polygones de conquête statiques (qui doivent rester publics et visibles par tous pour permettre le gameplay de conquête territoriale).

### B. Typo dans le SRID Géographique (Bug de Proximité)
*   **Fichier concerné :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L666)
*   **Description :** Dans la fonction de suggestion d'amis par proximité `suggerer_amis_proximite`, la comparaison utilise par erreur le SRID `4327` :
    `ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4327)::geography`
*   **Impact :** Le standard géographique mondial pour les coordonnées GPS est le SRID `4326` (WGS 84). L'utilisation du SRID inexistant ou mal configuré `4327` provoque des avertissements d'exécution PostGIS ou fait échouer silencieusement la requête de comparaison spatiale `ST_DWithin`, empêchant l'affichage des suggestions d'amis.

### C. Déficit Critique d'Indexation sur les Clés Étrangères
*   **Fichier concerné :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql)
*   **Description :** Les clés étrangères servant de pivots pour les requêtes de jointure et de filtrage fréquentes ne possèdent aucun index en base de données.
*   **Impact :**
    *   `profiles.guilde_id` : Ralentit le calcul des classements de guildes et les filtres d'affichage.
    *   `territoires.utilisateur_id` : Force PostgreSQL à scanner toute la table pour récupérer les zones d'un joueur.
    *   `courses.utilisateur_id` : Cause des lenteurs drastiques lors de l'ouverture de l'historique des courses d'un joueur à mesure que le nombre d'activités enregistrées augmente.
    *   `amis.demandeur_id` / `amis.destinataire_id` : Pénalise la vérification et l'affichage des relations sociales.

### D. Risque de Verrouillage Mutuel (Deadlock)
*   **Fichier concerné :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L469-L508)
*   **Description :** La fonction `enregistrer_course` effectue des opérations géométriques lourdes (`ST_Difference`, `ST_Union`) et met à jour plusieurs lignes de la table `territoires` dans une boucle sans verrouillage déterministe (par exemple, sans utiliser `SELECT ... FOR UPDATE` ou sans ordonner le verrouillage par ID).
*   **Impact :** Si deux joueurs actifs terminent des parcours qui se recoupent simultanément, leurs transactions respectives risquent de s'entrecroiser en verrouillant les lignes des territoires ennemis de façon croisée, provoquant un plantage de transaction pour cause de "Deadlock" côté PostgreSQL.

---

## 3. Failles de Gestion des Données, Synchronisation & Sécurité

### A. Faille de Duplication en Cas de Perte de Réseau
*   **Fichier concerné :** [`PendingRunsQueue.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/PendingRunsQueue.kt#L123-L146)
*   **Description :** Le système de synchronisation en arrière-plan envoie la course via l'appel RPC `enregistrer_course`. Si la requête SQL réussit côté serveur, mais qu'une coupure réseau survient avant que le client ne reçoive le code de confirmation HTTP, le client conserve la course dans sa base de données Room locale.
*   **Impact :** Lors de la prochaine tentative de synchronisation, la course sera renvoyée et insérée une seconde fois. Comme la table `courses` ne possède aucune contrainte d'unicité sur le couple `(utilisateur_id, date_debut)`, la course est dupliquée, ce qui fausse artificiellement les statistiques physiques du joueur (durée, distance, calories).

### B. Absence de Contrôle Anti-Triche et de Validation de Vitesse (Serveur)
*   **Fichier concerné :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L386)
*   **Description :** La fonction SQL `enregistrer_course` fait aveuglément confiance aux valeurs de vitesse, distance, durée et points géographiques envoyées par le client.
*   **Impact :** Un utilisateur malveillant peut contourner l'application mobile et appeler directement l'API Supabase pour envoyer des coordonnées forgées décrivant un polygone couvrant une ville entière en déclarant une durée de course factice. La base de données validera et fusionnera la conquête sans vérifier si la vitesse moyenne est humainement possible, permettant de tricher instantanément sans effort physique.

### C. Inefficacité de l'Algorithme d'Insertion des Points GPS
*   **Fichier concerné :** [`supabase_schema.sql`](file:///home/fanta/AndroidStudioProjects/SportAndroid/supabase_schema.sql#L440-L449)
*   **Description :** Pour chaque point de passage d'une course, la fonction effectue une opération d'expression régulière coûteuse (`regexp_split_to_array`) et exécute un `INSERT` individuel dans la table `points_gps` au sein d'une boucle PL/pgSQL.
*   **Impact :** Pour une course contenant des centaines de points, cela surcharge inutilement le processeur de la base de données. Il serait bien plus performant d'effectuer une insertion groupée ou d'exploiter les structures de lignes PostGIS (LineString) directement.

---

## 4. Failles Ergonomiques et de Mise en Page UI/UX

### A. Uniformité des Couleurs de Faction (Web Admin)
*   **Fichier concerné :** [`web-admin/src/app/page.tsx`](file:///home/fanta/AndroidStudioProjects/SportAndroid/web-admin/src/app/page.tsx#L216)
*   **Description :** Le code du tableau de bord Web force la couleur `#CCFF00` (vert fluo) pour l'affichage de tous les territoires et marqueurs de joueurs sur la carte d'administration.
*   **Impact :** Cette valeur fixe écrase les propriétés dynamiques `empire_color` et `guilde_couleur` pourtant disponibles dans l'objet GeoJSON. Il est ainsi impossible pour l'administrateur de distinguer visuellement l'appartenance des zones à des guildes ou des joueurs différents, ce qui nuit à la surveillance globale de l'équilibre du jeu.

### B. Manque de Rétroaction sur les Limites de Vitesse (Android)
*   **Fichier concerné :** [`LocationTrackerState.kt`](file:///home/fanta/AndroidStudioProjects/SportAndroid/app/src/main/java/com/fanta/androidsport/LocationTrackerState.kt#L135)
*   **Description :** Si l'utilisateur dépasse la vitesse maximale autorisée de $12\text{ m/s}$ (par exemple, s'il prend son vélo ou sa voiture), les points GPS sont rejetés silencieusement dans la console (`Log.d`).
*   **Impact :** L'utilisateur ne reçoit aucun retour visuel ou sonore direct dans l'interface de l'application lui indiquant que ses points sont rejetés et que sa course ne sera pas prise en compte pour la conquête. Cela génère de la frustration et des plaintes d'incompréhension.
