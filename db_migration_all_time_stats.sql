-- Migration Script: Separating Current & All-Time Stats in profiles
-- Run this in your Supabase SQL Editor.

-- 1. Add the cumulative all_time_area_m2 column
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS all_time_area_m2 float DEFAULT 0.0 NOT NULL;

-- 2. Backfill existing profile data
UPDATE public.profiles 
SET all_time_area_m2 = total_area_m2 
WHERE all_time_area_m2 = 0.0;

-- 3. Replace the trigger function to update all_time_area_m2 cumulatively
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
