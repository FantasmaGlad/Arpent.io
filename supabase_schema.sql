-- Active l'extension PostGIS pour stocker les polygones de territoires et faire des calculs spatiaux
create extension if not exists postgis;

-- 1. Table des Guildes/Clans (Optionnel)
create table if not exists public.guildes (
    id uuid default gen_random_uuid() primary key,
    nom text not null unique,
    couleur_hex text not null,
    date_creation timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 2. Table des Profils Utilisateurs (liée à la table auth.users de Supabase Auth)
create table if not exists public.profiles (
    id uuid references auth.users on delete cascade primary key,
    pseudonyme text unique,
    guilde_id uuid references public.guildes(id) on delete set null,
    date_inscription timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 3. Table des Sessions de Course
create table if not exists public.courses (
    id uuid default gen_random_uuid() primary key,
    utilisateur_id uuid references public.profiles(id) on delete cascade not null,
    date_debut timestamp with time zone not null,
    date_fin timestamp with time zone,
    distance_totale float default 0.0 not null,
    duree_secondes float default 0.0 not null,
    est_bouclee boolean default false not null
);

-- 4. Table des Territoires Conquis (PostGIS geometry polygon)
create table if not exists public.territoires (
    id uuid default gen_random_uuid() primary key,
    utilisateur_id uuid references public.profiles(id) on delete cascade not null,
    guilde_id uuid references public.guildes(id) on delete set null,
    contour geometry(Polygon, 4326) not null,
    superficie_m2 float not null,
    points text[] not null, -- points du contour stockés sous forme textuelle 'long lat'
    derniere_mise_a_jour timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Assure la présence de la colonne points si la table existait déjà
alter table public.territoires add column if not exists points text[];

-- Index géographique pour optimiser les calculs de collision/intersection
create index if not exists territoires_contour_geo_idx on public.territoires using gist(contour);

-- --- DÉCLENCHEURS (TRIGGERS) POUR SUPABASE AUTH ---

-- Fonction déclenchée lors de la création d'un utilisateur dans auth.users
-- Cela gère à la fois l'inscription classique et l'inscription anonyme.
create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (id, pseudonyme, date_inscription)
  values (
    new.id,
    coalesce(
      new.raw_user_meta_data->>'pseudonyme', 
      case 
        when new.email is null then 'Invité_' || substr(new.id::text, 1, 8)
        else 'Joueur_' || substr(new.id::text, 1, 8)
      end
    ),
    now()
  );
  return new;
end;
$$ language plpgsql security definer;

-- Lie le déclencheur à la table auth.users
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure public.handle_new_user();

-- --- SÉCURITÉ : ROW LEVEL SECURITY (RLS) ---
alter table public.profiles enable row level security;
alter table public.guildes enable row level security;
alter table public.courses enable row level security;
alter table public.territoires enable row level security;

-- Politiques de sécurité pour Profiles
drop policy if exists "Tout le monde peut lire les profils" on public.profiles;
create policy "Tout le monde peut lire les profils" on public.profiles for select using (true);

drop policy if exists "Les utilisateurs peuvent modifier leur propre profil" on public.profiles;
create policy "Les utilisateurs peuvent modifier leur propre profil" on public.profiles for update using (auth.uid() = id);

-- Politiques de sécurité pour Guildes
drop policy if exists "Tout le monde peut lire les guildes" on public.guildes;
create policy "Tout le monde peut lire les guildes" on public.guildes for select using (true);

drop policy if exists "Seuls les utilisateurs authentifiés peuvent créer des guildes" on public.guildes;
create policy "Seuls les utilisateurs authentifiés peuvent créer des guildes" on public.guildes for insert with check (auth.role() = 'authenticated');

-- Politiques de sécurité pour Courses
drop policy if exists "Les utilisateurs peuvent voir leurs propres courses" on public.courses;
create policy "Les utilisateurs peuvent voir leurs propres courses" on public.courses for select using (auth.uid() = utilisateur_id);

drop policy if exists "Les utilisateurs peuvent insérer leurs propres courses" on public.courses;
create policy "Les utilisateurs peuvent insérer leurs propres courses" on public.courses for insert with check (auth.uid() = utilisateur_id);

-- Politiques de sécurité pour Territoires
drop policy if exists "Tout le monde peut voir les territoires" on public.territoires;
create policy "Tout le monde peut voir les territoires" on public.territoires for select using (true);

drop policy if exists "Les utilisateurs peuvent insérer/modifier leurs propres territoires" on public.territoires;
create policy "Les utilisateurs peuvent insérer/modifier leurs propres territoires" on public.territoires for insert with check (auth.uid() = utilisateur_id);

-- --- FONCTION D'ENREGISTREMENT DE COURSE ET TERRITOIRE (RPC) ---
create or replace function public.enregistrer_course(
    p_user_id uuid,
    p_date_debut timestamp with time zone,
    p_date_fin timestamp with time zone,
    p_distance_totale float,
    p_duree_secondes float,
    p_est_bouclee boolean,
    p_points text[] -- tableau de points au format 'lng lat'
)
returns void as $$
declare
    v_course_id uuid;
    v_wkt text;
    v_superficie float;
begin
    -- 1. Insérer la session de course
    insert into public.courses (utilisateur_id, date_debut, date_fin, distance_totale, duree_secondes, est_bouclee)
    values (p_user_id, p_date_debut, p_date_fin, p_distance_totale, p_duree_secondes, p_est_bouclee)
    returning id into v_course_id;

    -- 2. Si c'est une boucle (territoire conquis) et qu'on a au moins 3 points, on crée le polygone
    if p_est_bouclee and array_length(p_points, 1) >= 3 then
        -- Construit le polygone WKT à partir des points
        v_wkt := 'POLYGON((' || array_to_string(p_points, ', ') || '))';
        
        -- Calcule la superficie réelle en mètres carrés avec ST_Area sur le type geography
        v_superficie := ST_Area(ST_GeomFromText(v_wkt, 4326)::geography);

        -- Insérer le territoire conquis
        insert into public.territoires (utilisateur_id, contour, superficie_m2, points)
        values (p_user_id, ST_GeomFromText(v_wkt, 4326), v_superficie, p_points);
    end if;
end;
$$ language plpgsql security invoker;

-- Assure la présence des colonnes de couleur d'empire et de coordonnées sur la table des profils
alter table public.profiles add column if not exists empire_color text default '#00E676';
alter table public.profiles add column if not exists latitude float;
alter table public.profiles add column if not exists longitude float;

-- Vue globale de classement triée par taille d'empire
create or replace view public.leaderboard as
select 
  p.id,
  p.pseudonyme,
  p.empire_color,
  p.latitude,
  p.longitude,
  coalesce(sum(t.superficie_m2), 0) as total_area_m2
from public.profiles p
left join public.territoires t on p.id = t.utilisateur_id
group by p.id, p.pseudonyme, p.empire_color, p.latitude, p.longitude
order by total_area_m2 desc;

