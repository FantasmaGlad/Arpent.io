-- ==========================================
-- CONSOLIDATED DATABASE SCHEMA FOR ARPENT.IO
-- ==========================================

-- 1. Activer l'extension PostGIS et sécuriser spatial_ref_sys
CREATE EXTENSION IF NOT EXISTS postgis;

-- Sécurisation de spatial_ref_sys (Faille de sécurité RLS)
-- Note: RLS ne peut pas être activée si le rôle postgres n'est pas le propriétaire de la table.
-- Pour sécuriser la table, nous révoquons tous les privilèges d'accès pour les rôles API publics (anon et authenticated).
REVOKE ALL PRIVILEGES ON TABLE public.spatial_ref_sys FROM anon, authenticated;

-- 2. Table des Guildes/Clans (avec chef_id)
CREATE TABLE IF NOT EXISTS public.guildes (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    nom text NOT NULL UNIQUE,
    couleur_hex text NOT NULL,
    avatar_url text,
    chef_id uuid, -- configuré en FK après la création de profiles
    tag text UNIQUE, -- Tag immutable #AA11AA11
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Table des Profils Utilisateurs (liée à la table auth.users de Supabase Auth)
CREATE TABLE IF NOT EXISTS public.profiles (
    id uuid REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
    pseudonyme text UNIQUE,
    guilde_id uuid REFERENCES public.guildes(id) ON DELETE SET NULL,
    total_area_m2 float DEFAULT 0.0 NOT NULL,
    all_time_area_m2 float DEFAULT 0.0 NOT NULL,
    share_location boolean DEFAULT true NOT NULL,
    avatar_url text,
    empire_color text DEFAULT '#00E676',
    latitude float,
    longitude float,
    tag text UNIQUE, -- Tag immutable #AA11AA11
    grade text DEFAULT 'membre' CHECK (grade IN ('chef', 'adjoint', 'membre')),
    date_inscription timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- S'assurer que la colonne chef_id existe dans guildes avant d'ajouter la contrainte
ALTER TABLE public.guildes ADD COLUMN IF NOT EXISTS chef_id uuid;

-- Ajouter la contrainte FK chef_id référençant profiles sur la table guildes
ALTER TABLE public.guildes DROP CONSTRAINT IF EXISTS fk_guildes_chef_id;
ALTER TABLE public.guildes ADD CONSTRAINT fk_guildes_chef_id 
    FOREIGN KEY (chef_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS profiles_total_area_m2_idx ON public.profiles(total_area_m2 DESC);

-- 4. Table des Sessions de Course (enrichi type Strava)
CREATE TABLE IF NOT EXISTS public.courses (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    utilisateur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    date_debut timestamp with time zone NOT NULL,
    date_fin timestamp with time zone,
    distance_totale float DEFAULT 0.0 NOT NULL,
    duree_secondes float DEFAULT 0.0 NOT NULL,
    est_bouclee boolean DEFAULT false NOT NULL,
    vitesse_moyenne float DEFAULT 0.0, -- km/h
    vitesse_max float DEFAULT 0.0, -- km/h
    allure_moyenne float DEFAULT 0.0, -- min/km (pace)
    calories_estimees float DEFAULT 0.0, -- kcal
    denivele_positif float DEFAULT 0.0, -- mètres cumulés montée
    denivele_negatif float DEFAULT 0.0, -- mètres cumulés descente
    points_gps_count integer DEFAULT 0
);

-- 5. Table des Territoires Conquis (PostGIS geometry polygon)
CREATE TABLE IF NOT EXISTS public.territoires (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    utilisateur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    guilde_id uuid REFERENCES public.guildes(id) ON DELETE SET NULL,
    contour geometry(Geometry, 4326) NOT NULL,
    superficie_m2 float NOT NULL,
    points text[] NOT NULL, -- points du contour stockés sous forme textuelle 'long lat'
    derniere_mise_a_jour timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS territoires_contour_geo_idx ON public.territoires USING gist(contour);

-- 6. Table des points GPS pour l'historique complet des tracés
CREATE TABLE IF NOT EXISTS public.points_gps (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    course_id uuid REFERENCES public.courses(id) ON DELETE CASCADE NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    altitude double precision,
    vitesse float,
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS points_gps_course_id_idx ON public.points_gps(course_id);

-- 7. Table des relations d'amis
CREATE TABLE IF NOT EXISTS public.amis (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    demandeur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    destinataire_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    statut text DEFAULT 'en_attente' NOT NULL, -- 'en_attente', 'accepte'
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE (demandeur_id, destinataire_id)
);

-- ==========================================
-- MIGRATION DE MISE À JOUR DES COLONNES EXISTANTES
-- ==========================================
ALTER TABLE public.guildes ADD COLUMN IF NOT EXISTS avatar_url text;
ALTER TABLE public.guildes ADD COLUMN IF NOT EXISTS chef_id uuid;
ALTER TABLE public.guildes ADD COLUMN IF NOT EXISTS tag text UNIQUE;

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS total_area_m2 float DEFAULT 0.0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS share_location boolean DEFAULT true NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS avatar_url text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS empire_color text DEFAULT '#00E676';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS latitude float;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS longitude float;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS guilde_id uuid REFERENCES public.guildes(id) ON DELETE SET NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS tag text UNIQUE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS grade text DEFAULT 'membre' CHECK (grade IN ('chef', 'adjoint', 'membre'));

-- Colonnes Strava enrichies sur courses
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS vitesse_moyenne float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS vitesse_max float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS allure_moyenne float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS calories_estimees float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS denivele_positif float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS denivele_negatif float DEFAULT 0.0;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS points_gps_count integer DEFAULT 0;

ALTER TABLE public.territoires ALTER COLUMN contour TYPE geometry(Geometry, 4326);
ALTER TABLE public.territoires ADD COLUMN IF NOT EXISTS points text[];

-- 8. Table des Administrateurs
CREATE TABLE IF NOT EXISTS public.admins (
    id uuid REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
    role text NOT NULL DEFAULT 'moderateur' CHECK (role IN ('super_admin', 'moderateur')),
    nom_complet text,
    avatar_url text,
    derniere_connexion timestamp with time zone DEFAULT now(),
    date_creation timestamp with time zone DEFAULT now()
);

-- Fonction utilitaire pour vérifier si un utilisateur est admin
CREATE OR REPLACE FUNCTION public.is_admin(p_user_id uuid)
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.admins WHERE id = p_user_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Les admins peuvent lire la table admins" ON public.admins;
CREATE POLICY "Les admins peuvent lire la table admins" ON public.admins
    FOR SELECT USING (
        public.is_admin(auth.uid())
    );

-- ==========================================
-- SÉCURITÉ : ROW LEVEL SECURITY (RLS) & POLITIQUES
-- ==========================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.guildes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.territoires ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.points_gps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.amis ENABLE ROW LEVEL SECURITY;

-- Politiques Profiles
DROP POLICY IF EXISTS "Tout le monde peut lire les profils" ON public.profiles;
CREATE POLICY "Tout le monde peut lire les profils" ON public.profiles FOR SELECT USING (true);

DROP POLICY IF EXISTS "Les utilisateurs peuvent modifier leur propre profil" ON public.profiles;
CREATE POLICY "Les utilisateurs peuvent modifier leur propre profil" ON public.profiles FOR UPDATE USING (auth.uid() = id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les admins peuvent insérer des profils" ON public.profiles;
CREATE POLICY "Les admins peuvent insérer des profils" ON public.profiles FOR INSERT WITH CHECK (public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les admins peuvent supprimer des profils" ON public.profiles;
CREATE POLICY "Les admins peuvent supprimer des profils" ON public.profiles FOR DELETE USING (public.is_admin(auth.uid()));

-- Politiques Guildes
DROP POLICY IF EXISTS "Tout le monde peut lire les guildes" ON public.guildes;
CREATE POLICY "Tout le monde peut lire les guildes" ON public.guildes FOR SELECT USING (true);

DROP POLICY IF EXISTS "Seuls les utilisateurs authentifiés peuvent créer des guildes" ON public.guildes;
CREATE POLICY "Seuls les utilisateurs authentifiés peuvent créer des guildes" ON public.guildes FOR INSERT WITH CHECK (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Les chefs de clan et admins peuvent modifier leur guilde" ON public.guildes;
CREATE POLICY "Les chefs de clan et admins peuvent modifier leur guilde" ON public.guildes FOR UPDATE USING (auth.uid() = chef_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les chefs de clan et admins peuvent supprimer leur guilde" ON public.guildes;
CREATE POLICY "Les chefs de clan et admins peuvent supprimer leur guilde" ON public.guildes FOR DELETE USING (auth.uid() = chef_id OR public.is_admin(auth.uid()));

-- Politiques Courses
DROP POLICY IF EXISTS "Les utilisateurs peuvent voir leurs propres courses" ON public.courses;
CREATE POLICY "Les utilisateurs peuvent voir leurs propres courses" ON public.courses FOR SELECT USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les utilisateurs peuvent insérer leurs propres courses" ON public.courses;
CREATE POLICY "Les utilisateurs peuvent insérer leurs propres courses" ON public.courses FOR INSERT WITH CHECK (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les admins peuvent modifier les courses" ON public.courses;
CREATE POLICY "Les admins peuvent modifier les courses" ON public.courses FOR UPDATE USING (public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les admins peuvent supprimer les courses" ON public.courses;
CREATE POLICY "Les admins peuvent supprimer les courses" ON public.courses FOR DELETE USING (public.is_admin(auth.uid()));

-- Politiques Territoires
DROP POLICY IF EXISTS "Tout le monde peut voir les territoires" ON public.territoires;
DROP POLICY IF EXISTS "Les utilisateurs peuvent voir les territoires publics ou les leurs" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent voir les territoires publics ou les leurs" ON public.territoires 
FOR SELECT USING (
    utilisateur_id = auth.uid() 
    OR EXISTS (
        SELECT 1 FROM public.profiles p 
        WHERE p.id = utilisateur_id AND p.share_location = true
    )
    OR public.is_admin(auth.uid())
);

DROP POLICY IF EXISTS "Les utilisateurs peuvent insérer/modifier leurs propres territoires" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent insérer/modifier leurs propres territoires" ON public.territoires FOR INSERT WITH CHECK (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les utilisateurs peuvent modifier leurs propres territoires" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent modifier leurs propres territoires" ON public.territoires FOR UPDATE USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

-- Politiques Points GPS
DROP POLICY IF EXISTS "Les utilisateurs peuvent insérer leurs propres points gps" ON public.points_gps;
CREATE POLICY "Les utilisateurs peuvent insérer leurs propres points gps" ON public.points_gps 
FOR INSERT WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.courses c 
        WHERE c.id = course_id AND c.utilisateur_id = auth.uid()
    ) OR public.is_admin(auth.uid())
);

DROP POLICY IF EXISTS "Les utilisateurs peuvent voir leurs propres points gps" ON public.points_gps;
CREATE POLICY "Les utilisateurs peuvent voir leurs propres points gps" ON public.points_gps 
FOR SELECT USING (
    EXISTS (
        SELECT 1 FROM public.courses c 
        WHERE c.id = course_id AND c.utilisateur_id = auth.uid()
    ) OR public.is_admin(auth.uid())
);

-- Politiques Amis
DROP POLICY IF EXISTS "Tout le monde peut voir ses relations d'amis" ON public.amis;
CREATE POLICY "Tout le monde peut voir ses relations d'amis" ON public.amis 
    FOR SELECT USING (auth.uid() = demandeur_id OR auth.uid() = destinataire_id);

DROP POLICY IF EXISTS "Les utilisateurs peuvent demander des amis" ON public.amis;
CREATE POLICY "Les utilisateurs peuvent demander des amis" ON public.amis 
    FOR INSERT WITH CHECK (auth.uid() = demandeur_id);

DROP POLICY IF EXISTS "Les utilisateurs peuvent accepter/modifier leurs demandes" ON public.amis;
CREATE POLICY "Les utilisateurs peuvent accepter/modifier leurs demandes" ON public.amis 
    FOR UPDATE USING (auth.uid() = destinataire_id OR auth.uid() = demandeur_id);

DROP POLICY IF EXISTS "Les utilisateurs peuvent supprimer une relation d'ami" ON public.amis;
CREATE POLICY "Les utilisateurs peuvent supprimer une relation d'ami" ON public.amis 
    FOR DELETE USING (auth.uid() = demandeur_id OR auth.uid() = destinataire_id);

-- ==========================================
-- FONCTIONS DÉCLENCHÉES (TRIGGERS)
-- ==========================================

-- Fonction de génération de tag unique #AA11AA11
CREATE OR REPLACE FUNCTION public.generate_unique_tag(p_table text)
RETURNS text AS $$
DECLARE
  v_tag text;
  v_exists boolean;
  v_chars text := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  v_digits text := '0123456789';
BEGIN
  LOOP
    v_tag := '#'
      || substr(v_chars, floor(random()*26+1)::int, 1)
      || substr(v_chars, floor(random()*26+1)::int, 1)
      || substr(v_digits, floor(random()*10+1)::int, 1)
      || substr(v_digits, floor(random()*10+1)::int, 1)
      || substr(v_chars, floor(random()*26+1)::int, 1)
      || substr(v_chars, floor(random()*26+1)::int, 1)
      || substr(v_digits, floor(random()*10+1)::int, 1)
      || substr(v_digits, floor(random()*10+1)::int, 1);
    
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM public.%I WHERE tag = $1)', p_table)
      INTO v_exists USING v_tag;
    
    IF NOT v_exists THEN RETURN v_tag; END IF;
  END LOOP;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger pour auto-assigner un tag aux profils
CREATE OR REPLACE FUNCTION public.assign_profile_tag()
RETURNS trigger AS $$
BEGIN
  IF NEW.tag IS NULL THEN
    NEW.tag := public.generate_unique_tag('profiles');
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_profile_assign_tag ON public.profiles;
CREATE TRIGGER on_profile_assign_tag
  BEFORE INSERT ON public.profiles
  FOR EACH ROW EXECUTE PROCEDURE public.assign_profile_tag();

-- Trigger pour auto-assigner un tag aux guildes
CREATE OR REPLACE FUNCTION public.assign_guilde_tag()
RETURNS trigger AS $$
BEGIN
  IF NEW.tag IS NULL THEN
    NEW.tag := public.generate_unique_tag('guildes');
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_guilde_assign_tag ON public.guildes;
CREATE TRIGGER on_guilde_assign_tag
  BEFORE INSERT ON public.guildes
  FOR EACH ROW EXECUTE PROCEDURE public.assign_guilde_tag();

-- A. Inscription Automatique d'utilisateurs (avec tag auto-généré via trigger)
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.profiles (id, pseudonyme, date_inscription)
  VALUES (
    new.id,
    COALESCE(
      new.raw_user_meta_data->>'pseudonyme', 
      CASE 
        WHEN new.email IS NULL THEN 'Invité_' || SUBSTR(new.id::text, 1, 8)
        ELSE 'Joueur_' || SUBSTR(new.id::text, 1, 8)
      END
    ),
    now()
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- B. Calcul en temps réel de superficie dans le profil
CREATE OR REPLACE FUNCTION public.update_profile_cached_area()
RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.profiles 
        SET total_area_m2 = total_area_m2 + new.superficie_m2,
            all_time_area_m2 = all_time_area_m2 + new.superficie_m2
        WHERE id = new.utilisateur_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.profiles 
        SET total_area_m2 = GREATEST(0.0, total_area_m2 - old.superficie_m2)
        WHERE id = old.utilisateur_id;
    ELSIF (TG_OP = 'UPDATE') THEN
        UPDATE public.profiles 
        SET total_area_m2 = GREATEST(0.0, total_area_m2 - old.superficie_m2 + new.superficie_m2),
            all_time_area_m2 = all_time_area_m2 + GREATEST(0.0, new.superficie_m2 - old.superficie_m2)
        WHERE id = new.utilisateur_id;
    END IF;
    RETURN null;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_territoire_changed ON public.territoires;
CREATE TRIGGER on_territoire_changed
    AFTER INSERT OR UPDATE OR DELETE ON public.territoires
    FOR EACH ROW EXECUTE PROCEDURE public.update_profile_cached_area();

-- ==========================================
-- FONCTIONS DU MOTEUR DU JEU (RPC)
-- ==========================================

-- A. Enregistrement de course et fusion MultiPolygon avec Conquête (SECURITY DEFINER + auth.uid() check)
CREATE OR REPLACE FUNCTION public.enregistrer_course(
    p_user_id uuid,
    p_date_debut timestamp with time zone,
    p_date_fin timestamp with time zone,
    p_distance_totale float,
    p_duree_secondes float,
    p_est_bouclee boolean,
    p_points text[],
    p_vitesse_moyenne float DEFAULT 0.0,
    p_vitesse_max float DEFAULT 0.0,
    p_allure_moyenne float DEFAULT 0.0,
    p_calories_estimees float DEFAULT 0.0,
    p_denivele_positif float DEFAULT 0.0,
    p_denivele_negatif float DEFAULT 0.0
)
RETURNS void AS $$
DECLARE
    v_course_id uuid;
    v_wkt text;
    v_geom geometry;
    v_superficie float;
    v_pt_text text;
    v_parts text[];
    v_lng float;
    v_lat float;
    v_guilde_id uuid;
    v_enemy_terr record;
    v_team_terr record;
    v_diff_geom geometry;
    v_enemy_pts text[];
    v_new_points text[];
    v_existing_id uuid;
    v_existing_contour geometry;
BEGIN
    -- 0. Contrôle d'accès strict
    IF auth.uid() IS NULL OR p_user_id <> auth.uid() THEN
        RAISE EXCEPTION 'Non autorisé : ID utilisateur invalide ou non authentifié.';
    END IF;

    -- 1. Insérer la session de course
    INSERT INTO public.courses (
        utilisateur_id, date_debut, date_fin, distance_totale, duree_secondes, est_bouclee,
        vitesse_moyenne, vitesse_max, allure_moyenne, calories_estimees,
        denivele_positif, denivele_negatif, points_gps_count
    )
    VALUES (
        p_user_id, p_date_debut, p_date_fin, p_distance_totale, p_duree_secondes, p_est_bouclee,
        p_vitesse_moyenne, p_vitesse_max, p_allure_moyenne, p_calories_estimees,
        p_denivele_positif, p_denivele_negatif, COALESCE(array_length(p_points, 1), 0)
    )
    RETURNING id INTO v_course_id;

    -- 2. Insérer les points GPS individuels
    IF array_length(p_points, 1) > 0 THEN
        FOR i IN 1..array_length(p_points, 1) LOOP
            v_pt_text := p_points[i];
            v_parts := regexp_split_to_array(trim(v_pt_text), '\s+');
            IF array_length(v_parts, 1) = 2 THEN
                v_lng := v_parts[1]::float;
                v_lat := v_parts[2]::float;
                INSERT INTO public.points_gps (course_id, latitude, longitude)
                VALUES (v_course_id, v_lat, v_lng);
            END IF;
        END LOOP;
    END IF;

    -- 3. Gestion de la boucle de territoire
    IF p_est_bouclee AND array_length(p_points, 1) >= 3 THEN
        v_wkt := 'POLYGON((' || array_to_string(p_points, ', ') || '))';
        
        BEGIN
            v_geom := ST_GeomFromText(v_wkt, 4326);
            
            IF NOT ST_IsValid(v_geom) THEN
                v_geom := ST_MakeValid(v_geom);
            END IF;

            v_geom := ST_CollectionExtract(v_geom, 3);
            
            -- Récupérer la guilde de l'utilisateur
            SELECT guilde_id INTO v_guilde_id FROM public.profiles WHERE id = p_user_id;

            -- A. CONQUÊTE : Soustraire ce nouveau territoire aux ennemis
            FOR v_enemy_terr IN 
                SELECT t.id, t.contour, p.guilde_id as enemy_guilde_id
                FROM public.territoires t
                JOIN public.profiles p ON t.utilisateur_id = p.id
                WHERE t.utilisateur_id <> p_user_id 
                  AND (v_guilde_id IS NULL OR p.guilde_id IS NULL OR p.guilde_id <> v_guilde_id)
                  AND ST_Intersects(t.contour, v_geom)
            LOOP
                v_diff_geom := ST_Difference(v_enemy_terr.contour, v_geom);
                v_diff_geom := ST_CollectionExtract(v_diff_geom, 3);
                
                IF ST_IsEmpty(v_diff_geom) THEN
                    DELETE FROM public.territoires WHERE id = v_enemy_terr.id;
                ELSE
                    SELECT array_agg(ST_X(geom) || ' ' || ST_Y(geom)) INTO v_enemy_pts
                    FROM ST_DumpPoints(v_diff_geom);
                    
                    UPDATE public.territoires 
                    SET contour = v_diff_geom,
                        superficie_m2 = ST_Area(v_diff_geom::geography),
                        points = v_enemy_pts,
                        derniere_mise_a_jour = now()
                    WHERE id = v_enemy_terr.id;
                END IF;
            END LOOP;

            -- B. NON-SUPERPOSITION ALLIÉS : Soustraire les territoires alliés existants
            IF v_guilde_id IS NOT NULL THEN
                FOR v_team_terr IN 
                    SELECT t.contour
                    FROM public.territoires t
                    JOIN public.profiles p ON t.utilisateur_id = p.id
                    WHERE t.utilisateur_id <> p_user_id 
                      AND p.guilde_id = v_guilde_id
                      AND ST_Intersects(t.contour, v_geom)
                LOOP
                    v_geom := ST_Difference(v_geom, v_team_terr.contour);
                    v_geom := ST_CollectionExtract(v_geom, 3);
                END LOOP;
            END IF;

            -- C. FUSION / MISE À JOUR : Fusionner avec le territoire existant du joueur
            SELECT id, contour INTO v_existing_id, v_existing_contour
            FROM public.territoires
            WHERE utilisateur_id = p_user_id;

            IF v_existing_id IS NOT NULL THEN
                v_geom := ST_Union(v_existing_contour, v_geom);
                v_geom := ST_CollectionExtract(v_geom, 3);

                IF NOT ST_IsValid(v_geom) THEN
                    v_geom := ST_MakeValid(v_geom);
                END IF;

                IF NOT ST_IsEmpty(v_geom) THEN
                    v_superficie := ST_Area(v_geom::geography);
                    SELECT array_agg(ST_X(geom) || ' ' || ST_Y(geom)) INTO v_new_points
                    FROM ST_DumpPoints(v_geom);

                    UPDATE public.territoires 
                    SET contour = v_geom,
                        superficie_m2 = v_superficie,
                        points = v_new_points,
                        derniere_mise_a_jour = now()
                    WHERE id = v_existing_id;
                ELSE
                    DELETE FROM public.territoires WHERE id = v_existing_id;
                END IF;
            ELSE
                IF NOT ST_IsEmpty(v_geom) THEN
                    v_superficie := ST_Area(v_geom::geography);
                    SELECT array_agg(ST_X(geom) || ' ' || ST_Y(geom)) INTO v_new_points
                    FROM ST_DumpPoints(v_geom);

                    INSERT INTO public.territoires (utilisateur_id, guilde_id, contour, superficie_m2, points)
                    VALUES (p_user_id, v_guilde_id, v_geom, v_superficie, v_new_points);
                END IF;
            END IF;

        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'Échec de la validation/conquête géométrique : %', SQLERRM;
        END;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Restriction explicite d'accès à la fonction enregistrer_course
REVOKE EXECUTE ON FUNCTION public.enregistrer_course(uuid, timestamptz, timestamptz, float, float, boolean, text[], float, float, float, float, float, float) FROM public;
GRANT EXECUTE ON FUNCTION public.enregistrer_course(uuid, timestamptz, timestamptz, float, float, boolean, text[], float, float, float, float, float, float) TO authenticated, service_role;

-- B. Récupération des territoires par Viewport (Bounding Box)
DROP FUNCTION IF EXISTS public.get_territoires_in_bbox(double precision, double precision, double precision, double precision);

CREATE OR REPLACE FUNCTION public.get_territoires_in_bbox(
    min_lng float,
    min_lat float,
    max_lng float,
    max_lat float
)
RETURNS TABLE (
    id uuid,
    utilisateur_id uuid,
    pseudonyme text,
    empire_color text,
    avatar_url text,
    points text[],
    latitude float,
    longitude float,
    guilde_nom text,
    guilde_couleur text,
    total_area_m2 float
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.utilisateur_id,
        p.pseudonyme,
        p.empire_color,
        p.avatar_url,
        t.points,
        CASE WHEN p.share_location THEN p.latitude ELSE null END as latitude,
        CASE WHEN p.share_location THEN p.longitude ELSE null END as longitude,
        g.nom as guilde_nom,
        g.couleur_hex as guilde_couleur,
        p.total_area_m2
    FROM public.territoires t
    JOIN public.profiles p ON t.utilisateur_id = p.id
    LEFT JOIN public.guildes g ON p.guilde_id = g.id
    WHERE t.contour && ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326)
      AND (p.share_location = true OR p.id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;

-- C. Suggestions d'amis par proximité géographique
DROP FUNCTION IF EXISTS public.suggerer_amis_proximite(uuid, int);
DROP FUNCTION IF EXISTS public.suggerer_amis_proximite(uuid, double precision);

CREATE OR REPLACE FUNCTION public.suggerer_amis_proximite(
    p_utilisateur_id uuid,
    p_max_distance_meters double precision DEFAULT 50000
)
RETURNS TABLE (
    id uuid,
    pseudonyme text,
    avatar_url text,
    empire_color text,
    distance_meters float
) AS $$
DECLARE
    v_user_geom geometry;
BEGIN
    SELECT ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) INTO v_user_geom
    FROM public.profiles
    WHERE id = p_utilisateur_id AND longitude IS NOT NULL AND latitude IS NOT NULL;

    IF v_user_geom IS NULL THEN
        -- Si l'utilisateur n'a pas de géolocalisation, on suggère par taille d'empire décroissante
        RETURN QUERY
        SELECT 
            p.id,
            p.pseudonyme,
            p.avatar_url,
            p.empire_color,
            null::float as distance_meters
        FROM public.profiles p
        WHERE p.id <> p_utilisateur_id
          AND p.share_location = true
          AND NOT EXISTS (
              SELECT 1 FROM public.amis a
              WHERE (a.demandeur_id = p_utilisateur_id AND a.destinataire_id = p.id)
                 OR (a.demandeur_id = p.id AND a.destinataire_id = p_utilisateur_id)
          )
        ORDER BY p.total_area_m2 DESC
        LIMIT 10;
    ELSE
        RETURN QUERY
        SELECT 
            p.id,
            p.pseudonyme,
            p.avatar_url,
            p.empire_color,
            ST_Distance(
                ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
                v_user_geom::geography
            ) as distance_meters
        FROM public.profiles p
        WHERE p.id <> p_utilisateur_id
          AND p.share_location = true
          AND p.longitude IS NOT NULL 
          AND p.latitude IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM public.amis a
              WHERE (a.demandeur_id = p_utilisateur_id AND a.destinataire_id = p.id)
                 OR (a.demandeur_id = p.id AND a.destinataire_id = p_utilisateur_id)
          )
          AND ST_DWithin(
              ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4327)::geography,
              v_user_geom::geography,
              p_max_distance_meters
          )
        ORDER BY distance_meters ASC
        LIMIT 10;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;

-- ==========================================
-- VUES DU CLASSEMENT (LEADERBOARD)
-- ==========================================

-- A. Classement individuel des joueurs (avec tag)
DROP VIEW IF EXISTS public.leaderboard;
CREATE OR REPLACE VIEW public.leaderboard 
WITH (security_invoker = on) AS
SELECT 
  p.id,
  p.pseudonyme,
  p.tag,
  p.empire_color,
  p.avatar_url,
  p.grade,
  CASE WHEN p.share_location THEN p.latitude ELSE null END as latitude,
  CASE WHEN p.share_location THEN p.longitude ELSE null END as longitude,
  p.total_area_m2,
  g.nom as guilde_nom,
  g.couleur_hex as guilde_couleur,
  g.tag as guilde_tag
FROM public.profiles p
LEFT JOIN public.guildes g ON p.guilde_id = g.id
ORDER BY p.total_area_m2 DESC;

-- B. Classement général des clans (avec tag)
DROP VIEW IF EXISTS public.clan_leaderboard;
CREATE OR REPLACE VIEW public.clan_leaderboard 
WITH (security_invoker = on) AS
SELECT 
    g.id,
    g.nom,
    g.tag,
    g.couleur_hex,
    g.avatar_url,
    COALESCE(SUM(p.total_area_m2), 0.0) as total_area_m2,
    COUNT(p.id) as membre_count
FROM public.guildes g
LEFT JOIN public.profiles p ON p.guilde_id = g.id
GROUP BY g.id, g.nom, g.tag, g.couleur_hex, g.avatar_url
ORDER BY total_area_m2 DESC;

-- ==========================================
-- SCRIPT OPTIONNEL DE NETTOYAGE ET FUSION INITIALE DES DOUBLONS
-- ==========================================
DO $$
DECLARE
    r record;
    v_merged_geom geometry;
    v_total_area float;
    v_new_points text[];
BEGIN
    FOR r IN SELECT utilisateur_id, guilde_id FROM public.territoires GROUP BY utilisateur_id, guilde_id LOOP
        SELECT ST_Union(contour) INTO v_merged_geom
        FROM public.territoires
        WHERE utilisateur_id = r.utilisateur_id;

        IF v_merged_geom IS NOT NULL THEN
            v_merged_geom := ST_CollectionExtract(v_merged_geom, 3);
            
            IF NOT ST_IsEmpty(v_merged_geom) THEN
                v_total_area := ST_Area(v_merged_geom::geography);
                SELECT array_agg(ST_X(geom) || ' ' || ST_Y(geom)) INTO v_new_points
                FROM ST_DumpPoints(v_merged_geom);

                DELETE FROM public.territoires WHERE utilisateur_id = r.utilisateur_id;

                INSERT INTO public.territoires (utilisateur_id, guilde_id, contour, superficie_m2, points)
                VALUES (r.utilisateur_id, r.guilde_id, v_merged_geom, v_total_area, v_new_points);
            END IF;
        END IF;
    END LOOP;
END;
$$;

-- Recalculer les superficies globales mises en cache
UPDATE public.profiles p
SET total_area_m2 = COALESCE(
    (SELECT SUM(superficie_m2) FROM public.territoires t WHERE t.utilisateur_id = p.id),
    0.0
);

-- ==========================================
-- SÉCURISATION ET ACCÈS STOCKAGE AVATARS
-- ==========================================

-- Création du bucket Images si inexistant
INSERT INTO storage.buckets (id, name, public)
VALUES ('Images', 'Images', true)
ON CONFLICT (id) DO NOTHING;

-- Nettoyer les politiques existantes sur storage.objects pour éviter les conflits à la ré-application
DROP POLICY IF EXISTS "Images Public Access" ON storage.objects;
DROP POLICY IF EXISTS "Users can upload their own avatar" ON storage.objects;
DROP POLICY IF EXISTS "Users can update their own avatar" ON storage.objects;
DROP POLICY IF EXISTS "Chefs can upload guild avatar" ON storage.objects;
DROP POLICY IF EXISTS "Chefs can update guild avatar" ON storage.objects;

-- Politiques RLS pour storage.objects dans le bucket Images
CREATE POLICY "Images Public Access" ON storage.objects FOR SELECT
USING (bucket_id = 'Images');

CREATE POLICY "Users can upload their own avatar" ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'Images' AND name = auth.uid()::text || '.jpg');

CREATE POLICY "Users can update their own avatar" ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'Images' AND name = auth.uid()::text || '.jpg')
WITH CHECK (bucket_id = 'Images' AND name = auth.uid()::text || '.jpg');

CREATE POLICY "Chefs can upload guild avatar" ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'Images' AND name LIKE 'guild_%' AND (
    SELECT chef_id::text FROM public.guildes WHERE id::text = substring(name from 'guild_(.*)\.jpg')
) = auth.uid()::text);

CREATE POLICY "Chefs can update guild avatar" ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'Images' AND name LIKE 'guild_%' AND (
    SELECT chef_id::text FROM public.guildes WHERE id::text = substring(name from 'guild_(.*)\.jpg')
) = auth.uid()::text)
WITH CHECK (bucket_id = 'Images' AND name LIKE 'guild_%' AND (
    SELECT chef_id::text FROM public.guildes WHERE id::text = substring(name from 'guild_(.*)\.jpg')
) = auth.uid()::text);

-- ==========================================
-- FONCTIONS RPC HIÉRARCHIE DE CLAN
-- ==========================================

-- Fonction pour promouvoir un membre (Chef uniquement)
CREATE OR REPLACE FUNCTION public.promouvoir_membre(
    p_target_id uuid,
    p_new_grade text
)
RETURNS void AS $$
DECLARE
    v_caller_grade text;
    v_caller_guild uuid;
    v_target_guild uuid;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Non authentifié.';
    END IF;

    IF p_new_grade NOT IN ('adjoint', 'membre') THEN
        RAISE EXCEPTION 'Grade invalide. Valeurs autorisées : adjoint, membre.';
    END IF;

    SELECT grade, guilde_id INTO v_caller_grade, v_caller_guild
    FROM public.profiles WHERE id = auth.uid();

    SELECT guilde_id INTO v_target_guild
    FROM public.profiles WHERE id = p_target_id;

    IF v_caller_grade <> 'chef' THEN
        RAISE EXCEPTION 'Seul le chef peut promouvoir ou rétrograder des membres.';
    END IF;

    IF v_caller_guild IS NULL OR v_target_guild IS NULL OR v_caller_guild <> v_target_guild THEN
        RAISE EXCEPTION 'Le joueur cible n''appartient pas au même clan.';
    END IF;

    UPDATE public.profiles SET grade = p_new_grade WHERE id = p_target_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Fonction pour expulser un membre (N+1 peut expulser N-1)
CREATE OR REPLACE FUNCTION public.expulser_membre(p_target_id uuid)
RETURNS void AS $$
DECLARE
    v_caller_grade text;
    v_caller_guild uuid;
    v_target_grade text;
    v_target_guild uuid;
    v_caller_rank int;
    v_target_rank int;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Non authentifié.';
    END IF;

    SELECT grade, guilde_id INTO v_caller_grade, v_caller_guild
    FROM public.profiles WHERE id = auth.uid();

    SELECT grade, guilde_id INTO v_target_grade, v_target_guild
    FROM public.profiles WHERE id = p_target_id;

    IF v_caller_guild IS NULL OR v_target_guild IS NULL OR v_caller_guild <> v_target_guild THEN
        RAISE EXCEPTION 'Le joueur cible n''appartient pas au même clan.';
    END IF;

    -- Hiérarchie : chef=3, adjoint=2, membre=1
    v_caller_rank := CASE v_caller_grade WHEN 'chef' THEN 3 WHEN 'adjoint' THEN 2 ELSE 1 END;
    v_target_rank := CASE v_target_grade WHEN 'chef' THEN 3 WHEN 'adjoint' THEN 2 ELSE 1 END;

    IF v_caller_rank <= v_target_rank THEN
        RAISE EXCEPTION 'Vous ne pouvez expulser qu''un membre de rang inférieur au vôtre.';
    END IF;

    UPDATE public.profiles SET guilde_id = NULL, grade = 'membre' WHERE id = p_target_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Fonction pour dissoudre un clan (Chef uniquement)
CREATE OR REPLACE FUNCTION public.dissoudre_clan(p_guild_id uuid)
RETURNS void AS $$
DECLARE
    v_caller_grade text;
    v_caller_guild uuid;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Non authentifié.';
    END IF;

    SELECT grade, guilde_id INTO v_caller_grade, v_caller_guild
    FROM public.profiles WHERE id = auth.uid();

    IF v_caller_grade <> 'chef' OR v_caller_guild <> p_guild_id THEN
        RAISE EXCEPTION 'Seul le chef de ce clan peut le dissoudre.';
    END IF;

    -- Retirer tous les membres du clan
    UPDATE public.profiles SET guilde_id = NULL, grade = 'membre'
    WHERE guilde_id = p_guild_id;

    -- Dissocier les territoires
    UPDATE public.territoires SET guilde_id = NULL WHERE guilde_id = p_guild_id;

    -- Supprimer le clan
    DELETE FROM public.guildes WHERE id = p_guild_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Fonction pour renommer un clan (Chef uniquement)
CREATE OR REPLACE FUNCTION public.renommer_clan(p_guild_id uuid, p_new_name text)
RETURNS void AS $$
DECLARE
    v_caller_grade text;
    v_caller_guild uuid;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Non authentifié.';
    END IF;

    SELECT grade, guilde_id INTO v_caller_grade, v_caller_guild
    FROM public.profiles WHERE id = auth.uid();

    IF v_caller_grade <> 'chef' OR v_caller_guild <> p_guild_id THEN
        RAISE EXCEPTION 'Seul le chef de ce clan peut le renommer.';
    END IF;

    UPDATE public.guildes SET nom = p_new_name WHERE id = p_guild_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ==========================================
-- FONCTION GEOJSON POUR TERRITOIRES (web-admin)
-- ==========================================

DROP FUNCTION IF EXISTS public.get_territoires_geojson();

CREATE OR REPLACE FUNCTION public.get_territoires_geojson()
RETURNS TABLE (
    id uuid,
    utilisateur_id uuid,
    pseudonyme text,
    tag text,
    empire_color text,
    guilde_nom text,
    guilde_couleur text,
    guilde_tag text,
    superficie_m2 float,
    geojson text
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.utilisateur_id,
        p.pseudonyme,
        p.tag,
        p.empire_color,
        g.nom as guilde_nom,
        g.couleur_hex as guilde_couleur,
        g.tag as guilde_tag,
        t.superficie_m2,
        ST_AsGeoJSON(t.contour)::text as geojson
    FROM public.territoires t
    JOIN public.profiles p ON t.utilisateur_id = p.id
    LEFT JOIN public.guildes g ON t.guilde_id = g.id;
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;

-- ==========================================
-- MIGRATION : Rétro-génération des tags et grades
-- ==========================================

-- Assigner un tag à tous les profils existants sans tag
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT id FROM public.profiles WHERE tag IS NULL LOOP
        UPDATE public.profiles SET tag = public.generate_unique_tag('profiles') WHERE id = r.id;
    END LOOP;
END;
$$;

-- Assigner un tag à toutes les guildes existantes sans tag
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT id FROM public.guildes WHERE tag IS NULL LOOP
        UPDATE public.guildes SET tag = public.generate_unique_tag('guildes') WHERE id = r.id;
    END LOOP;
END;
$$;

-- Assigner le grade 'chef' au chef_id de chaque guilde
UPDATE public.profiles p
SET grade = 'chef'
FROM public.guildes g
WHERE g.chef_id = p.id AND p.guilde_id = g.id;

