CREATE EXTENSION IF NOT EXISTS postgis;

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

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS total_area_m2 float DEFAULT 0.0 NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS share_location boolean DEFAULT true NOT NULL;

CREATE INDEX IF NOT EXISTS profiles_total_area_m2_idx ON public.profiles(total_area_m2 DESC);

ALTER TABLE public.territoires ALTER COLUMN contour TYPE geometry(Geometry, 4326);

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

UPDATE public.profiles p
SET total_area_m2 = coalesce(
    (SELECT sum(superficie_m2) FROM public.territoires t WHERE t.utilisateur_id = p.id),
    0.0
);

CREATE OR REPLACE VIEW public.leaderboard AS
SELECT 
  p.id,
  p.pseudonyme,
  p.empire_color,
  CASE WHEN p.share_location THEN p.latitude ELSE null END as latitude,
  CASE WHEN p.share_location THEN p.longitude ELSE null END as longitude,
  p.total_area_m2
FROM public.profiles p
ORDER BY p.total_area_m2 DESC;

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
BEGIN
    INSERT INTO public.courses (utilisateur_id, date_debut, date_fin, distance_totale, duree_secondes, est_bouclee)
    VALUES (p_user_id, p_date_debut, p_date_fin, p_distance_totale, p_duree_secondes, p_est_bouclee)
    RETURNING id INTO v_course_id;

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

    IF p_est_bouclee AND array_length(p_points, 1) >= 3 THEN
        v_wkt := 'POLYGON((' || array_to_string(p_points, ', ') || '))';
        
        BEGIN
            v_geom := ST_GeomFromText(v_wkt, 4326);
            
            IF NOT ST_IsValid(v_geom) THEN
                v_geom := ST_MakeValid(v_geom);
            END IF;

            v_geom := ST_CollectionExtract(v_geom, 3);
            
            v_superficie := ST_Area(v_geom::geography);

            INSERT INTO public.territoires (utilisateur_id, contour, superficie_m2, points)
            VALUES (p_user_id, v_geom, v_superficie, p_points);
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'Échec de la validation géométrique : %', SQLERRM;
        END;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;

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
    points text[],
    latitude float,
    longitude float
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.utilisateur_id,
        p.pseudonyme,
        p.empire_color,
        t.points,
        CASE WHEN p.share_location THEN p.latitude ELSE null END as latitude,
        CASE WHEN p.share_location THEN p.longitude ELSE null END as longitude
    FROM public.territoires t
    JOIN public.profiles p ON t.utilisateur_id = p.id
    WHERE t.contour && ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326);
END;
$$ LANGUAGE plpgsql SECURITY INVOKER;
