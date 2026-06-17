'use client';

import React, { useEffect, useRef, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Calendar,
  Globe,
  Layers,
  Search, 
  Trash2, 
  Activity, 
  Clock, 
  X,
  User,
  ShieldAlert,
  Save,
  Image as ImageIcon,
  Award,
  MapPin,
  Users,
  ChevronRight
} from 'lucide-react';
import { 
  ResponsiveContainer, 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip
} from 'recharts';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';

// Set mapbox token
mapboxgl.accessToken = process.env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN || '';

interface Profile {
  id: string;
  pseudonyme: string | null;
  tag: string | null;
  guilde_id: string | null;
  total_area_m2: number;
  all_time_area_m2: number;
  max_area_m2: number;
  area_lost_m2: number;
  xp: number;
  level: number;
  loop_count: number;
  max_loop_distance_km: number;
  share_location: boolean;
  avatar_url: string | null;
  empire_color: string;
  latitude: number | null;
  longitude: number | null;
  grade: string | null;
  ghost_mode: boolean;
  date_inscription: string;
}

interface Guild {
  id: string;
  nom: string;
  tag: string | null;
  couleur_hex: string;
  avatar_url: string | null;
}

interface TerritoryGeoJSON {
  id: string;
  utilisateur_id: string;
  pseudonyme: string;
  tag: string | null;
  empire_color: string;
  guilde_nom: string | null;
  guilde_couleur: string | null;
  guilde_tag: string | null;
  superficie_m2: number;
  geojson: string;
}

export default function MapPage() {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);

  // State
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTerritory, setSelectedTerritory] = useState<any>(null);
  const [geoTerritories, setGeoTerritories] = useState<TerritoryGeoJSON[]>([]);

  // Profile detailed modal state
  const [selectedProfile, setSelectedProfile] = useState<any>(null);
  const [activeTab, setActiveTab] = useState<'apercu' | 'conquete' | 'entrainement' | 'amis' | 'parametres'>('apercu');
  const [courses, setCourses] = useState<any[]>([]);
  const [loadingCourses, setLoadingCourses] = useState(false);
  const [friends, setFriends] = useState<any[]>([]);
  const [loadingFriends, setLoadingFriends] = useState(false);
  const [streak, setStreak] = useState<number>(0);
  const [mounted, setMounted] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Filters for runs inside the modal
  const [minDistance, setMinDistance] = useState<number>(0);
  const [loopOnlyFilter, setLoopOnlyFilter] = useState<'all' | 'loops' | 'noloops'>('all');

  // Edit fields (for Tab 5: Moderation)
  const [newPseudonyme, setNewPseudonyme] = useState('');
  const [newTag, setNewTag] = useState('');
  const [newGrade, setNewGrade] = useState('');
  const [newGuildeId, setNewGuildeId] = useState('');
  const [newXp, setNewXp] = useState(0);
  const [newLevel, setNewLevel] = useState(1);
  const [newEmpireColor, setNewEmpireColor] = useState('#CCFF00');
  const [newTotalArea, setNewTotalArea] = useState(0);
  const [newAllTimeArea, setNewAllTimeArea] = useState(0);
  const [newMaxArea, setNewMaxArea] = useState(0);
  const [newAreaLost, setNewAreaLost] = useState(0);
  const [newLoopCount, setNewLoopCount] = useState(0);
  const [newMaxLoopDistance, setNewMaxLoopDistance] = useState(0);
  const [newGhostMode, setNewGhostMode] = useState(false);
  const [newShareLocation, setNewShareLocation] = useState(false);

  // In-modal stats and derived values
  const totalRuns = courses.length;
  const totalDistanceKm = courses.reduce((acc, c) => acc + c.distance_totale, 0) / 1000;
  const totalDurationSec = courses.reduce((acc, c) => acc + c.duree_secondes, 0);
  const avgSpeed = courses.length > 0 ? courses.reduce((acc, c) => acc + (c.vitesse_moyenne || 0), 0) / courses.length : 0;
  const totalCalories = courses.reduce((acc, c) => acc + (c.calories_estimees || 0), 0);
  const totalElevationPos = courses.reduce((acc, c) => acc + (c.denivele_positif || 0), 0);

  const formatDurationText = (sec: number) => {
    const hrs = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const remainingSecs = sec % 60;
    if (hrs > 0) return `${hrs}h ${mins}m`;
    return `${mins}m ${remainingSecs}s`;
  };

  const filteredCourses = courses.filter(c => {
    const distKm = c.distance_totale / 1000;
    if (distKm < minDistance) return false;
    if (loopOnlyFilter === 'loops' && !c.est_bouclee) return false;
    if (loopOnlyFilter === 'noloops' && c.est_bouclee) return false;
    return true;
  });

  const chartData = [...courses]
    .reverse()
    .slice(-15)
    .map(c => ({
      date: new Date(c.date_debut).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
      distance: parseFloat((c.distance_totale / 1000).toFixed(2)),
      duration: parseFloat((c.duree_secondes / 60).toFixed(1))
    }));

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!selectedProfile) return;

    setActiveTab('apercu');
    setMessage(null);

    // Initialize edit fields
    setNewPseudonyme(selectedProfile.pseudonyme || '');
    setNewTag(selectedProfile.tag || '');
    setNewGrade(selectedProfile.grade || '');
    setNewGuildeId(selectedProfile.guilde_id || '');
    setNewXp(selectedProfile.xp || 0);
    setNewLevel(selectedProfile.level || 1);
    setNewEmpireColor(selectedProfile.empire_color || '#CCFF00');
    setNewTotalArea(selectedProfile.total_area_m2 || 0);
    setNewAllTimeArea(selectedProfile.all_time_area_m2 || 0);
    setNewMaxArea(selectedProfile.max_area_m2 || 0);
    setNewAreaLost(selectedProfile.area_lost_m2 || 0);
    setNewLoopCount(selectedProfile.loop_count || 0);
    setNewMaxLoopDistance(selectedProfile.max_loop_distance_km || 0);
    setNewGhostMode(selectedProfile.ghost_mode || false);
    setNewShareLocation(selectedProfile.share_location || false);

    // Fetch streak via RPC
    supabase.rpc('get_user_streak', { p_user_id: selectedProfile.id })
      .then(({ data, error }) => {
        if (error) console.error('Error fetching streak:', error);
        setStreak(data || 0);
      });

    // Fetch courses
    setLoadingCourses(true);
    supabase.from('courses')
      .select('*')
      .eq('utilisateur_id', selectedProfile.id)
      .order('date_debut', { ascending: false })
      .then(({ data, error }) => {
        if (error) console.error('Error fetching courses:', error);
        setCourses(data || []);
        setLoadingCourses(false);
      });

    // Fetch friends
    setLoadingFriends(true);
    supabase.from('relations')
      .select('demandeur_id, destinataire_id, statut')
      .or(`demandeur_id.eq.${selectedProfile.id},destinataire_id.eq.${selectedProfile.id}`)
      .eq('statut', 'accepte')
      .then(async ({ data: relData, error: relErr }) => {
        if (relErr || !relData) {
          setFriends([]);
          setLoadingFriends(false);
          return;
        }

        const friendIds = relData.map(r => 
          r.demandeur_id === selectedProfile.id ? r.destinataire_id : r.demandeur_id
        );

        if (friendIds.length === 0) {
          setFriends([]);
          setLoadingFriends(false);
          return;
        }

        const { data: profilesData, error: profErr } = await supabase
          .from('profiles')
          .select('*')
          .in('id', friendIds);

        if (profErr) {
          console.error('Error fetching friend profiles:', profErr);
          setFriends([]);
        } else {
          setFriends(profilesData || []);
        }
        setLoadingFriends(false);
      });

  }, [selectedProfile]);

  const handleUpdateProfile = async () => {
    setActionLoading(true);
    setMessage(null);
    try {
      const response = await fetch('/api/admin/profiles', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: selectedProfile.id,
          pseudonyme: newPseudonyme,
          tag: newTag,
          grade: newGrade,
          guilde_id: newGuildeId || null,
          xp: newXp,
          level: newLevel,
          empire_color: newEmpireColor,
          total_area_m2: newTotalArea,
          all_time_area_m2: newAllTimeArea,
          max_area_m2: newMaxArea,
          area_lost_m2: newAreaLost,
          loop_count: newLoopCount,
          max_loop_distance_km: newMaxLoopDistance,
          ghost_mode: newGhostMode,
          share_location: newShareLocation
        })
      });

      if (!response.ok) {
        throw new Error('Failed to update profile');
      }

      const updated = await response.json();
      setMessage({ text: 'Profil mis à jour avec succès.', type: 'success' });
      
      setSelectedProfile(updated);
      setProfiles(prev => prev.map(p => p.id === updated.id ? { ...p, ...updated } : p));
    } catch (err: any) {
      setMessage({ text: err.message || 'Erreur lors de la mise à jour.', type: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCourse = async (courseId: string) => {
    if (!window.confirm('Êtes-vous sûr de vouloir supprimer cette course ? Cette action recalculera les XP et le niveau.')) return;
    setActionLoading(true);
    try {
      const response = await fetch(`/api/admin/courses?id=${courseId}`, {
        method: 'DELETE'
      });
      if (!response.ok) {
        throw new Error('Failed to delete course');
      }

      setCourses(prev => prev.filter(c => c.id !== courseId));
      
      const { data } = await supabase.from('profiles').select('*').eq('id', selectedProfile.id).single();
      if (data) {
        setSelectedProfile(data);
        setProfiles(prev => prev.map(p => p.id === data.id ? { ...p, ...data } : p));
      }
    } catch (err: any) {
      alert(err.message || 'Erreur lors de la suppression.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveAvatar = async () => {
    if (!window.confirm('Supprimer l\'avatar de cet utilisateur ?')) return;
    setActionLoading(true);
    try {
      const { error } = await supabase.from('profiles').update({ avatar_url: null }).eq('id', selectedProfile.id);
      if (error) throw error;
      const updated = { ...selectedProfile, avatar_url: null };
      setSelectedProfile(updated);
      setProfiles(prev => prev.map(p => p.id === updated.id ? updated : p));
    } catch (err: any) {
      alert(err.message || 'Erreur lors de la suppression de l\'avatar.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (userId: string) => {
    if (!window.confirm('ATTENTION: Supprimer définitivement cet utilisateur et toutes ses données ? Cette action est irréversible.')) return;
    setActionLoading(true);
    try {
      const { error } = await supabase.from('profiles').delete().eq('id', userId);
      if (error) throw error;
      setProfiles(prev => prev.filter(p => p.id !== userId));
      setSelectedProfile(null);
      setSelectedTerritory(null);
    } catch (err: any) {
      alert(err.message || 'Erreur lors de la suppression.');
    } finally {
      setActionLoading(false);
    }
  };

  const centerPlayerOnMap = (lat: number, lng: number) => {
    if (mapRef.current) {
      mapRef.current.flyTo({
        center: [lng, lat],
        zoom: 14,
        pitch: 0,
        essential: true
      });
      setSelectedProfile(null);
    }
  };

  // Initial fetch
  useEffect(() => {
    async function loadData() {
      try {
        const [
          { data: profilesData },
          { data: guildsData }
        ] = await Promise.all([
          supabase.from('profiles').select('*'),
          supabase.from('guildes').select('*')
        ]);

        const profilesList = (profilesData || []) as Profile[];
        const guildsList = (guildsData || []) as Guild[];

        setProfiles(profilesList);
        setGuilds(guildsList);

        // Fetch GeoJSON territories via RPC
        try {
          const { data: geoData } = await supabase.rpc('get_territoires_geojson');
          if (geoData) setGeoTerritories(geoData as TerritoryGeoJSON[]);
        } catch (geoErr) {
          console.error('Error fetching GeoJSON territories:', geoErr);
        }
      } catch (err) {
        console.error('Error fetching dashboard data:', err);
      } finally {
        setLoading(false);
      }
    }

    loadData();

    // Subscribe to real-time updates for coordinates and territories
    const profileChannel = supabase.channel('profiles-realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'profiles' }, (payload) => {
        const newProfile = payload.new as Profile;
        if (!newProfile || !newProfile.id) return;
        setProfiles(prev => {
          const index = prev.findIndex(p => p.id === newProfile.id);
          if (index !== -1) {
            const updated = [...prev];
            updated[index] = newProfile;
            return updated;
          }
          return [...prev, newProfile];
        });
      })
      .subscribe();

    const territoryChannel = supabase.channel('territories-realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'territoires' }, () => {
        // Refresh GeoJSON
        supabase.rpc('get_territoires_geojson').then(({ data }) => {
          if (data) setGeoTerritories(data as TerritoryGeoJSON[]);
        });
      })
      .subscribe();

    return () => {
      supabase.removeChannel(profileChannel);
      supabase.removeChannel(territoryChannel);
    };
  }, []);

  // Initialize Mapbox map
  useEffect(() => {
    if (loading || !mapContainer.current || mapRef.current) return;

    // Use specific application style
    const map = new mapboxgl.Map({
      container: mapContainer.current,
      style: 'mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8',
      center: [2.3522, 48.8566], // Paris
      zoom: 11,
      projection: { name: 'mercator' }
    });

    map.on('style.load', () => {
      map.setFog({
        color: 'rgb(0, 0, 0)',
        'high-color': 'rgb(10, 10, 12)',
        'horizon-blend': 0.02,
        'space-color': 'rgb(0, 0, 0)',
        'star-intensity': 0.0
      });

      // Check for redirect target location
      const targetLat = localStorage.getItem('map_center_lat');
      const targetLng = localStorage.getItem('map_center_lng');
      if (targetLat && targetLng) {
        map.flyTo({
          center: [parseFloat(targetLng), parseFloat(targetLat)],
          zoom: 14,
          pitch: 0,
          essential: true
        });
        localStorage.removeItem('map_center_lat');
        localStorage.removeItem('map_center_lng');
      }
    });

    mapRef.current = map;
    map.addControl(new mapboxgl.NavigationControl({ showCompass: true }), 'bottom-left');

    return () => {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, [loading]);

  // Update map layers and markers when data changes
  useEffect(() => {
    const map = mapRef.current;
    if (!map || loading) return;

    if (!map.isStyleLoaded()) {
      map.on('style.load', updateMapElements);
      return;
    }

    updateMapElements();

    function updateMapElements() {
      if (!map) return;

      // --- 1. RENDER TERRITORIES (POLYGONS) via GeoJSON ---
      const geojsonFeatures = geoTerritories.map(t => {
        try {
          const geometry = JSON.parse(t.geojson);
          const color = t.guilde_couleur || t.empire_color || '#CCFF00';
          return {
            type: 'Feature' as const,
            properties: {
              id: t.id,
              utilisateur_id: t.utilisateur_id,
              superficie: t.superficie_m2,
              joueur: t.pseudonyme || 'Utilisateur',
              tag: t.tag || '',
              clan: t.guilde_nom || 'Aucun groupe',
              guilde_tag: t.guilde_tag || '',
              color: color
            },
            geometry
          };
        } catch {
          return null;
        }
      }).filter(Boolean);

      const geojson: any = {
        type: 'FeatureCollection',
        features: geojsonFeatures
      };

      if (map.getSource('territories')) {
        (map.getSource('territories') as mapboxgl.GeoJSONSource).setData(geojson);
      } else {
        map.addSource('territories', {
          type: 'geojson',
          data: geojson
        });

        map.addLayer({
          id: 'territories-fill',
          type: 'fill',
          source: 'territories',
          paint: {
            'fill-color': ['get', 'color'],
            'fill-opacity': 0.2
          }
        });

        map.addLayer({
          id: 'territories-stroke',
          type: 'line',
          source: 'territories',
          paint: {
            'line-color': ['get', 'color'],
            'line-width': 1.5
          }
        });

        map.on('click', 'territories-fill', (e) => {
          const feature = e.features?.[0];
          if (feature) {
            setSelectedTerritory(feature.properties);
          }
        });

        map.on('mouseenter', 'territories-fill', () => {
          map.getCanvas().style.cursor = 'pointer';
        });
        map.on('mouseleave', 'territories-fill', () => {
          map.getCanvas().style.cursor = '';
        });
      }

      // --- 2. RENDER ACTIVE PLAYER MARKERS ---
      markersRef.current.forEach(m => m.remove());
      markersRef.current = [];

      profiles.forEach(p => {
        if (p.share_location && p.latitude !== null && p.longitude !== null) {
          const guild = guilds.find(g => g.id === p.guilde_id);
          const color = guild?.couleur_hex || p.empire_color || '#CCFF00';

          const el = document.createElement('div');
          el.className = 'player-marker';
          el.style.width = '14px';
          el.style.height = '14px';
          el.style.borderRadius = '50%';
          el.style.backgroundColor = color;
          el.style.border = '2px solid #000000';
          el.style.boxShadow = `0 2px 6px rgba(0, 0, 0, 0.6)`;
          el.style.cursor = 'pointer';

          // Apply visual distinction for ghost mode (semi-transparent outline marker)
          if (p.ghost_mode) {
            el.style.opacity = '0.7';
            el.style.border = '2px dashed #FFD700';
          }

          const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(`
            <div style="font-family: var(--font-outfit)">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${color}"></div>
                <strong style="font-size: 0.95rem; color: #FFFFFF;">${p.pseudonyme || 'Utilisateur'}</strong>
                <span style="font-size: 0.7rem; color: #8E9BAE; font-family: monospace;">${p.tag || ''}</span>
                ${p.ghost_mode ? '<span style="font-size: 0.75rem; color: #FFA500; font-weight: bold; margin-left: 8px; display: flex; align-items: center; gap: 2px;">👻 Invisible</span>' : ''}
              </div>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0 0 4px 0;">Groupe: ${guild?.nom || 'Indépendant'}</p>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0;">Lat: ${p.latitude!.toFixed(5)}, Lng: ${p.longitude!.toFixed(5)}</p>
            </div>
          `);

          // Allow clicking the marker to open profile modal directly
          el.addEventListener('click', (ev) => {
            ev.stopPropagation();
            setSelectedProfile(p);
          });

          const marker = new mapboxgl.Marker({ element: el })
             .setLngLat([p.longitude, p.latitude])
             .setPopup(popup)
             .addTo(map);

          markersRef.current.push(marker);
        }
      });
    }
  }, [geoTerritories, profiles, guilds, loading]);

  const resetMapCenter = () => {
    if (mapRef.current) {
      mapRef.current.flyTo({
        center: [2.3522, 48.8566],
        zoom: 11,
        pitch: 0,
        bearing: 0,
        essential: true
      });
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh', gap: '16px' }}>
        <div className="avatar avatar-placeholder" style={{ width: '48px', height: '48px', border: '1px solid var(--primary-green)', animation: 'pulse 1.5s infinite' }} />
        <p style={{ color: 'var(--text-muted)' }}>Initialisation du réseau...</p>
        <style jsx>{`
          @keyframes pulse {
            0% { transform: scale(0.95); opacity: 0.5; }
            50% { transform: scale(1.05); opacity: 1; }
            100% { transform: scale(0.95); opacity: 0.5; }
          }
        `}</style>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 80px)', position: 'relative' }}>
      <div className="cyber-bg" />

      {/* Main Map Panel with controls */}
      <div className="map-container-wrapper" style={{ height: '100%', margin: 0, border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden' }}>
        <div ref={mapContainer} className="map-viewport" />

        {/* Reset Map control button */}
        <button 
          onClick={resetMapCenter}
          style={{
            position: 'absolute',
            bottom: '20px',
            left: '20px',
            zIndex: 10,
            backgroundColor: '#0F1115',
            border: '1px solid var(--border-color)',
            color: 'var(--text-white)',
            padding: '8px 12px',
            borderRadius: '6px',
            fontFamily: 'var(--font-outfit)',
            fontSize: '0.8rem',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)',
            transition: 'all 0.2s'
          }}
          onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--primary-green)'}
          onMouseLeave={(e) => e.currentTarget.style.borderColor = 'var(--border-color)'}
        >
          <Globe size={14} style={{ color: 'var(--primary-green)' }} /> Centrer Paris
        </button>

        {/* Territory Inspector Side Panel */}
        {selectedTerritory && (
          <div className="map-overlay-panel glass-card" style={{ padding: '20px', borderLeft: `3px solid var(--primary-green)`, top: '20px', right: '20px', backgroundColor: 'var(--card-bg)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
              <div>
                <span className="badge badge-volt" style={{ marginBottom: '8px' }}>Détail du Secteur</span>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>Zone Couverte</h3>
              </div>
              <button 
                onClick={() => setSelectedTerritory(null)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  cursor: 'pointer',
                  fontSize: '1.4rem',
                  lineHeight: '1'
                }}
              >
                ×
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '0.9rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Utilisateur :</span>
                <span style={{ fontWeight: 700, color: 'var(--text-white)' }}>{selectedTerritory.joueur} <span style={{ color: 'var(--primary-green)', fontSize: '0.8rem', fontFamily: 'monospace' }}>{selectedTerritory.tag}</span></span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Groupe / Équipe :</span>
                <span style={{ fontWeight: 700, color: 'var(--text-white)' }}>{selectedTerritory.clan}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Superficie :</span>
                <span style={{ fontWeight: 700, color: 'var(--primary-green)' }}>{(selectedTerritory.superficie / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '4px' }}>
                <span style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Calendar size={14} /> Mis à jour :
                </span>
                <span style={{ color: 'var(--text-white)', fontSize: '0.85rem' }}>
                  {new Date(selectedTerritory.date_mise_a_jour || Date.now()).toLocaleDateString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>

              {selectedTerritory.utilisateur_id && (
                <button 
                  onClick={() => {
                    const p = profiles.find(prof => prof.id === selectedTerritory.utilisateur_id);
                    if (p) {
                      setSelectedProfile(p);
                    } else {
                      alert('Profil non trouvé ou introuvable.');
                    }
                  }}
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '12px', padding: '8px 12px', fontSize: '0.85rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                >
                  <User size={14} /> Voir le profil détaillé
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Profile Detailed Modal Overlay */}
      {selectedProfile && (
        <div className="modal-overlay" style={{ zIndex: 1000 }}>
          <div className="modal-content glass-card" style={{ 
            border: '1px solid var(--border-color)',
            maxWidth: '700px',
            padding: '0',
            overflow: 'hidden'
          }}>
            {/* Banner Header */}
            <div style={{
              background: 'var(--card-bg)',
              padding: '24px',
              borderBottom: '1px solid var(--border-color)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                {selectedProfile.avatar_url ? (
                  <img src={selectedProfile.avatar_url} alt="" className="avatar avatar-large" style={{ borderColor: 'var(--primary-green)' }} />
                ) : (
                  <div className="avatar avatar-large avatar-placeholder" style={{ borderColor: 'var(--primary-green)', color: 'var(--primary-green)' }}>
                    {selectedProfile.pseudonyme ? selectedProfile.pseudonyme.substring(0, 2).toUpperCase() : 'US'}
                  </div>
                )}
                <div>
                  <h2 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-white)' }}>
                    {selectedProfile.pseudonyme || 'Utilisateur'}
                  </h2>
                  <p style={{ color: 'var(--primary-green)', fontSize: '0.85rem', fontFamily: 'monospace', fontWeight: 700, marginTop: '2px' }}>
                    TAG: {selectedProfile.tag || 'NON DÉFINI'}
                  </p>
                </div>
              </div>
              
              <button 
                onClick={() => setSelectedProfile(null)}
                style={{
                  background: 'rgba(255,255,255,0.02)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--text-muted)',
                  borderRadius: '50%',
                  width: '32px',
                  height: '32px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  fontSize: '1.2rem'
                }}
              >
                ×
              </button>
            </div>

            {/* Notification messages inside modal */}
            {message && (
              <div style={{
                margin: '16px 24px 0 24px',
                padding: '10px 16px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                backgroundColor: message.type === 'success' ? 'rgba(204, 255, 0, 0.05)' : 'rgba(255, 75, 75, 0.05)',
                border: message.type === 'success' ? '1px solid var(--primary-green)' : '1px solid #FF4B4B',
                color: message.type === 'success' ? 'var(--primary-green)' : '#FF4B4B'
              }}>
                {message.text}
              </div>
            )}

            {/* Modal Tabs Panel */}
            <div style={{ 
              display: 'flex', 
              background: 'rgba(255, 255, 255, 0.01)', 
              borderBottom: '1px solid var(--border-color)',
              padding: '0 24px',
              overflowX: 'auto',
              scrollbarWidth: 'none'
            }}>
              <button 
                onClick={() => setActiveTab('apercu')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'apercu' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'apercu' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 16px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  whiteSpace: 'nowrap'
                }}
              >
                <User size={14} /> Aperçu
              </button>
              <button 
                onClick={() => setActiveTab('conquete')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'conquete' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'conquete' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 16px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  whiteSpace: 'nowrap'
                }}
              >
                <Award size={14} /> Conquête
              </button>
              <button 
                onClick={() => setActiveTab('entrainement')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'entrainement' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'entrainement' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 16px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  whiteSpace: 'nowrap'
                }}
              >
                <Activity size={14} /> Activités ({courses.length})
              </button>
              <button 
                onClick={() => setActiveTab('amis')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'amis' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'amis' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 16px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  whiteSpace: 'nowrap'
                }}
              >
                <Users size={14} /> Relations ({friends.length})
              </button>
              <button 
                onClick={() => setActiveTab('parametres')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'parametres' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'parametres' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 16px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  whiteSpace: 'nowrap'
                }}
              >
                <ShieldAlert size={14} /> Modération
              </button>
            </div>

            {/* Modal Body Container */}
            <div style={{ padding: '24px', maxHeight: '550px', overflowY: 'auto' }}>
              
              {/* TAB 1: APERCU */}
              {activeTab === 'apercu' && (() => {
                const currentLevel = selectedProfile.level || 1;
                const currentXp = selectedProfile.xp || 0;
                const prevLvlXp = 250 * Math.pow(currentLevel - 1, 2);
                const nextLvlXp = 250 * Math.pow(currentLevel, 2);
                const xpDiff = nextLvlXp - prevLvlXp;
                const xpProgress = xpDiff > 0 ? Math.min(100, Math.max(0, ((currentXp - prevLvlXp) / xpDiff) * 100)) : 100;

                return (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    {/* Streak & Status Header */}
                    <div style={{ 
                      display: 'flex', 
                      justifyContent: 'space-between', 
                      alignItems: 'center',
                      background: 'rgba(204, 255, 0, 0.03)',
                      padding: '16px',
                      borderRadius: '8px',
                      border: '1px solid rgba(204, 255, 0, 0.15)'
                    }}>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Assiduité</span>
                        <p style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                          🔥 {streak} {streak > 1 ? 'jours consécutifs' : 'jour actif'}
                        </p>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Mode Fantôme</span>
                        <p style={{ 
                          fontSize: '0.9rem', 
                          fontWeight: 700, 
                          color: selectedProfile.ghost_mode ? 'var(--primary-green)' : 'var(--text-muted)', 
                          marginTop: '2px' 
                        }}>
                          {selectedProfile.ghost_mode ? 'ACTIVÉ (INVISIBLE) 👻' : 'DÉSACTIVÉ (VISIBLE)'}
                        </p>
                      </div>
                    </div>

                    {/* Level & XP Progression Card */}
                    <div style={{ 
                      background: 'rgba(255, 255, 255, 0.02)', 
                      padding: '20px', 
                      borderRadius: '8px', 
                      border: '1px solid var(--border-color)' 
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Niveau actuel</span>
                          <h3 style={{ fontSize: '1.8rem', fontWeight: 900, color: 'var(--text-white)' }}>
                            Lvl {currentLevel}
                          </h3>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Expérience globale</span>
                          <p style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--primary-green)' }}>
                            {currentXp} / {nextLvlXp} XP
                          </p>
                        </div>
                      </div>

                      {/* XP Progress Bar */}
                      <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px', overflow: 'hidden' }}>
                        <div style={{ width: `${xpProgress}%`, height: '100%', background: 'var(--primary-green)', borderRadius: '4px', transition: 'width 0.3s ease' }} />
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '6px' }}>
                        <span>Lvl {currentLevel} ({prevLvlXp} XP)</span>
                        <span>Lvl {currentLevel + 1} ({nextLvlXp} XP)</span>
                      </div>
                    </div>

                    {/* Information Grid */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                        <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px', fontWeight: 700 }}>Profil Réseau</h4>
                        
                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Identifiant Unique (UUID)</span>
                          <p style={{ fontSize: '0.8rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px', wordBreak: 'break-all' }}>{selectedProfile.id}</p>
                        </div>

                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Date d'inscription</span>
                          <p style={{ fontSize: '0.85rem', color: 'var(--text-white)', marginTop: '2px' }}>
                            {selectedProfile.date_inscription ? new Date(selectedProfile.date_inscription).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Inconnue'}
                          </p>
                        </div>

                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Couleur personnalisée</span>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                            <span style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '3px', backgroundColor: selectedProfile.empire_color || '#CCFF00' }} />
                            <span style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)' }}>{selectedProfile.empire_color || '#CCFF00'}</span>
                          </div>
                        </div>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                        <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px', fontWeight: 700 }}>Position GPS</h4>
                        
                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Partage de position</span>
                          <p style={{ fontSize: '0.85rem', fontWeight: 'bold', color: selectedProfile.share_location ? 'var(--primary-green)' : '#FF4B4B', marginTop: '2px' }}>
                            {selectedProfile.share_location ? 'EN LIGNE' : 'HORS LIGNE / ANONYME'}
                          </p>
                        </div>

                        <div>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Coordonnées en direct</span>
                          <p style={{ fontSize: '0.8rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px' }}>
                            {selectedProfile.latitude && selectedProfile.longitude ? (
                              `${selectedProfile.latitude.toFixed(6)}, ${selectedProfile.longitude.toFixed(6)}`
                            ) : (
                              'Aucun point GPS stocké'
                            )}
                          </p>
                        </div>

                        {selectedProfile.share_location && selectedProfile.latitude && selectedProfile.longitude && (
                          <button 
                            className="btn btn-secondary" 
                            onClick={() => centerPlayerOnMap(selectedProfile.latitude!, selectedProfile.longitude!)}
                            style={{ width: '100%', marginTop: '6px', padding: '8px 12px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                          >
                            <MapPin size={12} /> Centrer la carte globale sur le joueur
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })()}

              {/* TAB 2: CONQUETE */}
              {activeTab === 'conquete' && (() => {
                const totalDistanceMeters = courses.reduce((acc, c) => acc + (c.distance_totale || 0), 0);
                const totalDistanceKm = totalDistanceMeters / 1000.0;
                const yieldVal = totalDistanceKm > 0 ? (selectedProfile.all_time_area_m2 || 0) / totalDistanceKm : 0;
                
                const formatArea = (val: number) => {
                  if (val >= 10000) {
                    return `${(val / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²`;
                  }
                  return `${val.toLocaleString('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} m²`;
                };

                return (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                      
                      {/* Current Area Widget */}
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Contrôlée</span>
                        <p style={{ fontSize: '1.4rem', fontWeight: 900, color: 'var(--primary-green)', marginTop: '4px' }}>
                          {formatArea(selectedProfile.total_area_m2 || 0)}
                        </p>
                        <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>Superficie actuelle active</p>
                      </div>

                      {/* Historic Max Area Widget */}
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Max Historique</span>
                        <p style={{ fontSize: '1.4rem', fontWeight: 900, color: 'var(--text-white)', marginTop: '4px' }}>
                          {formatArea(selectedProfile.max_area_m2 || 0)}
                        </p>
                        <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>Record historique absolu</p>
                      </div>

                      {/* All Time Cumulative Area Widget */}
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Cumulée Totale</span>
                        <p style={{ fontSize: '1.4rem', fontWeight: 900, color: 'var(--text-white)', marginTop: '4px' }}>
                          {formatArea(selectedProfile.all_time_area_m2 || 0)}
                        </p>
                        <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>Somme de toutes les zones closes</p>
                      </div>

                      {/* Area Lost Widget */}
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Territoire Perdu (Fragmenté)</span>
                        <p style={{ fontSize: '1.4rem', fontWeight: 900, color: '#FF4B4B', marginTop: '4px' }}>
                          {formatArea(selectedProfile.area_lost_m2 || 0)}
                        </p>
                        <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>Territoire volé par d'autres clans</p>
                      </div>

                    </div>

                    {/* Paper.io Loops & Yield Stats */}
                    <div style={{ 
                      background: 'rgba(255,255,255,0.01)', 
                      padding: '20px', 
                      borderRadius: '8px', 
                      border: '1px solid var(--border-color)' 
                    }}>
                      <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px', fontWeight: 700, marginBottom: '14px' }}>Rendement & Boucles</h4>
                      
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
                        <div>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Boucles Fermées</span>
                          <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                            {selectedProfile.loop_count || 0}
                          </p>
                        </div>
                        <div>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Boucle Max (km)</span>
                          <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                            {(selectedProfile.max_loop_distance_km || 0).toFixed(2)} km
                          </p>
                        </div>
                        <div>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Rendement Moyen</span>
                          <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>
                            {yieldVal.toFixed(1)} m²/km
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* Clan & Grade */}
                    <div style={{ display: 'flex', gap: '16px', background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <div style={{ flex: 1 }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Clan / Guilde</span>
                        <p style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-white)', marginTop: '2px' }}>
                          {(() => {
                            const guild = guilds.find(g => g.id === selectedProfile.guilde_id);
                            return guild ? `${guild.nom} [${guild.tag || 'SANS TAG'}]` : 'Sans clan';
                          })()}
                        </p>
                      </div>
                      <div style={{ flex: 1 }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Rôle du clan</span>
                        <p style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--primary-green)', marginTop: '2px' }}>
                          {selectedProfile.grade === 'chef' ? '👑 Chef de guilde' : selectedProfile.grade === 'adjoint' ? '👥 Adjoint de guilde' : selectedProfile.guilde_id ? 'Membre standard' : 'Aucun rôle'}
                        </p>
                      </div>
                    </div>

                  </div>
                );
              })()}

              {/* TAB 3: ACTIVITIES & RUN DETAILS */}
              {activeTab === 'entrainement' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Cumulative stats block */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.05em' }}>Statistiques d'Activité Cumulées</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Sessions</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{totalRuns}</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Distance Totale</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{totalDistanceKm.toFixed(2)} km</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Temps Cumulé</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>{formatDurationText(totalDurationSec)}</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Vitesse Moyenne</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{avgSpeed.toFixed(1)} km/h</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Calories</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{Math.round(totalCalories)} kcal</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Dénivelé Positif</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>+{Math.round(totalElevationPos)}m</p>
                      </div>
                    </div>
                  </div>

                  {/* Graphic Performance */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.05em' }}>Graphique d'Évolution (Distance & Temps)</h4>
                    <div className="glass-card" style={{ padding: '16px', background: 'rgba(255, 255, 255, 0.01)', borderRadius: '8px', minHeight: '220px' }}>
                      {mounted && chartData.length > 0 ? (
                        <div style={{ width: '100%', height: 220 }}>
                          <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={chartData} margin={{ top: 10, right: 5, left: -20, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.03)" />
                              <XAxis dataKey="date" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} />
                              <YAxis yAxisId="left" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} unit=" km" />
                              <YAxis yAxisId="right" orientation="right" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} unit=" min" />
                              <Tooltip 
                                contentStyle={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                                labelStyle={{ color: '#FFFFFF', fontWeight: 'bold' }}
                              />
                              <Line yAxisId="left" type="monotone" dataKey="distance" name="Distance" stroke="var(--primary-green)" strokeWidth={2} dot={{ fill: 'var(--primary-green)', strokeWidth: 1, r: 3 }} activeDot={{ r: 5 }} />
                              <Line yAxisId="right" type="monotone" dataKey="duration" name="Durée" stroke="#FFFFFF" strokeWidth={2} dot={{ fill: '#FFFFFF', strokeWidth: 1, r: 3 }} activeDot={{ r: 5 }} />
                            </LineChart>
                          </ResponsiveContainer>
                        </div>
                      ) : (
                        <div style={{ height: 180, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                          Aucune donnée d'activité à afficher.
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Search and Filters on runs */}
                  <div style={{ 
                    border: '1px solid var(--border-color)', 
                    borderRadius: '6px', 
                    padding: '12px 16px', 
                    background: 'rgba(255, 255, 255, 0.01)',
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr',
                    gap: '16px'
                  }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Distance Min (km)</span>
                      <input 
                        type="number" 
                        step="0.5"
                        min="0"
                        value={minDistance || ''}
                        onChange={(e) => setMinDistance(parseFloat(e.target.value) || 0)}
                        className="input-field"
                        style={{ padding: '6px 10px', fontSize: '0.85rem' }}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Type d'activité</span>
                      <select 
                        value={loopOnlyFilter}
                        onChange={(e: any) => setLoopOnlyFilter(e.target.value)}
                        className="input-field"
                        style={{ padding: '6px 10px', fontSize: '0.85rem' }}
                      >
                        <option value="all">Toutes les sessions</option>
                        <option value="loops">Bouclées uniquement</option>
                        <option value="noloops">Incomplètes uniquement</option>
                      </select>
                    </div>
                  </div>

                  {/* Course list */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.05em' }}>Historique des activités ({filteredCourses.length})</h4>
                    
                    {loadingCourses ? (
                      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chargement des sessions...</p>
                    ) : filteredCourses.length === 0 ? (
                      <div style={{ padding: '24px', textAlign: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Aucune session ne correspond aux critères.</p>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {filteredCourses.map((c) => {
                          const durationMin = Math.floor(c.duree_secondes / 60);
                          const rawSec = c.duree_secondes % 60;
                          const durationSec = Math.round(rawSec * 100) / 100;
                          const distKm = c.distance_totale / 1000;
                          return (
                            <div 
                              key={c.id}
                              style={{
                                background: 'rgba(255, 255, 255, 0.01)',
                                border: '1px solid var(--border-color)',
                                borderRadius: '8px',
                                padding: '14px 16px',
                              }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                                <div>
                                  <p style={{ fontWeight: 700, fontSize: '0.9rem' }}>
                                    Activité du {new Date(c.date_debut).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })}
                                  </p>
                                  <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                    Statut : {c.est_bouclee ? (
                                      <span style={{ color: 'var(--primary-green)' }}>Bouclée (Terminée)</span>
                                    ) : (
                                      <span style={{ color: 'var(--text-muted)' }}>Non bouclée</span>
                                    )}
                                  </p>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                  <div style={{ textAlign: 'right' }}>
                                    <p style={{ fontWeight: 800, color: 'var(--text-white)', fontSize: '1.05rem' }}>{distKm.toFixed(2)} km</p>
                                    <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                      {durationMin}m {durationSec}s
                                    </p>
                                  </div>
                                  <button 
                                    onClick={() => handleDeleteCourse(c.id)}
                                    disabled={actionLoading}
                                    style={{
                                      background: 'rgba(255, 75, 75, 0.05)',
                                      border: '1px solid rgba(255, 75, 75, 0.1)',
                                      borderRadius: '6px',
                                      color: '#FF4B4B',
                                      padding: '6px',
                                      cursor: 'pointer',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      transition: 'all 0.2s',
                                      opacity: actionLoading ? 0.5 : 1
                                    }}
                                    title="Supprimer cette activité"
                                  >
                                    <Trash2 size={16} />
                                  </button>
                                </div>
                              </div>
                              
                              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px', marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--border-color)' }}>
                                <div style={{ textAlign: 'center' }}>
                                  <p style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Vit. Moy</p>
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--primary-green)' }}>{(c.vitesse_moyenne || 0).toFixed(1)} km/h</p>
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                  <p style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Allure</p>
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--text-white)' }}>{(c.allure_moyenne || 0).toFixed(1)} min/km</p>
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                  <p style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Calories</p>
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--text-white)' }}>{Math.round(c.calories_estimees || 0)} kcal</p>
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                  <p style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>D+/D-</p>
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--primary-green)' }}>+{Math.round(c.denivele_positif || 0)}/-{Math.round(c.denivele_negatif || 0)}m</p>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>

                </div>
              )}

              {/* TAB 4: RELATIONS (FRIENDS LIST) */}
              {activeTab === 'amis' && (
                <div>
                  <h4 style={{ fontSize: '0.85rem', color: 'var(--primary-green)', textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px', fontWeight: 700, marginBottom: '14px' }}>Liste d'amis</h4>
                  {loadingFriends ? (
                    <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>Chargement des relations...</p>
                  ) : friends.length === 0 ? (
                    <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>Aucun ami dans la liste</p>
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                      {friends.map(f => (
                        <div 
                          key={f.id} 
                          onClick={() => {
                            setSelectedProfile(f);
                            setActiveTab('apercu');
                          }}
                          style={{ 
                            display: 'flex', 
                            alignItems: 'center', 
                            gap: '10px', 
                            border: '1px solid var(--border-color)', 
                            padding: '10px', 
                            borderRadius: '6px', 
                            background: 'rgba(255, 255, 255, 0.01)',
                            cursor: 'pointer',
                            transition: 'all 0.2s'
                          }}
                          className="friend-card-hover"
                        >
                          {f.avatar_url ? (
                            <img src={f.avatar_url} alt="" className="avatar" style={{ borderColor: f.empire_color || '#CCFF00' }} />
                          ) : (
                            <div className="avatar avatar-placeholder" style={{ borderColor: f.empire_color || '#CCFF00', color: f.empire_color || '#CCFF00', fontSize: '0.75rem' }}>
                              {f.pseudonyme ? f.pseudonyme.substring(0, 2).toUpperCase() : 'US'}
                            </div>
                          )}
                          <div>
                            <p style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-white)' }}>{f.pseudonyme || 'Utilisateur'}</p>
                            <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>TAG: {f.tag || 'AUCUN'}</p>
                          </div>
                          <ChevronRight size={14} style={{ marginLeft: 'auto', color: 'var(--text-muted)' }} />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* TAB 5: MODERATION (PARAMETRES) */}
              {activeTab === 'parametres' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  
                  {/* Danger Zone Actions */}
                  <div style={{ display: 'flex', gap: '12px', background: 'rgba(255, 75, 75, 0.03)', padding: '12px', borderRadius: '6px', border: '1px solid rgba(255, 75, 75, 0.2)' }}>
                    {selectedProfile.avatar_url && (
                      <button className="btn btn-secondary" onClick={handleRemoveAvatar} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem', borderColor: 'rgba(255,255,255,0.1)' }}>
                        <ImageIcon size={12} /> Supprimer l'avatar
                      </button>
                    )}
                    <button className="btn btn-danger" onClick={() => handleDeleteUser(selectedProfile.id)} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem', marginLeft: 'auto' }}>
                      <Trash2 size={12} /> Supprimer définitivement l'utilisateur
                    </button>
                  </div>

                  {/* Form fields for updating database values */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    
                    {/* Identity parameters */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Pseudonyme</label>
                      <input 
                        type="text" 
                        className="input-field" 
                        value={newPseudonyme}
                        onChange={(e) => setNewPseudonyme(e.target.value)}
                        placeholder="Pseudonyme"
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Tag de guilde / Clan</label>
                      <input 
                        type="text" 
                        className="input-field" 
                        value={newTag}
                        onChange={(e) => setNewTag(e.target.value)}
                        placeholder="Tag (ex: FR)"
                      />
                    </div>

                    {/* Guild / Grade controls */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Rôle dans la guilde (Grade)</label>
                      <select 
                        className="input-field" 
                        value={newGrade}
                        onChange={(e) => setNewGrade(e.target.value)}
                      >
                        <option value="">Utilisateur standard (Aucun rôle)</option>
                        <option value="chef">Chef de guilde (Responsable)</option>
                        <option value="adjoint">Adjoint de guilde</option>
                      </select>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Clan associé (Guilde)</label>
                      <select 
                        className="input-field" 
                        value={newGuildeId}
                        onChange={(e) => setNewGuildeId(e.target.value)}
                      >
                        <option value="">Aucune guilde</option>
                        {guilds.map(g => (
                          <option key={g.id} value={g.id}>{g.nom}</option>
                        ))}
                      </select>
                    </div>

                    {/* Progression parameters */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Points d'Expérience (XP)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newXp}
                        onChange={(e) => setNewXp(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Niveau (Level)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newLevel}
                        onChange={(e) => setNewLevel(Number(e.target.value))}
                      />
                    </div>

                    {/* Color picker */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Couleur d'empire (Hexadecimal)</label>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <input 
                          type="color" 
                          style={{ width: '40px', height: '38px', padding: '0', border: '1px solid var(--border-color)', borderRadius: '6px', background: 'none', cursor: 'pointer' }}
                          value={newEmpireColor}
                          onChange={(e) => setNewEmpireColor(e.target.value)}
                        />
                        <input 
                          type="text" 
                          className="input-field" 
                          style={{ flex: 1, fontFamily: 'monospace' }}
                          value={newEmpireColor}
                          onChange={(e) => setNewEmpireColor(e.target.value)}
                          placeholder="#CCFF00"
                        />
                      </div>
                    </div>

                    {/* Conquest metrics updates */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Superficie Actuelle (m²)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newTotalArea}
                        onChange={(e) => setNewTotalArea(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Superficie Cumulée All-Time (m²)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newAllTimeArea}
                        onChange={(e) => setNewAllTimeArea(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Superficie Max Historique (m²)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newMaxArea}
                        onChange={(e) => setNewMaxArea(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Superficie Perdue Cumulée (m²)</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newAreaLost}
                        onChange={(e) => setNewAreaLost(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Nombre de Boucles Fermées</label>
                      <input 
                        type="number" 
                        className="input-field" 
                        value={newLoopCount}
                        onChange={(e) => setNewLoopCount(Number(e.target.value))}
                      />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Distance de Boucle Max (km)</label>
                      <input 
                        type="number" 
                        step="0.01"
                        className="input-field" 
                        value={newMaxLoopDistance}
                        onChange={(e) => setNewMaxLoopDistance(Number(e.target.value))}
                      />
                    </div>

                    {/* Switches */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '14px' }}>
                      <input 
                        type="checkbox" 
                        id="ghost_mode_check"
                        checked={newGhostMode}
                        onChange={(e) => setNewGhostMode(e.target.checked)}
                        style={{ cursor: 'pointer', accentColor: 'var(--primary-green)' }}
                      />
                      <label htmlFor="ghost_mode_check" style={{ fontSize: '0.85rem', color: 'var(--text-white)', cursor: 'pointer' }}>
                        Mode Fantôme (Invisible sur la carte publique)
                      </label>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '14px' }}>
                      <input 
                        type="checkbox" 
                        id="share_location_check"
                        checked={newShareLocation}
                        onChange={(e) => setNewShareLocation(e.target.checked)}
                        style={{ cursor: 'pointer', accentColor: 'var(--primary-green)' }}
                      />
                      <label htmlFor="share_location_check" style={{ fontSize: '0.85rem', color: 'var(--text-white)', cursor: 'pointer' }}>
                        Activer le partage GPS
                      </label>
                    </div>

                  </div>

                  {/* Save button */}
                  <button 
                    className="btn btn-primary" 
                    onClick={handleUpdateProfile} 
                    disabled={actionLoading}
                    style={{ width: '100%', padding: '12px', fontSize: '0.9rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginTop: '10px' }}
                  >
                    <Save size={16} /> 
                    {actionLoading ? 'Mise à jour en cours...' : 'Enregistrer toutes les modifications'}
                  </button>

                </div>
              )}

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
