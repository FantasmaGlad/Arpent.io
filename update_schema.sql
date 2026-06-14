-- ==========================================
-- CONSOLIDATED SCHEMA UPDATE FOR ARPENT.IO
-- ==========================================

-- 1. Activer l'extension PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Table des points GPS pour l'historique complet des tracés
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

ALTER TABLE public.points_gps ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Les utilisateurs peuvent insérer leurs propres points gps" ON public.points_gps;
CREATE POLICY "Les utilisateurs peuvent insérer leurs propres points gps" ON public.points_gps 
FOR INSERT WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.courses c 
        WHERE c.id = course_id AND c.utilisateur_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Les utilisateurs peuvent voir leurs propres points gps" ON public.points_gps;
CREATE POLICY "Les utilisateurs peuvent voir leurs propres points gps" ON public.points_gps 
FOR SELECT USING (
    EXISTS (
        SELECT 1 FROM public.courses c 
        WHERE c.id = course_id AND c.utilisateur_id = auth.uid()
    )
);

-- 3. Structure des profils et paramètres de vie privée / empire
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS total_area_m2 float DEFAULT 0.0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS share_location boolean DEFAULT true NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS avatar_url text;
ALTER TABLE public.guildes ADD COLUMN IF NOT EXISTS avatar_url text;

CREATE INDEX IF NOT EXISTS profiles_total_area_m2_idx ON public.profiles(total_area_m2 DESC);

-- 4. Table des relations d'amis
CREATE TABLE IF NOT EXISTS public.amis (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    demandeur_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    destinataire_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    statut text DEFAULT 'en_attente' NOT NULL, -- 'en_attente', 'accepte'
    date_creation timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE (demandeur_id, destinataire_id)
);

ALTER TABLE public.amis ENABLE ROW LEVEL SECURITY;

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

-- 5. Adaptation du contour géographique
ALTER TABLE public.territoires ALTER COLUMN contour TYPE geometry(Geometry, 4326);

-- 6. Trigger pour mettre à jour la superficie totale dans profiles
CREATE OR REPLACE FUNCTION public.update_profile_cached_area()
RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.profiles 
        SET total_area_m2 = total_area_m2 + new.superficie_m2
        WHERE id = new.utilisateur_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.profiles 
        SET total_area_m2 = greatest(0.0, total_area_m2 - old.superficie_m2)
        WHERE id = old.utilisateur_id;
    ELSIF (TG_OP = 'UPDATE') THEN
        UPDATE public.profiles 
        SET total_area_m2 = greatest(0.0, total_area_m2 - old.superficie_m2 + new.superficie_m2)
        WHERE id = new.utilisateur_id;
    END IF;
    RETURN null;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_territoire_changed ON public.territoires;
CREATE TRIGGER on_territoire_changed
    AFTER INSERT OR UPDATE OR DELETE ON public.territoires
    FOR EACH ROW EXECUTE PROCEDURE public.update_profile_cached_area();

-- 7. Nettoyage et fusion des doublons existants par joueur
DO $$
DECLARE
    r record;
    v_merged_geom geometry;
    v_total_area float;
    v_new_points text[];
BEGIN
    FOR r IN SELECT utilisateur_id, guilde_id FROM public.territoires GROUP BY utilisateur_id, guilde_id LOOP
        SELECT ST_Union(ST_Accum(contour)) INTO v_merged_geom
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

-- Recalculer les superficies existantes
UPDATE public.profiles p
SET total_area_m2 = coalesce(
    (SELECT sum(superficie_m2) FROM public.territoires t WHERE t.utilisateur_id = p.id),
    0.0
);

-- 8. Fonction d'enregistrement de course avec Conquête et fusion automatique (MultiPolygon)
CREATE OR REPLACE FUNCTION public.enregistrer_course(
    p_user_id uuid,
    p_date_debut timestamp with time zone,
    p_date_fin timestamp with time zone,
    p_distance_totale float,
    p_duree_secondes float,
    p_est_bouclee boolean,
    p_points text[]
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
    -- 1. Insérer la session de course
    INSERT INTO public.courses (utilisateur_id, date_debut, date_fin, distance_totale, duree_secondes, est_bouclee)
    VALUES (p_user_id, p_date_debut, p_date_fin, p_distance_totale, p_duree_secondes, p_est_bouclee)
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
$$ LANGUAGE plpgsql SECURITY INVOKER;

-- 9. Fonction de récupération des territoires par viewport (Bounding Box)
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
    WHERE t.contour && ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326);
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;

-- 10. Vue globale du Classement des Joueurs (Leaderboard)
DROP VIEW IF EXISTS public.leaderboard;

CREATE OR REPLACE VIEW public.leaderboard AS
SELECT 
  p.id,
  p.pseudonyme,
  p.empire_color,
  p.avatar_url,
  CASE WHEN p.share_location THEN p.latitude ELSE null END as latitude,
  CASE WHEN p.share_location THEN p.longitude ELSE null END as longitude,
  p.total_area_m2,
  g.nom as guilde_nom,
  g.couleur_hex as guilde_couleur
FROM public.profiles p
LEFT JOIN public.guildes g ON p.guilde_id = g.id
ORDER BY p.total_area_m2 DESC;

-- 11. Vue globale du Classement des Clans (Clan Leaderboard)
DROP VIEW IF EXISTS public.clan_leaderboard;

CREATE OR REPLACE VIEW public.clan_leaderboard AS
SELECT 
    g.id,
    g.nom,
    g.couleur_hex,
    g.avatar_url,
    COALESCE(SUM(p.total_area_m2), 0.0) as total_area_m2,
    COUNT(p.id) as membre_count
FROM public.guildes g
LEFT JOIN public.profiles p ON p.guilde_id = g.id
GROUP BY g.id, g.nom, g.couleur_hex, g.avatar_url
ORDER BY total_area_m2 DESC;

-- 12. Fonction de suggestions d'amis par proximité géographique
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
        -- If current user has no location, suggest profiles by total area
        RETURN QUERY
        SELECT 
            p.id,
            p.pseudonyme,
            p.avatar_url,
            p.empire_color,
            null::float as distance_meters
        FROM public.profiles p
        WHERE p.id <> p_utilisateur_id
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
