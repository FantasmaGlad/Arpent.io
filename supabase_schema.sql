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
    date_inscription timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    max_area_m2 float DEFAULT 0.0 NOT NULL,
    area_lost_m2 float DEFAULT 0.0 NOT NULL,
    xp integer DEFAULT 0 NOT NULL,
    level integer DEFAULT 1 NOT NULL,
    loop_count integer DEFAULT 0 NOT NULL,
    max_loop_distance_km float DEFAULT 0.0 NOT NULL,
    total_steps integer DEFAULT 0,
    average_cadence float DEFAULT 0.0
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
    points_gps_count integer DEFAULT 0,
    nom text,
    legende text,
    superficie_conquise float DEFAULT 0.0,
    total_steps integer DEFAULT 0,
    average_cadence integer DEFAULT 0,
    image_url text,
    CONSTRAINT courses_user_date_debut_unique UNIQUE (utilisateur_id, date_debut)
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
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    timestamp_gps timestamp with time zone,
    accel_x double precision,
    accel_y double precision,
    accel_z double precision,
    pas integer,
    cadence integer
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

-- 8. Table des réactions de courses
CREATE TABLE IF NOT EXISTS public.course_reactions (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    course_id uuid REFERENCES public.courses(id) ON DELETE CASCADE NOT NULL,
    utilisateur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    type_reaction text CHECK (type_reaction IN ('baamix')) NOT NULL,
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE (course_id, utilisateur_id, type_reaction)
);

-- Table des invitations de guilde/clan
CREATE TABLE IF NOT EXISTS public.guilde_invitations (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    guilde_id uuid REFERENCES public.guildes(id) ON DELETE CASCADE NOT NULL,
    invitant_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    invite_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    statut text DEFAULT 'en_attente' NOT NULL CHECK (statut IN ('en_attente', 'accepte', 'refuse')),
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE (guilde_id, invite_id)
);

CREATE INDEX IF NOT EXISTS course_reactions_course_id_idx ON public.course_reactions(course_id);

-- 9. Table des commentaires de courses
CREATE TABLE IF NOT EXISTS public.course_commentaires (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    course_id uuid REFERENCES public.courses(id) ON DELETE CASCADE NOT NULL,
    utilisateur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    contenu text NOT NULL,
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS course_commentaires_course_id_idx ON public.course_commentaires(course_id);

-- Indexation sur les clés étrangères pivots pour la performance
CREATE INDEX IF NOT EXISTS profiles_guilde_id_idx ON public.profiles(guilde_id);
CREATE INDEX IF NOT EXISTS territoires_utilisateur_id_idx ON public.territoires(utilisateur_id);
CREATE INDEX IF NOT EXISTS courses_utilisateur_id_idx ON public.courses(utilisateur_id);
CREATE INDEX IF NOT EXISTS amis_demandeur_id_idx ON public.amis(demandeur_id);
CREATE INDEX IF NOT EXISTS amis_destinataire_id_idx ON public.amis(destinataire_id);

-- Nettoyer les doublons potentiels dans la table courses avant d'appliquer la contrainte d'unicité
DELETE FROM public.courses c1
USING public.courses c2
WHERE c1.id > c2.id
  AND c1.utilisateur_id = c2.utilisateur_id
  AND c1.date_debut = c2.date_debut;

-- Forcer l'ajout de la contrainte unique sur l'historique des courses
ALTER TABLE public.courses DROP CONSTRAINT IF EXISTS courses_user_date_debut_unique;
ALTER TABLE public.courses ADD CONSTRAINT courses_user_date_debut_unique UNIQUE (utilisateur_id, date_debut);

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
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS max_area_m2 float DEFAULT 0.0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS area_lost_m2 float DEFAULT 0.0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS xp integer DEFAULT 0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS level integer DEFAULT 1 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS loop_count integer DEFAULT 0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS max_loop_distance_km float DEFAULT 0.0 NOT NULL;

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
ALTER TABLE public.course_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_commentaires ENABLE ROW LEVEL SECURITY;


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
CREATE POLICY "Tout le monde peut voir les territoires" ON public.territoires 
FOR SELECT USING (true);

DROP POLICY IF EXISTS "Les utilisateurs peuvent insérer/modifier leurs propres territoires" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent insérer/modifier leurs propres territoires" ON public.territoires FOR INSERT WITH CHECK (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les utilisateurs peuvent modifier leurs propres territoires" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent modifier leurs propres territoires" ON public.territoires FOR UPDATE USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Les utilisateurs peuvent supprimer leurs propres territoires" ON public.territoires;
CREATE POLICY "Les utilisateurs peuvent supprimer leurs propres territoires" ON public.territoires FOR DELETE USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

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

DROP POLICY IF EXISTS "Les utilisateurs peuvent supprimer leurs propres points gps" ON public.points_gps;
CREATE POLICY "Les utilisateurs peuvent supprimer leurs propres points gps" ON public.points_gps
FOR DELETE USING (
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
    FOR DELETE USING (auth.uid() = demandeur_id OR auth.uid() = destinataire_id OR public.is_admin(auth.uid()));

-- Politiques Invitations de Guilde
ALTER TABLE public.guilde_invitations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Lecture des invitations de guilde" ON public.guilde_invitations;
CREATE POLICY "Lecture des invitations de guilde" ON public.guilde_invitations
    FOR SELECT USING (
        auth.uid() = invite_id 
        OR auth.uid() = invitant_id 
        OR (SELECT guilde_id FROM public.profiles WHERE id = auth.uid()) = guilde_id
    );

DROP POLICY IF EXISTS "Insertion des invitations par chef/adjoint" ON public.guilde_invitations;
CREATE POLICY "Insertion des invitations par chef/adjoint" ON public.guilde_invitations
    FOR INSERT WITH CHECK (
        auth.uid() = invitant_id 
        AND (SELECT grade FROM public.profiles WHERE id = auth.uid()) IN ('chef', 'adjoint')
        AND (SELECT guilde_id FROM public.profiles WHERE id = auth.uid()) = guilde_id
    );

DROP POLICY IF EXISTS "Modification des invitations par l'invite" ON public.guilde_invitations;
CREATE POLICY "Modification des invitations par l'invite" ON public.guilde_invitations
    FOR UPDATE USING (
        auth.uid() = invite_id
    );

DROP POLICY IF EXISTS "Suppression des invitations par l'invitant ou chef/adjoint" ON public.guilde_invitations;
CREATE POLICY "Suppression des invitations par l'invitant ou chef/adjoint" ON public.guilde_invitations
    FOR DELETE USING (
        auth.uid() = invitant_id
        OR ((SELECT grade FROM public.profiles WHERE id = auth.uid()) IN ('chef', 'adjoint')
            AND (SELECT guilde_id FROM public.profiles WHERE id = auth.uid()) = guilde_id)
    );

-- Politiques Reactions de Courses
DROP POLICY IF EXISTS "Les utilisateurs authentifiés peuvent voir les réactions" ON public.course_reactions;
CREATE POLICY "Les utilisateurs authentifiés peuvent voir les réactions" ON public.course_reactions
    FOR SELECT USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Les utilisateurs peuvent réagir aux courses" ON public.course_reactions;
CREATE POLICY "Les utilisateurs peuvent réagir aux courses" ON public.course_reactions
    FOR INSERT WITH CHECK (auth.uid() = utilisateur_id);

DROP POLICY IF EXISTS "Les utilisateurs peuvent supprimer leur propre réaction" ON public.course_reactions;
CREATE POLICY "Les utilisateurs peuvent supprimer leur propre réaction" ON public.course_reactions
    FOR DELETE USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

-- Politiques Commentaires de Courses
DROP POLICY IF EXISTS "Les utilisateurs authentifiés peuvent voir les commentaires" ON public.course_commentaires;
CREATE POLICY "Les utilisateurs authentifiés peuvent voir les commentaires" ON public.course_commentaires
    FOR SELECT USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Les utilisateurs peuvent commenter les courses" ON public.course_commentaires;
CREATE POLICY "Les utilisateurs peuvent commenter les courses" ON public.course_commentaires
    FOR INSERT WITH CHECK (auth.uid() = utilisateur_id);

DROP POLICY IF EXISTS "Les utilisateurs peuvent supprimer leur propre commentaire" ON public.course_commentaires;
CREATE POLICY "Les utilisateurs peuvent supprimer leur propre commentaire" ON public.course_commentaires
    FOR DELETE USING (auth.uid() = utilisateur_id OR public.is_admin(auth.uid()));

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

-- Helper function to calculate level based on cumulative XP
CREATE OR REPLACE FUNCTION public.xp_to_level(p_xp integer)
RETURNS integer AS $$
DECLARE
    v_level integer := 1;
    v_sum_xp float := 0.0;
    v_step_xp float := 100.0;
BEGIN
    IF p_xp < 0 THEN
        RETURN 1;
    END IF;
    LOOP
        v_sum_xp := v_sum_xp + v_step_xp;
        IF p_xp < v_sum_xp THEN
            RETURN v_level;
        END IF;
        v_level := v_level + 1;
        v_step_xp := v_step_xp * 1.15;
    END LOOP;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- B. Calcul en temps réel de superficie dans le profil (avec gestion rollback)
CREATE OR REPLACE FUNCTION public.update_profile_cached_area()
RETURNS trigger AS $$
DECLARE
    v_gain float;
    v_loss float;
    v_is_rollback boolean := COALESCE(current_setting('app.rollback_course_deletion', true) = 'true', false);
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.profiles 
        SET total_area_m2 = total_area_m2 + new.superficie_m2,
            all_time_area_m2 = all_time_area_m2 + new.superficie_m2,
            max_area_m2 = GREATEST(max_area_m2, total_area_m2 + new.superficie_m2)
        WHERE id = new.utilisateur_id;
    ELSIF (TG_OP = 'DELETE') THEN
        IF v_is_rollback THEN
            UPDATE public.profiles 
            SET total_area_m2 = GREATEST(0.0, total_area_m2 - old.superficie_m2),
                all_time_area_m2 = GREATEST(0.0, all_time_area_m2 - old.superficie_m2),
                max_area_m2 = GREATEST(0.0, max_area_m2 - old.superficie_m2)
            WHERE id = old.utilisateur_id;
        ELSE
            UPDATE public.profiles 
            SET total_area_m2 = GREATEST(0.0, total_area_m2 - old.superficie_m2),
                area_lost_m2 = area_lost_m2 + old.superficie_m2
            WHERE id = old.utilisateur_id;
        END IF;
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (new.superficie_m2 > old.superficie_m2) THEN
            v_gain := new.superficie_m2 - old.superficie_m2;
            UPDATE public.profiles 
            SET total_area_m2 = total_area_m2 + v_gain,
                all_time_area_m2 = all_time_area_m2 + v_gain,
                max_area_m2 = GREATEST(max_area_m2, total_area_m2 + v_gain)
            WHERE id = new.utilisateur_id;
        ELSIF (new.superficie_m2 < old.superficie_m2) THEN
            v_loss := old.superficie_m2 - new.superficie_m2;
            IF v_is_rollback THEN
                UPDATE public.profiles 
                SET total_area_m2 = GREATEST(0.0, total_area_m2 - v_loss),
                    all_time_area_m2 = GREATEST(0.0, all_time_area_m2 - v_loss),
                    max_area_m2 = GREATEST(0.0, max_area_m2 - v_loss)
                WHERE id = new.utilisateur_id;
            ELSE
                UPDATE public.profiles 
                SET total_area_m2 = GREATEST(0.0, total_area_m2 - v_loss),
                    area_lost_m2 = area_lost_m2 + v_loss
                WHERE id = new.utilisateur_id;
            END IF;
        END IF;
    END IF;
    RETURN null;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_territoire_changed ON public.territoires;
CREATE TRIGGER on_territoire_changed
    AFTER INSERT OR UPDATE OR DELETE ON public.territoires
    FOR EACH ROW EXECUTE PROCEDURE public.update_profile_cached_area();

-- B.2 Retrait de la portion de territoire lors de la suppression d'une course
CREATE OR REPLACE FUNCTION public.delete_course_territory_portion()
RETURNS trigger AS $$
DECLARE
    v_points text[];
    v_wkt text;
    v_course_geom geometry;
    v_existing_id uuid;
    v_existing_geom geometry;
    v_diff_geom geometry;
    v_new_points text[];
    v_new_area float;
BEGIN
    IF OLD.est_bouclee AND OLD.superficie_conquise > 0.0 THEN
        SELECT array_agg(longitude || ' ' || latitude ORDER BY timestamp_gps ASC, date_creation ASC)
        INTO v_points
        FROM public.points_gps
        WHERE course_id = OLD.id;

        IF v_points IS NOT NULL AND array_length(v_points, 1) >= 3 THEN
            IF v_points[1] <> v_points[array_length(v_points, 1)] THEN
                v_points := array_append(v_points, v_points[1]);
            END IF;

            v_wkt := 'POLYGON((' || array_to_string(v_points, ', ') || '))';
            
            BEGIN
                v_course_geom := ST_GeomFromText(v_wkt, 4326);
                IF NOT ST_IsValid(v_course_geom) THEN
                    v_course_geom := ST_MakeValid(v_course_geom);
                END IF;
                v_course_geom := ST_CollectionExtract(v_course_geom, 3);

                SELECT id, contour INTO v_existing_id, v_existing_geom
                FROM public.territoires
                WHERE utilisateur_id = OLD.utilisateur_id;

                IF v_existing_id IS NOT NULL AND v_existing_geom IS NOT NULL THEN
                    PERFORM set_config('app.rollback_course_deletion', 'true', true);

                    v_diff_geom := ST_Difference(v_existing_geom, v_course_geom);
                    v_diff_geom := ST_CollectionExtract(v_diff_geom, 3);

                    IF ST_IsEmpty(v_diff_geom) OR v_diff_geom IS NULL THEN
                        DELETE FROM public.territoires WHERE id = v_existing_id;
                    ELSE
                        SELECT array_agg(ST_X(geom) || ' ' || ST_Y(geom)) INTO v_new_points
                        FROM ST_DumpPoints(v_diff_geom);

                        v_new_area := ST_Area(v_diff_geom::geography);

                        UPDATE public.territoires
                        SET contour = v_diff_geom,
                            superficie_m2 = v_new_area,
                            points = v_new_points,
                            derniere_mise_a_jour = now()
                        WHERE id = v_existing_id;
                    END IF;
                END IF;
            EXCEPTION WHEN OTHERS THEN
                RAISE WARNING 'Erreur de mise à jour du territoire lors de la suppression de course : %', SQLERRM;
            END;
        END IF;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_course_before_delete ON public.courses;
CREATE TRIGGER on_course_before_delete
    BEFORE DELETE ON public.courses
    FOR EACH ROW EXECUTE PROCEDURE public.delete_course_territory_portion();

-- C. Calcul de la série d'activité consécutive (streak)
CREATE OR REPLACE FUNCTION public.get_user_streak(p_user_id uuid)
RETURNS integer AS $$
DECLARE
    v_streak integer := 0;
    v_date date;
    v_expected_date date := current_date;
    v_has_activity boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM public.courses 
        WHERE utilisateur_id = p_user_id 
          AND (date_debut AT TIME ZONE 'UTC')::date = current_date
    ) INTO v_has_activity;

    IF NOT v_has_activity THEN
        v_expected_date := current_date - 1;
    END IF;

    LOOP
        SELECT EXISTS (
            SELECT 1 FROM public.courses 
            WHERE utilisateur_id = p_user_id 
              AND (date_debut AT TIME ZONE 'UTC')::date = v_expected_date
        ) INTO v_has_activity;

        IF v_has_activity THEN
            v_streak := v_streak + 1;
            v_expected_date := v_expected_date - 1;
        ELSE
            EXIT;
        END IF;
    END LOOP;

    RETURN v_streak;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- D. Gestion de l'XP et du niveau sur modification de course
CREATE OR REPLACE FUNCTION public.update_profile_stats_on_course()
RETURNS trigger AS $$
DECLARE
    v_xp_gain integer;
    v_xp_loss integer;
BEGIN
    IF (TG_OP = 'INSERT') THEN
        v_xp_gain := FLOOR(80.0 * new.distance_totale + COALESCE(new.superficie_conquise, 0.0) / 50000.0);
        
        UPDATE public.profiles
        SET level = public.xp_to_level(xp + v_xp_gain),
            xp = xp + v_xp_gain,
            loop_count = loop_count + (CASE WHEN new.est_bouclee THEN 1 ELSE 0 END),
            max_loop_distance_km = CASE 
                WHEN new.est_bouclee THEN GREATEST(max_loop_distance_km, new.distance_totale)
                ELSE max_loop_distance_km 
            END,
            total_steps = total_steps + COALESCE(new.total_steps, 0),
            average_cadence = COALESCE((
                SELECT AVG(average_cadence) 
                FROM public.courses 
                WHERE utilisateur_id = new.utilisateur_id AND average_cadence > 0
            ), 0.0)
        WHERE id = new.utilisateur_id;

    ELSIF (TG_OP = 'DELETE') THEN
        v_xp_loss := FLOOR(80.0 * old.distance_totale + COALESCE(old.superficie_conquise, 0.0) / 50000.0);
        
        UPDATE public.profiles
        SET level = public.xp_to_level(GREATEST(0, xp - v_xp_loss)),
            xp = GREATEST(0, xp - v_xp_loss),
            loop_count = GREATEST(0, loop_count - (CASE WHEN old.est_bouclee THEN 1 ELSE 0 END)),
            total_steps = GREATEST(0, total_steps - COALESCE(old.total_steps, 0)),
            average_cadence = COALESCE((
                SELECT AVG(average_cadence) 
                FROM public.courses 
                WHERE utilisateur_id = old.utilisateur_id AND id <> old.id AND average_cadence > 0
            ), 0.0)
        WHERE id = old.utilisateur_id;

        IF old.est_bouclee THEN
            UPDATE public.profiles
            SET max_loop_distance_km = COALESCE((
                SELECT MAX(distance_totale) 
                FROM public.courses 
                WHERE utilisateur_id = old.utilisateur_id AND est_bouclee = true
            ), 0.0)
            WHERE id = old.utilisateur_id;
        END IF;
    END IF;
    RETURN null;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_course_changed ON public.courses;
CREATE TRIGGER on_course_changed
    AFTER INSERT OR DELETE ON public.courses
    FOR EACH ROW EXECUTE PROCEDURE public.update_profile_stats_on_course();

-- ==========================================
-- FONCTIONS DU MOTEUR DU JEU (RPC)
-- ==========================================

-- A. Enregistrement de course et fusion MultiPolygon avec Conquête (SECURITY DEFINER + auth.uid() check)
DROP FUNCTION IF EXISTS public.enregistrer_course(uuid, timestamp with time zone, timestamp with time zone, float, float, boolean, text[], float, float, float, float, float, float);

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
    p_denivele_negatif float DEFAULT 0.0,
    p_total_steps integer DEFAULT 0,
    p_average_cadence integer DEFAULT 0,
    p_nom_course text DEFAULT NULL,
    p_legende text DEFAULT NULL,
    p_points_details jsonb DEFAULT NULL,
    p_image_url text DEFAULT NULL
)
RETURNS void AS $$
DECLARE
    v_course_id uuid;
    v_wkt text;
    v_geom geometry;
    v_superficie float := 0.0;
    v_pt_text text;
    v_parts text[];
    v_guilde_id uuid;
    v_enemy_terr record;
    v_team_terr record;
    v_diff_geom geometry;
    v_enemy_pts text[];
    v_new_points text[];
    v_existing_id uuid;
    v_existing_contour geometry;

    -- Variables pour calculs anti-triche
    v_calculated_distance float := 0.0;
    v_calculated_duration float := 0.0;
    v_calculated_distance_km float := 0.0;
BEGIN
    -- Contrôle d'accès strict
    IF auth.uid() IS NULL OR p_user_id <> auth.uid() THEN
        RAISE EXCEPTION 'Non autorisé : ID utilisateur invalide ou non authentifié.';
    END IF;

    -- Contrôle anti-triche serveur (vitesse réelle, distance, durée et instantanée)
    IF p_points_details IS NOT NULL AND jsonb_array_length(p_points_details) >= 2 THEN
        SELECT COALESCE(ST_Length(ST_MakeLine(array_agg(ST_SetSRID(ST_MakePoint((val->>'longitude')::double precision, (val->>'latitude')::double precision), 4326) ORDER BY ord))::geography), 0.0)
        INTO v_calculated_distance
        FROM jsonb_array_elements(p_points_details) WITH ORDINALITY AS f(val, ord);
    ELSIF p_points IS NOT NULL AND array_length(p_points, 1) >= 2 THEN
        SELECT COALESCE(ST_Length(ST_MakeLine(array_agg(ST_SetSRID(ST_MakePoint(split_part(trim(pt), ' ', 1)::double precision, split_part(trim(pt), ' ', 2)::double precision), 4326) ORDER BY ord))::geography), 0.0)
        INTO v_calculated_distance
        FROM unnest(p_points) WITH ORDINALITY AS f(pt, ord);
    END IF;

    v_calculated_distance_km := v_calculated_distance / 1000.0;
    v_calculated_duration := EXTRACT(EPOCH FROM (p_date_fin - p_date_debut));

    -- Validation de l'écart de distance (max 15% de divergence si distance significative)
    IF v_calculated_distance_km > 0.05 AND ABS(p_distance_totale - v_calculated_distance_km) / v_calculated_distance_km > 0.15 THEN
        RAISE EXCEPTION 'Écart de distance trop important (anti-triche). Client: %, Serveur: %', p_distance_totale, v_calculated_distance_km;
    END IF;

    -- Validation de l'écart de durée (max 15% de divergence si durée significative)
    IF v_calculated_duration > 5.0 AND ABS(p_duree_secondes - v_calculated_duration) / v_calculated_duration > 0.15 THEN
        RAISE EXCEPTION 'Écart de durée trop important (anti-triche). Client: %, Serveur: %', p_duree_secondes, v_calculated_duration;
    END IF;

    -- Validation de la vitesse moyenne réelle (< 12 m/s, soit 43.2 km/h)
    IF v_calculated_duration > 0.0 AND (v_calculated_distance / v_calculated_duration) > 12.0 THEN
        RAISE EXCEPTION 'Vitesse moyenne réelle irréaliste (anti-triche). Vitesse: % m/s', (v_calculated_distance / v_calculated_duration);
    END IF;

    -- Validation de la vitesse instantanée dans les points détaillés (max 43.2 km/h)
    IF p_points_details IS NOT NULL AND jsonb_array_length(p_points_details) > 0 THEN
        IF EXISTS (
            SELECT 1 FROM jsonb_array_elements(p_points_details) AS val
            WHERE (val->>'speed')::double precision > 43.2
        ) THEN
            RAISE EXCEPTION 'Vitesse instantanée maximale dépassée (anti-triche).';
        END IF;
    END IF;

    -- Gestion de la boucle de territoire d'abord pour calculer la superficie
    IF p_est_bouclee AND (
        (p_points_details IS NOT NULL AND jsonb_array_length(p_points_details) >= 3) OR
        (p_points IS NOT NULL AND array_length(p_points, 1) >= 3)
    ) THEN
        -- Si les points textuels ne sont pas fournis, on peut essayer de les construire à partir de points_details
        IF (p_points IS NULL OR array_length(p_points, 1) < 3) AND p_points_details IS NOT NULL THEN
            SELECT array_agg((val->>'longitude') || ' ' || (val->>'latitude')) INTO p_points
            FROM jsonb_array_elements(p_points_details) AS val;
        END IF;

        v_wkt := 'POLYGON((' || array_to_string(p_points, ', ') || '))';
        BEGIN
            v_geom := ST_GeomFromText(v_wkt, 4326);
            IF NOT ST_IsValid(v_geom) THEN
                v_geom := ST_MakeValid(v_geom);
            END IF;
            v_geom := ST_CollectionExtract(v_geom, 3);

            -- Contrôle anti-triche isopérimétrique
            IF ST_Area(v_geom::geography) > ((p_distance_totale * 1000.0) ^ 2) / 12.56637 THEN
                RAISE EXCEPTION 'Géométrie de conquête invalide pour la distance fournie (anti-triche).';
            END IF;
            
            v_superficie := ST_Area(v_geom::geography);
        EXCEPTION WHEN OTHERS THEN
            v_superficie := 0.0;
        END;
    END IF;

    -- 1. Insérer la session de course
    INSERT INTO public.courses (
        utilisateur_id, date_debut, date_fin, distance_totale, duree_secondes, est_bouclee,
        vitesse_moyenne, vitesse_max, allure_moyenne, calories_estimees,
        denivele_positif, denivele_negatif, points_gps_count, nom, legende, superficie_conquise,
        total_steps, average_cadence, image_url
    )
    VALUES (
        p_user_id, p_date_debut, p_date_fin, p_distance_totale, p_duree_secondes, p_est_bouclee,
        p_vitesse_moyenne, p_vitesse_max, p_allure_moyenne, p_calories_estimees,
        p_denivele_positif, p_denivele_negatif, 
        COALESCE(
            CASE 
                WHEN p_points_details IS NOT NULL THEN jsonb_array_length(p_points_details)
                ELSE array_length(p_points, 1)
            END, 
            0
        ),
        p_nom_course, p_legende, v_superficie, p_total_steps, p_average_cadence, p_image_url
    )
    ON CONFLICT (utilisateur_id, date_debut) DO NOTHING
    RETURNING id INTO v_course_id;

    IF v_course_id IS NULL THEN
        RETURN;
    END IF;

    -- 2. Insérer les points GPS détaillés ou bruts
    IF p_points_details IS NOT NULL AND jsonb_array_length(p_points_details) > 0 THEN
        INSERT INTO public.points_gps (
            course_id, longitude, latitude, altitude, vitesse,
            timestamp_gps, accel_x, accel_y, accel_z, pas, cadence
        )
        SELECT 
            v_course_id,
            (val->>'longitude')::double precision,
            (val->>'latitude')::double precision,
            (val->>'altitude')::double precision,
            (val->>'speed')::double precision,
            to_timestamp((val->>'time')::double precision / 1000.0),
            (val->>'accelX')::double precision,
            (val->>'accelY')::double precision,
            (val->>'accelZ')::double precision,
            (val->>'steps')::integer,
            (val->>'cadence')::integer
        FROM jsonb_array_elements(p_points_details) AS val;
    ELSIF array_length(p_points, 1) > 0 THEN
        INSERT INTO public.points_gps (course_id, longitude, latitude)
        SELECT 
            v_course_id,
            split_part(trim(pt), ' ', 1)::double precision,
            split_part(trim(pt), ' ', 2)::double precision
        FROM unnest(p_points) AS pt;
    END IF;

    -- 3. Gestion de la conquête de territoire (soustraire ennemi et rajouter ami)
    IF p_est_bouclee AND v_superficie > 0.0 THEN
        SELECT guilde_id INTO v_guilde_id FROM public.profiles WHERE id = p_user_id;

        -- A. Verrouillage déterministe des profils impactés pour éviter les Deadlocks
        PERFORM id FROM public.profiles
        WHERE id IN (
            SELECT p_user_id
            UNION
            SELECT DISTINCT t.utilisateur_id
            FROM public.territoires t
            WHERE t.utilisateur_id <> p_user_id 
              AND ST_Intersects(t.contour, v_geom)
        )
        ORDER BY id
        FOR UPDATE;

        -- B. Verrouillage des territoires impactés
        PERFORM id FROM public.territoires
        WHERE id IN (
            SELECT id FROM public.territoires WHERE utilisateur_id = p_user_id
            UNION
            SELECT t.id
            FROM public.territoires t
            WHERE t.utilisateur_id <> p_user_id 
              AND ST_Intersects(t.contour, v_geom)
        )
        ORDER BY id
        FOR UPDATE;

        FOR v_enemy_terr IN 
            SELECT t.id, t.contour
            FROM public.territoires t
            WHERE t.utilisateur_id <> p_user_id 
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
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

REVOKE EXECUTE ON FUNCTION public.enregistrer_course(uuid, timestamp with time zone, timestamp with time zone, float, float, boolean, text[], float, float, float, float, float, float, integer, integer, text, text, jsonb, text) FROM public;
GRANT EXECUTE ON FUNCTION public.enregistrer_course(uuid, timestamp with time zone, timestamp with time zone, float, float, boolean, text[], float, float, float, float, float, float, integer, integer, text, text, jsonb, text) TO authenticated, service_role;

-- B. Récupération du Feed de Courses (Strava-like)
--    Amis + clan + local 50 km — tracé GPS, réactions et commentaires agrégés.
DROP FUNCTION IF EXISTS public.get_feed_courses(uuid);
CREATE OR REPLACE FUNCTION public.get_feed_courses(p_utilisateur_id uuid)
RETURNS TABLE (
    id                  uuid,
    utilisateur_id      uuid,
    pseudonyme          text,
    avatar_url          text,
    empire_color        text,
    level               integer,
    guilde_nom          text,
    guilde_couleur      text,
    date_debut          text,
    date_fin            text,
    distance_totale     float,
    duree_secondes      float,
    est_bouclee         boolean,
    vitesse_moyenne     float,
    vitesse_max         float,
    allure_moyenne      float,
    calories_estimees   float,
    denivele_positif    float,
    denivele_negatif    float,
    nom                 text,
    legende             text,
    superficie_conquise float,
    total_steps         integer,
    average_cadence     integer,
    image_url           text,
    points_gps          jsonb,
    reactions           jsonb,
    commentaires        jsonb
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
#variable_conflict use_column
DECLARE
    v_guilde_id uuid;
    v_user_lat  float;
    v_user_lon  float;
BEGIN
    SELECT guilde_id, latitude, longitude
    INTO v_guilde_id, v_user_lat, v_user_lon
    FROM public.profiles
    WHERE id = p_utilisateur_id;

    RETURN QUERY
    WITH

    -- 1. Utilisateurs dont les courses apparaissent dans le feed
    feed_users AS (
        SELECT p_utilisateur_id AS user_id
        UNION
        SELECT
            CASE
                WHEN demandeur_id = p_utilisateur_id THEN destinataire_id
                ELSE demandeur_id
            END AS user_id
        FROM public.amis
        WHERE (demandeur_id = p_utilisateur_id OR destinataire_id = p_utilisateur_id)
          AND statut = 'accepte'
        UNION
        SELECT p.id AS user_id
        FROM public.profiles p
        WHERE p.guilde_id IS NOT NULL
          AND p.guilde_id = v_guilde_id
    ),

    -- 2. Premier point GPS de chaque course
    course_start_points AS (
        SELECT DISTINCT ON (course_id)
            course_id, latitude, longitude
        FROM public.points_gps
        ORDER BY course_id, timestamp_gps ASC, date_creation ASC
    ),

    -- 3. Tracé complet par course
    course_points AS (
        SELECT
            pts.course_id,
            jsonb_agg(
                jsonb_build_object(
                    'longitude',     pts.longitude,
                    'latitude',      pts.latitude,
                    'altitude',      pts.altitude,
                    'timestamp_gps', pts.timestamp_gps
                )
                ORDER BY pts.timestamp_gps ASC, pts.date_creation ASC
            ) AS points
        FROM public.points_gps pts
        GROUP BY pts.course_id
    ),

    -- 4. Réactions agrégées
    course_reactions_agg AS (
        SELECT
            r.course_id,
            jsonb_agg(
                jsonb_build_object(
                    'id',             r.id,
                    'utilisateur_id', r.utilisateur_id,
                    'pseudonyme',     p.pseudonyme,
                    'type_reaction',  r.type_reaction
                )
            ) AS reactions
        FROM public.course_reactions r
        JOIN public.profiles p ON r.utilisateur_id = p.id
        GROUP BY r.course_id
    ),

    -- 5. Commentaires agrégés (ordre chronologique)
    course_comments_agg AS (
        SELECT
            c.course_id,
            jsonb_agg(
                jsonb_build_object(
                    'id',             c.id,
                    'utilisateur_id', c.utilisateur_id,
                    'pseudonyme',     p.pseudonyme,
                    'avatar_url',     p.avatar_url,
                    'contenu',        c.contenu,
                    'date_creation',  c.date_creation
                )
                ORDER BY c.date_creation ASC
            ) AS commentaires
        FROM public.course_commentaires c
        JOIN public.profiles p ON c.utilisateur_id = p.id
        GROUP BY c.course_id
    )

    -- 6. Résultat final
    SELECT 
        c.id,
        c.utilisateur_id,
        p.pseudonyme,
        p.avatar_url,
        p.empire_color,
        p.level,
        g.nom AS guilde_nom,
        g.couleur_hex AS guilde_couleur,
        c.date_debut::text,
        c.date_fin::text,
        c.distance_totale,
        c.duree_secondes,
        c.est_bouclee,
        c.vitesse_moyenne,
        c.vitesse_max,
        c.allure_moyenne,
        c.calories_estimees,
        c.denivele_positif,
        c.denivele_negatif,
        c.nom,
        c.legende,
        c.superficie_conquise,
        c.total_steps,
        c.average_cadence,
        c.image_url,
        COALESCE(pts.points, '[]'::jsonb),
        COALESCE(reacts.reactions, '[]'::jsonb),
        COALESCE(comments.commentaires, '[]'::jsonb)
    FROM public.courses c
    JOIN public.profiles p ON c.utilisateur_id = p.id
    LEFT JOIN public.guildes g              ON p.guilde_id   = g.id
    LEFT JOIN course_start_points csp       ON c.id          = csp.course_id
    LEFT JOIN course_points pts             ON c.id          = pts.course_id
    LEFT JOIN course_reactions_agg reacts   ON c.id          = reacts.course_id
    LEFT JOIN course_comments_agg comments  ON c.id          = comments.course_id
    WHERE
        c.utilisateur_id IN (SELECT user_id FROM feed_users)
        OR (
            v_user_lat IS NOT NULL AND v_user_lon IS NOT NULL
            AND COALESCE(csp.latitude,  p.latitude)  IS NOT NULL
            AND COALESCE(csp.longitude, p.longitude) IS NOT NULL
            AND COALESCE(csp.latitude,  p.latitude)  BETWEEN v_user_lat - 0.5 AND v_user_lat + 0.5
            AND COALESCE(csp.longitude, p.longitude) BETWEEN v_user_lon - 0.5 AND v_user_lon + 0.5
            AND ST_DWithin(
                ST_SetSRID(ST_MakePoint(v_user_lon, v_user_lat), 4326)::geography,
                ST_SetSRID(
                    ST_MakePoint(
                        COALESCE(csp.longitude, p.longitude),
                        COALESCE(csp.latitude,  p.latitude)
                    ), 4326
                )::geography,
                50000
            )
        )
    ORDER BY c.date_debut DESC;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.get_feed_courses(uuid) FROM public;
GRANT  EXECUTE ON FUNCTION public.get_feed_courses(uuid) TO authenticated, service_role;

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
#variable_conflict use_column
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
    WHERE t.contour && ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326);
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
#variable_conflict use_column
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
              ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
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

-- A. Classement individuel des joueurs (avec tag, boucles et distance)
DROP VIEW IF EXISTS public.leaderboard CASCADE;
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
  p.loop_count,
  COALESCE((SELECT SUM(c.distance_totale) FROM public.courses c WHERE c.utilisateur_id = p.id), 0.0) as distance_totale,
  g.nom as guilde_nom,
  g.couleur_hex as guilde_couleur,
  g.tag as guilde_tag
FROM public.profiles p
LEFT JOIN public.guildes g ON p.guilde_id = g.id;

-- B. Classement général des clans (avec tag, boucles et distance)
DROP VIEW IF EXISTS public.clan_leaderboard CASCADE;
CREATE OR REPLACE VIEW public.clan_leaderboard 
WITH (security_invoker = on) AS
SELECT 
    g.id,
    g.nom,
    g.tag,
    g.couleur_hex,
    g.avatar_url,
    COALESCE(SUM(p.total_area_m2), 0.0) as total_area_m2,
    COUNT(p.id) as membre_count,
    COALESCE(SUM(p.loop_count), 0) as loop_count,
    COALESCE(SUM((SELECT COALESCE(SUM(c.distance_totale), 0.0) FROM public.courses c WHERE c.utilisateur_id = p.id)), 0.0) as distance_totale
FROM public.guildes g
LEFT JOIN public.profiles p ON p.guilde_id = g.id
GROUP BY g.id, g.nom, g.tag, g.couleur_hex, g.avatar_url;

-- ==========================================
-- FONCTIONS D'ACCÈS DU CLASSEMENT LOCAL (POSTGIS)
-- ==========================================

-- C. Récupérer le classement des joueurs à proximité
CREATE OR REPLACE FUNCTION public.get_local_leaderboard(
  user_lat float,
  user_lon float,
  max_dist_meters float DEFAULT 50000
)
RETURNS TABLE (
  id uuid,
  pseudonyme text,
  tag text,
  empire_color text,
  avatar_url text,
  grade text,
  latitude float,
  longitude float,
  total_area_m2 float,
  loop_count integer,
  distance_totale float,
  guilde_nom text,
  guilde_couleur text,
  guilde_tag text,
  distance_meters float
)
LANGUAGE plpgsql SECURITY INVOKER
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    l.id,
    l.pseudonyme,
    l.tag,
    l.empire_color,
    l.avatar_url,
    l.grade,
    l.latitude,
    l.longitude,
    l.total_area_m2,
    l.loop_count,
    l.distance_totale,
    l.guilde_nom,
    l.guilde_couleur,
    l.guilde_tag,
    ST_Distance(
      ST_SetSRID(ST_MakePoint(l.longitude, l.latitude), 4326)::geography,
      ST_SetSRID(ST_MakePoint(user_lon, user_lat), 4326)::geography
    )::float as distance_meters
  FROM public.leaderboard l
  WHERE l.latitude IS NOT NULL 
    AND l.longitude IS NOT NULL
    AND ST_DWithin(
      ST_SetSRID(ST_MakePoint(l.longitude, l.latitude), 4326)::geography,
      ST_SetSRID(ST_MakePoint(user_lon, user_lat), 4326)::geography,
      max_dist_meters
    )
  ORDER BY distance_meters ASC;
END;
$$;

-- D. Récupérer le classement des clans à proximité
CREATE OR REPLACE FUNCTION public.get_local_clan_leaderboard(
  user_lat float,
  user_lon float,
  max_dist_meters float DEFAULT 50000
)
RETURNS TABLE (
  id uuid,
  nom text,
  tag text,
  couleur_hex text,
  avatar_url text,
  total_area_m2 float,
  membre_count bigint,
  loop_count numeric,
  distance_totale float,
  distance_meters float
)
LANGUAGE plpgsql SECURITY INVOKER
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    c.id,
    c.nom,
    c.tag,
    c.couleur_hex,
    c.avatar_url,
    c.total_area_m2,
    c.membre_count,
    c.loop_count::numeric,
    c.distance_totale,
    MIN(ST_Distance(
      ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
      ST_SetSRID(ST_MakePoint(user_lon, user_lat), 4326)::geography
    ))::float as distance_meters
  FROM public.clan_leaderboard c
  JOIN public.profiles p ON p.guilde_id = c.id
  WHERE p.latitude IS NOT NULL AND p.longitude IS NOT NULL
  GROUP BY c.id, c.nom, c.tag, c.couleur_hex, c.avatar_url, c.total_area_m2, c.membre_count, c.loop_count, c.distance_totale
  HAVING MIN(ST_Distance(
    ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
    ST_SetSRID(ST_MakePoint(user_lon, user_lat), 4326)::geography
  )) <= max_dist_meters
  ORDER BY distance_meters ASC;
END;
$$;

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
#variable_conflict use_column
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

-- Calcul de gain d'xp passif calculé sur la surface totale de l'empire
CREATE OR REPLACE FUNCTION public.calculer_gain_xp_passif()
RETURNS void AS $$
DECLARE
    r record;
    v_xp_gain integer;
BEGIN
    FOR r IN SELECT id, total_area_m2, xp FROM public.profiles LOOP
        v_xp_gain := FLOOR(1.05 * r.total_area_m2 / 100000.0);
        IF v_xp_gain > 0 THEN
            UPDATE public.profiles
            SET level = public.xp_to_level(xp + v_xp_gain),
                xp = xp + v_xp_gain
            WHERE id = r.id;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Activer pg_cron et programmer le calcul à 18h chaque jour (18h local ou UTC selon config)
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_cron;
    PERFORM cron.unschedule('calcul-xp-passif-18h') FROM cron.job WHERE jobname = 'calcul-xp-passif-18h';
    PERFORM cron.schedule('calcul-xp-passif-18h', '0 18 * * *', 'SELECT public.calculer_gain_xp_passif();');
EXCEPTION WHEN OTHERS THEN
    -- Fallback in case pg_cron cannot be configured or is restricted
    NULL;
END;
$$;

-- ==========================================
-- EMPIR STATS & 24H AREA VARIATION HISTORY
-- ==========================================

-- Table de l'historique des superficies des profils
CREATE TABLE IF NOT EXISTS public.profile_area_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    total_area_m2 float NOT NULL,
    recorded_at timestamptz DEFAULT now() NOT NULL
);

-- Index pour accélérer les requêtes
CREATE INDEX IF NOT EXISTS profile_area_history_profile_id_recorded_at_idx ON public.profile_area_history(profile_id, recorded_at DESC);

-- Trigger pour enregistrer l'historique lors du changement de total_area_m2 dans profiles
CREATE OR REPLACE FUNCTION public.log_profile_area_history()
RETURNS trigger AS $$
DECLARE
    v_last_recorded timestamptz;
BEGIN
    -- On ne loggue que si la superficie change ou lors de l'insertion
    IF (TG_OP = 'INSERT' OR OLD.total_area_m2 IS DISTINCT FROM NEW.total_area_m2) THEN
        -- On limite à un enregistrement par heure maximum par joueur pour économiser de l'espace
        SELECT MAX(recorded_at) INTO v_last_recorded 
        FROM public.profile_area_history 
        WHERE profile_id = NEW.id;
        
        IF (v_last_recorded IS NULL OR v_last_recorded < now() - INTERVAL '1 hour') THEN
            INSERT INTO public.profile_area_history (profile_id, total_area_m2, recorded_at)
            VALUES (NEW.id, NEW.total_area_m2, now());
            
            -- Nettoyage automatique des enregistrements de plus de 8 jours
            DELETE FROM public.profile_area_history 
            WHERE profile_id = NEW.id AND recorded_at < now() - INTERVAL '8 days';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_profile_area_changed ON public.profiles;
CREATE TRIGGER on_profile_area_changed
    AFTER INSERT OR UPDATE OF total_area_m2 ON public.profiles
    FOR EACH ROW
    EXECUTE PROCEDURE public.log_profile_area_history();

-- Fonction RPC pour récupérer les stats de l'empire et les variations sur 24h
CREATE OR REPLACE FUNCTION public.get_empire_stats(p_user_id uuid)
RETURNS TABLE (
    user_current_area_m2 float,
    user_change_24h_pct float,
    clan_current_area_m2 float,
    clan_change_24h_pct float
) AS $$
DECLARE
    v_user_current_area float;
    v_user_area_24h float;
    v_user_change_pct float;
    v_guild_id uuid;
    v_clan_current_area float := 0.0;
    v_clan_area_24h float := 0.0;
    v_clan_change_pct float := 0.0;
    v_member_id uuid;
    v_member_area float;
    v_member_area_24h float;
BEGIN
    -- 1. Récupérer les infos de l'utilisateur
    SELECT total_area_m2, guilde_id INTO v_user_current_area, v_guild_id
    FROM public.profiles
    WHERE id = p_user_id;

    IF v_user_current_area IS NULL THEN
        v_user_current_area := 0.0;
    END IF;

    -- Récupérer la superficie de l'utilisateur il y a 24h
    SELECT COALESCE(
        (SELECT total_area_m2 
         FROM public.profile_area_history 
         WHERE profile_id = p_user_id AND recorded_at <= now() - INTERVAL '24 hours'
         ORDER BY recorded_at DESC
         LIMIT 1),
        (SELECT total_area_m2 
         FROM public.profile_area_history 
         WHERE profile_id = p_user_id 
         ORDER BY recorded_at ASC
         LIMIT 1),
        v_user_current_area
    ) INTO v_user_area_24h;

    -- Calculer la variation pour l'utilisateur
    IF v_user_area_24h = 0 THEN
        IF v_user_current_area > 0 THEN
            v_user_change_pct := 100.0;
        ELSE
            v_user_change_pct := 0.0;
        END IF;
    ELSE
        v_user_change_pct := ((v_user_current_area - v_user_area_24h) / v_user_area_24h) * 100.0;
    END IF;

    -- 2. Si l'utilisateur appartient à un clan, calculer les stats du clan
    IF v_guild_id IS NOT NULL THEN
        -- Somme des superficies actuelles des membres du clan
        SELECT COALESCE(SUM(total_area_m2), 0.0) INTO v_clan_current_area
        FROM public.profiles
        WHERE guilde_id = v_guild_id;

        -- Somme des superficies des membres il y a 24h
        FOR v_member_id, v_member_area IN
            SELECT id, total_area_m2 FROM public.profiles WHERE guilde_id = v_guild_id
        LOOP
            SELECT COALESCE(
                (SELECT total_area_m2 
                 FROM public.profile_area_history 
                 WHERE profile_id = v_member_id AND recorded_at <= now() - INTERVAL '24 hours'
                 ORDER BY recorded_at DESC
                 LIMIT 1),
                (SELECT total_area_m2 
                 FROM public.profile_area_history 
                 WHERE profile_id = v_member_id 
                 ORDER BY recorded_at ASC
                 LIMIT 1),
                v_member_area
            ) INTO v_member_area_24h;
            
            v_clan_area_24h := v_clan_area_24h + v_member_area_24h;
        END LOOP;

        -- Calculer la variation pour le clan
        IF v_clan_area_24h = 0 THEN
            IF v_clan_current_area > 0 THEN
                v_clan_change_pct := 100.0;
            ELSE
                v_clan_change_pct := 0.0;
            END IF;
        ELSE
            v_clan_change_pct := ((v_clan_current_area - v_clan_area_24h) / v_clan_area_24h) * 100.0;
        END IF;
    END IF;

    user_current_area_m2 := v_user_current_area;
    user_change_24h_pct := v_user_change_pct;
    clan_current_area_m2 := v_clan_current_area;
    clan_change_24h_pct := v_clan_change_pct;
    
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;


