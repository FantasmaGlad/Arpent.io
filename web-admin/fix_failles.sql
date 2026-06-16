-- 1. Faille 1: Sécurisation de spatial_ref_sys en révoquant les privilèges pour anon et authenticated
REVOKE ALL PRIVILEGES ON TABLE public.spatial_ref_sys FROM anon, authenticated;

-- 2. Faille 2: Reconstruire la vue public.leaderboard en activant security_invoker
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

-- 3. Faille 3: Reconstruire la vue public.clan_leaderboard en activant security_invoker
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
