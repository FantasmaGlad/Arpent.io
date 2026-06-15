# Variables d'environnement pour Vercel (web-admin)

Ces variables doivent être configurées dans les paramètres de votre projet Vercel (**Project Settings > Environment Variables**) pour permettre le bon fonctionnement de la console d'administration.

> [!IMPORTANT]
> Pour des raisons de sécurité (bloqué par la protection push GitHub), les clés ont été masquées ci-dessous. Veuillez récupérer les vraies valeurs depuis votre fichier local `web-admin/.env.local` ou `.env`.

---

## 🔑 Variables requises pour Vercel

| Nom de la variable | Valeur à saisir | Description / Portée |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_SUPABASE_URL` | *Récupérer dans `.env.local`* | **Public (Client & Serveur)** : URL de l'instance Supabase. |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | *Récupérer dans `.env.local`* | **Public (Client & Serveur)** : Clé anonyme publique Supabase. |
| `SUPABASE_SERVICE_ROLE_KEY` | *Récupérer dans `.env.local`* | **Privé (Serveur uniquement)** : Clé service_role secrète pour contourner le RLS lors des actions d'administration. |
| `NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN` | *Récupérer dans `.env.local`* | **Public (Client & Serveur)** : Token d'accès public Mapbox pour charger la carte. |

---

## 💡 Instructions de configuration sur Vercel

1. Rendez-vous sur votre tableau de bord **Vercel**.
2. Sélectionnez le projet de votre interface d'administration (`web-admin`).
3. Allez dans **Settings** > **Environment Variables**.
4. Ajoutez les clés et valeurs listées ci-dessus une par une.
5. Cochez les cases **Production**, **Preview**, et **Development** pour chaque clé.
6. Cliquez sur **Save**.
