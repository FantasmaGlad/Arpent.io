'use client';

import React, { useEffect, useRef, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Users, 
  Swords, 
  Map as MapIcon, 
  Activity, 
  TrendingUp, 
  Navigation, 
  ShieldAlert, 
  Calendar,
  Layers,
  MapPin,
  Clock,
  Zap,
  Globe,
  Award
} from 'lucide-react';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell,
  AreaChart,
  Area,
  CartesianGrid
} from 'recharts';

// Set mapbox token
mapboxgl.accessToken = process.env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN || '';

interface Profile {
  id: string;
  pseudonyme: string | null;
  tag: string | null;
  guilde_id: string | null;
  total_area_m2: number;
  share_location: boolean;
  avatar_url: string | null;
  empire_color: string;
  latitude: number | null;
  longitude: number | null;
  grade: string | null;
}

interface Guild {
  id: string;
  nom: string;
  tag: string | null;
  couleur_hex: string;
  avatar_url: string | null;
}

interface Territory {
  id: string;
  utilisateur_id: string;
  guilde_id: string | null;
  superficie_m2: number;
  points: string[];
  derniere_mise_a_jour: string;
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

interface CourseWithProfile {
  id: string;
  utilisateur_id: string;
  date_debut: string;
  duree_secondes: number;
  distance_totale: number;
  vitesse_moyenne: number;
  allure_moyenne: number;
  calories_estimees: number;
  denivele_positif: number;
  denivele_negatif: number;
  est_bouclee: boolean;
  profiles: {
    pseudonyme: string | null;
    avatar_url: string | null;
    empire_color: string;
    tag: string | null;
  } | null;
}

export default function DashboardPage() {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);

  // State
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [courses, setCourses] = useState<CourseWithProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTerritory, setSelectedTerritory] = useState<any>(null);
  const [geoTerritories, setGeoTerritories] = useState<TerritoryGeoJSON[]>([]);
  
  // Tab for Recharts view
  const [activeChartTab, setActiveChartTab] = useState<'empires' | 'activity'>('empires');
  const [mounted, setMounted] = useState(false);

  // Stats
  const [totalArea, setTotalArea] = useState(0);

  useEffect(() => {
    setMounted(true);
  }, []);

  // Initial fetch
  useEffect(() => {
    async function loadData() {
      try {
        const [
          { data: profilesData },
          { data: guildsData },
          { data: territoriesData },
          { data: coursesData }
        ] = await Promise.all([
          supabase.from('profiles').select('*'),
          supabase.from('guildes').select('*'),
          supabase.from('territoires').select('*'),
          supabase.from('courses')
            .select('*, profiles(pseudonyme, avatar_url, empire_color, tag)')
            .order('date_debut', { ascending: false })
            .limit(15)
        ]);

        const profilesList = (profilesData || []) as Profile[];
        const guildsList = (guildsData || []) as Guild[];
        const territoriesList = (territoriesData || []) as Territory[];
        const coursesList = (coursesData || []) as any[];

        setProfiles(profilesList);
        setGuilds(guildsList);
        setTerritories(territoriesList);
        setCourses(coursesList);

        // Calc total area
        const total = territoriesList.reduce((acc, curr) => acc + curr.superficie_m2, 0);
        setTotalArea(total);

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

    // Subscribe to real-time updates for coordinates, territories, and courses
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
        // Reload territories on change
        supabase.from('territoires').select('*').then(({ data }) => {
          if (data) {
            setTerritories(data as Territory[]);
            const total = (data as Territory[]).reduce((acc, curr) => acc + curr.superficie_m2, 0);
            setTotalArea(total);
          }
        });
        // Also refresh GeoJSON
        supabase.rpc('get_territoires_geojson').then(({ data }) => {
          if (data) setGeoTerritories(data as TerritoryGeoJSON[]);
        });
      })
      .subscribe();

    const coursesChannel = supabase.channel('courses-realtime')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'courses' }, async (payload) => {
        const { data: newCourse, error } = await supabase
          .from('courses')
          .select('*, profiles(pseudonyme, avatar_url, empire_color, tag)')
          .eq('id', payload.new.id)
          .single();
        if (newCourse && !error) {
          setCourses(prev => [newCourse as CourseWithProfile, ...prev.slice(0, 14)]);
        }
      })
      .subscribe();

    return () => {
      supabase.removeChannel(profileChannel);
      supabase.removeChannel(territoryChannel);
      supabase.removeChannel(coursesChannel);
    };
  }, []);

  // Initialize Mapbox map
  useEffect(() => {
    if (loading || !mapContainer.current || mapRef.current) return;

    // Default center
    const map = new mapboxgl.Map({
      container: mapContainer.current,
      style: 'mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8',
      center: [2.3522, 48.8566], // Paris
      zoom: 11,
      projection: { name: 'globe' }
    });

    map.on('style.load', () => {
      map.setFog({
        color: 'rgb(15, 19, 24)',
        'high-color': 'rgb(30, 36, 44)',
        'horizon-blend': 0.02,
        'space-color': 'rgb(10, 10, 12)',
        'star-intensity': 0.6
      });

      // Check for redirect target location
      const targetLat = localStorage.getItem('map_center_lat');
      const targetLng = localStorage.getItem('map_center_lng');
      if (targetLat && targetLng) {
        map.flyTo({
          center: [parseFloat(targetLng), parseFloat(targetLat)],
          zoom: 14,
          pitch: 45,
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
              superficie: t.superficie_m2,
              joueur: t.pseudonyme || 'Recrue Anonyme',
              tag: t.tag || '',
              clan: t.guilde_nom || 'Aucun clan',
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
            'fill-opacity': 0.45
          }
        });

        map.addLayer({
          id: 'territories-stroke',
          type: 'line',
          source: 'territories',
          paint: {
            'line-color': ['get', 'color'],
            'line-width': 2.5
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
          const color = guild?.couleur_hex || p.empire_color || '#00E5FF';

          const el = document.createElement('div');
          el.className = 'player-marker';
          el.style.width = '18px';
          el.style.height = '18px';
          el.style.borderRadius = '50%';
          el.style.backgroundColor = color;
          el.style.border = '2px solid #FFFFFF';
          el.style.boxShadow = `0 0 10px ${color}, 0 0 20px ${color}`;
          el.style.cursor = 'pointer';

          const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(`
            <div style="font-family: var(--font-outfit)">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${color}"></div>
                <strong style="font-size: 0.95rem; color: #FFFFFF;">${p.pseudonyme || 'Soldat'}</strong>
                <span style="font-size: 0.7rem; color: #8E9BAE; font-family: monospace;">${p.tag || ''}</span>
              </div>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0 0 4px 0;">Clan: ${guild?.nom || 'Autonome'}</p>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0;">Lat: ${p.latitude!.toFixed(5)}, Lng: ${p.longitude!.toFixed(5)}</p>
            </div>
          `);

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

  // Aggregated charts computations
  const getTopEmpiresData = () => {
    return [...profiles]
      .filter(p => p.total_area_m2 > 0)
      .sort((a, b) => b.total_area_m2 - a.total_area_m2)
      .slice(0, 5)
      .map(p => ({
        name: p.pseudonyme || 'Inconnu',
        area: parseFloat((p.total_area_m2 / 1000000).toFixed(4)),
        color: p.empire_color || '#CCFF00'
      }));
  };

  const getDailyActivityData = () => {
    const dates: { [key: string]: number } = {};
    
    // Last 7 days helper
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      const label = d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
      dates[label] = 0;
    }

    // Populate actual distances
    courses.forEach(c => {
      const dateLabel = new Date(c.date_debut).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
      if (dates[dateLabel] !== undefined) {
        dates[dateLabel] += c.distance_totale / 1000;
      }
    });

    return Object.keys(dates).map(key => ({
      date: key,
      distance: parseFloat(dates[key].toFixed(2))
    }));
  };

  const topEmpiresData = getTopEmpiresData();
  const dailyActivityData = getDailyActivityData();

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh', gap: '16px' }}>
        <div className="avatar avatar-placeholder" style={{ width: '48px', height: '48px', border: '1px solid var(--electric-blue)', animation: 'pulse 1.5s infinite' }} />
        <p style={{ color: 'var(--text-muted)' }}>Initialisation du réseau global...</p>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Cyberpunk Header with Status Banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 className="title-cyber" style={{ fontSize: '2.2rem', textShadow: '0 0 10px rgba(255,255,255,0.1)' }}>Réseau Global</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Surveillance militaire et contrôle territorial d'Arpent.io</p>
        </div>

        {/* Status indicator */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '12px', 
          background: 'rgba(204, 255, 0, 0.05)', 
          border: '1px solid rgba(204, 255, 0, 0.2)', 
          padding: '8px 16px', 
          borderRadius: '8px' 
        }}>
          <span style={{ 
            display: 'inline-block', 
            width: '8px', 
            height: '8px', 
            borderRadius: '50%', 
            backgroundColor: 'var(--neon-volt)',
            boxShadow: '0 0 8px var(--neon-volt)',
            animation: 'beacon 1.8s infinite'
          }} />
          <span style={{ color: 'var(--neon-volt)', fontSize: '0.8rem', fontWeight: 800, fontFamily: 'monospace', textTransform: 'uppercase' }}>
            SYSTÈME ACTIF // LIVE CONNECTED
          </span>
          <style jsx>{`
            @keyframes beacon {
              0% { opacity: 0.4; }
              50% { opacity: 1; }
              100% { opacity: 0.4; }
            }
          `}</style>
        </div>
      </div>

      {/* Stats Cards Row */}
      <div className="dashboard-grid">
        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px', borderLeft: '3px solid var(--electric-blue)' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(0, 229, 255, 0.1)',
            color: 'var(--electric-blue)'
          }}>
            <Users size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Joueurs Enrôlés</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{profiles.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px', borderLeft: '3px solid #FFD700' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(255, 215, 0, 0.1)',
            color: '#FFD700'
          }}>
            <Swords size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Clans Actifs</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{guilds.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px', borderLeft: '3px solid var(--active-orange)' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(255, 109, 0, 0.1)',
            color: 'var(--active-orange)'
          }}>
            <MapIcon size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Secteurs Revendiqués</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{territories.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px', borderLeft: '3px solid var(--neon-volt)' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(204, 255, 0, 0.1)',
            color: 'var(--neon-volt)'
          }}>
            <TrendingUp size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Zone Conquise</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
              {(totalArea / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
            </p>
          </div>
        </div>
      </div>

      {/* Main Map Panel with controls */}
      <div className="map-container-wrapper" style={{ boxShadow: '0 0 25px rgba(0,0,0,0.5)' }}>
        <div ref={mapContainer} className="map-viewport" />

        {/* Reset Map control button */}
        <button 
          onClick={resetMapCenter}
          style={{
            position: 'absolute',
            bottom: '20px',
            left: '80px',
            zIndex: 10,
            backgroundColor: 'rgba(30, 36, 44, 0.85)',
            border: '1px solid var(--border-color)',
            color: 'var(--text-white)',
            padding: '8px 12px',
            borderRadius: '8px',
            fontFamily: 'var(--font-outfit)',
            fontSize: '0.8rem',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.35)',
            transition: 'all 0.2s'
          }}
          onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--electric-blue)'}
          onMouseLeave={(e) => e.currentTarget.style.borderColor = 'var(--border-color)'}
        >
          <Globe size={14} style={{ color: 'var(--electric-blue)' }} /> Centrer Paris
        </button>

        {/* Territory Inspector Side Panel */}
        {selectedTerritory && (
          <div className="map-overlay-panel glass-card" style={{ padding: '20px', borderLeft: `3px solid ${selectedTerritory.color}`, top: '20px', right: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
              <div>
                <span className="badge badge-blue" style={{ marginBottom: '8px' }}>Inspecteur Zone</span>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>Territoire Conquis</h3>
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
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Joueur :</span>
                <span style={{ fontWeight: 700, color: 'var(--text-white)' }}>{selectedTerritory.joueur} <span style={{ color: 'var(--electric-blue)', fontSize: '0.8rem', fontFamily: 'monospace' }}>{selectedTerritory.tag}</span></span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Clan / Empire :</span>
                <span style={{ fontWeight: 700, color: selectedTerritory.color }}>{selectedTerritory.clan}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Superficie :</span>
                <span style={{ fontWeight: 700, color: 'var(--neon-volt)' }}>{(selectedTerritory.superficie / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '4px' }}>
                <span style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Calendar size={14} /> Mis à jour :
                </span>
                <span style={{ color: 'var(--text-white)', fontSize: '0.85rem' }}>
                  {new Date(selectedTerritory.date_mise_a_jour || Date.now()).toLocaleDateString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Sub-Map Double Column Row (Charts & Live Activities) */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: '32px', minHeight: '450px', flexWrap: 'wrap' }}>
        
        {/* Recharts Analytics Panel */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '12px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Zap size={20} style={{ color: 'var(--neon-volt)' }} /> Senseurs Analytiques
            </h3>
            
            <div style={{ display: 'flex', background: 'rgba(15, 19, 24, 0.8)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '2px' }}>
              <button 
                onClick={() => setActiveChartTab('empires')} 
                style={{
                  background: activeChartTab === 'empires' ? 'rgba(0, 229, 255, 0.15)' : 'none',
                  border: 'none',
                  color: activeChartTab === 'empires' ? 'var(--electric-blue)' : 'var(--text-muted)',
                  padding: '6px 12px',
                  borderRadius: '6px',
                  fontFamily: 'var(--font-outfit)',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
              >
                Top Empires
              </button>
              <button 
                onClick={() => setActiveChartTab('activity')} 
                style={{
                  background: activeChartTab === 'activity' ? 'rgba(0, 229, 255, 0.15)' : 'none',
                  border: 'none',
                  color: activeChartTab === 'activity' ? 'var(--electric-blue)' : 'var(--text-muted)',
                  padding: '6px 12px',
                  borderRadius: '6px',
                  fontFamily: 'var(--font-outfit)',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
              >
                Entraînements (7j)
              </button>
            </div>
          </div>

          <div style={{ flexGrow: 1, minHeight: '300px', width: '100%' }}>
            {mounted ? (
              <ResponsiveContainer width="100%" height={300}>
                {activeChartTab === 'empires' ? (
                  <BarChart data={topEmpiresData} margin={{ top: 20, right: 10, left: -10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="name" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} unit=" km²" />
                    <Tooltip 
                      contentStyle={{ background: '#1E242C', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                      labelStyle={{ color: '#FFFFFF', fontWeight: 'bold' }}
                    />
                    <Bar dataKey="area" radius={[4, 4, 0, 0]}>
                      {topEmpiresData.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                ) : (
                  <AreaChart data={dailyActivityData} margin={{ top: 20, right: 10, left: -10, bottom: 5 }}>
                    <defs>
                      <linearGradient id="colorDist" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--electric-blue)" stopOpacity={0.4}/>
                        <stop offset="95%" stopColor="var(--electric-blue)" stopOpacity={0.0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="date" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} unit=" km" />
                    <Tooltip 
                      contentStyle={{ background: '#1E242C', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                      labelStyle={{ color: '#FFFFFF', fontWeight: 'bold' }}
                    />
                    <Area type="monotone" dataKey="distance" stroke="var(--electric-blue)" strokeWidth={3} fillOpacity={1} fill="url(#colorDist)" />
                  </AreaChart>
                )}
              </ResponsiveContainer>
            ) : (
              <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
                Initialisation des capteurs graphiques...
              </div>
            )}
          </div>
        </div>

        {/* Live Network Activity Feed */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Activity size={20} style={{ color: 'var(--active-orange)' }} /> Activité Réseau Direct
          </h3>

          {courses.length === 0 ? (
            <div style={{ flexGrow: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px', padding: '24px' }}>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center' }}>
                Aucune session de course n'est en cours sur le globe d'Arpent.
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto', maxHeight: '330px', paddingRight: '4px' }}>
              {courses.map((item) => {
                const distanceKm = item.distance_totale / 1000;
                const min = Math.floor(item.duree_secondes / 60);
                const sec = item.duree_secondes % 60;
                const avatar = item.profiles?.avatar_url;
                const color = item.profiles?.empire_color || 'var(--text-muted)';
                return (
                  <div 
                    key={item.id}
                    style={{
                      background: 'rgba(15, 19, 24, 0.3)',
                      border: '1px solid var(--border-color)',
                      borderRadius: '8px',
                      padding: '10px 14px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      transition: 'border-color 0.2s'
                    }}
                    className="feed-item"
                  >
                    {avatar ? (
                      <img src={avatar} alt="" className="avatar" style={{ width: '34px', height: '34px', borderColor: color }} />
                    ) : (
                      <div className="avatar avatar-placeholder" style={{ width: '34px', height: '34px', borderColor: color, fontSize: '0.8rem', color: color }}>
                        {item.profiles?.pseudonyme?.substring(0, 2).toUpperCase() || 'SO'}
                      </div>
                    )}
                    
                    <div style={{ flexGrow: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                        <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-white)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {item.profiles?.pseudonyme || 'Recrue'}
                        </span>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                          {new Date(item.date_debut).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', gap: '8px', marginTop: '2px' }}>
                        <span>🏃 {distanceKm.toFixed(2)} km</span>
                        <span>⏱️ {min}m {sec}s</span>
                        {item.est_bouclee && <span style={{ color: 'var(--neon-volt)' }}>🔁 Bouclé</span>}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
