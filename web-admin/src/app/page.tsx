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
  MapPin
} from 'lucide-react';
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

export default function DashboardPage() {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);

  // State
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTerritory, setSelectedTerritory] = useState<any>(null);
  const [geoTerritories, setGeoTerritories] = useState<TerritoryGeoJSON[]>([]);

  // Stats
  const [totalArea, setTotalArea] = useState(0);

  // Initial fetch
  useEffect(() => {
    async function loadData() {
      try {
        const [
          { data: profilesData },
          { data: guildsData },
          { data: territoriesData }
        ] = await Promise.all([
          supabase.from('profiles').select('*'),
          supabase.from('guildes').select('*'),
          supabase.from('territoires').select('*')
        ]);

        const profilesList = (profilesData || []) as Profile[];
        const guildsList = (guildsData || []) as Guild[];
        const territoriesList = (territoriesData || []) as Territory[];

        setProfiles(profilesList);
        setGuilds(guildsList);
        setTerritories(territoriesList);

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

    // Subscribe to real-time updates for coordinates & territories
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

    return () => {
      supabase.removeChannel(profileChannel);
      supabase.removeChannel(territoryChannel);
    };
  }, []);

  // Initialize Mapbox map
  useEffect(() => {
    if (loading || !mapContainer.current || mapRef.current) return;

    // Default center (Paris/France or generic center)
    const map = new mapboxgl.Map({
      container: mapContainer.current,
      style: 'mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8',
      center: [2.3522, 48.8566], // Paris
      zoom: 3,
      projection: { name: 'globe' } // Beautiful 3D globe projection
    });

    map.on('style.load', () => {
      // Set atmospheric glow on the globe
      map.setFog({
        color: 'rgb(15, 19, 24)', // BackgroundDark
        'high-color': 'rgb(30, 36, 44)',
        'horizon-blend': 0.02,
        'space-color': 'rgb(10, 10, 12)',
        'star-intensity': 0.6
      });
    });

    mapRef.current = map;

    // Map controls
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

    // Wait until map style is loaded
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

      // Update source
      if (map.getSource('territories')) {
        (map.getSource('territories') as mapboxgl.GeoJSONSource).setData(geojson);
      } else {
        map.addSource('territories', {
          type: 'geojson',
          data: geojson
        });

        // Add fill layer (semitransparent conquis regions)
        map.addLayer({
          id: 'territories-fill',
          type: 'fill',
          source: 'territories',
          paint: {
            'fill-color': ['get', 'color'],
            'fill-opacity': 0.45
          }
        });

        // Add stroke line layer (neon glowing outline border)
        map.addLayer({
          id: 'territories-stroke',
          type: 'line',
          source: 'territories',
          paint: {
            'line-color': ['get', 'color'],
            'line-width': 2.5
          }
        });

        // Handle click on polygons
        map.on('click', 'territories-fill', (e) => {
          const feature = e.features?.[0];
          if (feature) {
            setSelectedTerritory(feature.properties);
          }
        });

        // Pointer cursor hover
        map.on('mouseenter', 'territories-fill', () => {
          map.getCanvas().style.cursor = 'pointer';
        });
        map.on('mouseleave', 'territories-fill', () => {
          map.getCanvas().style.cursor = '';
        });
      }

      // --- 2. RENDER ACTIVE PLAYER MARKERS ---
      // Clear old markers
      markersRef.current.forEach(m => m.remove());
      markersRef.current = [];

      profiles.forEach(p => {
        if (p.share_location && p.latitude !== null && p.longitude !== null) {
          const guild = guilds.find(g => g.id === p.guilde_id);
          const color = guild?.couleur_hex || p.empire_color || '#00E5FF';

          // Create custom glowing marker element
          const el = document.createElement('div');
          el.className = 'player-marker';
          el.style.width = '16px';
          el.style.height = '16px';
          el.style.borderRadius = '50%';
          el.style.backgroundColor = color;
          el.style.border = '2px solid #FFFFFF';
          el.style.boxShadow = `0 0 10px ${color}, 0 0 20px ${color}`;
          el.style.cursor = 'pointer';

          // Create popup
          const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(`
            <div style="font-family: var(--font-outfit)">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${color}"></div>
                <strong style="font-size: 0.95rem; color: #FFFFFF;">${p.pseudonyme || 'Soldat'}</strong>
                <span style="font-size: 0.7rem; color: #8E9BAE; font-family: monospace;">${(p as any).tag || ''}</span>
              </div>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0 0 4px 0;">Clan: ${guild?.nom || 'Autonome'}</p>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0;">Lat: ${p.latitude!.toFixed(5)}, Lng: ${p.longitude!.toFixed(5)}</p>
            </div>
          `);

          // Append to map
          const marker = new mapboxgl.Marker({ element: el })
            .setLngLat([p.longitude, p.latitude])
            .setPopup(popup)
            .addTo(map);

          markersRef.current.push(marker);
        }
      });
    }
  }, [geoTerritories, profiles, guilds, loading]);

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '80vh' }}>
        <p style={{ color: 'var(--text-muted)' }}>Chargement des données cartographiques...</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Réseau Global</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Surveillance territoriale et géolocalisation en temps réel</p>
        </div>
      </div>

      {/* Metrics Row */}
      <div className="dashboard-grid">
        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(204, 255, 0, 0.1)',
            color: 'var(--neon-volt)'
          }}>
            <Users size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Joueurs Inscrits</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{profiles.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(0, 229, 255, 0.1)',
            color: 'var(--electric-blue)'
          }}>
            <Swords size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Clans Actifs</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{guilds.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(255, 109, 0, 0.1)',
            color: 'var(--active-orange)'
          }}>
            <MapIcon size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Zones Capturées</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{territories.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'rgba(255, 255, 255, 0.05)',
            color: 'var(--text-white)'
          }}>
            <TrendingUp size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Superficie Totale</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
              {(totalArea / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
            </p>
          </div>
        </div>
      </div>

      {/* Main Map Panel */}
      <div className="map-container-wrapper">
        <div ref={mapContainer} className="map-viewport" />

        {/* Territory Inspector Side Panel */}
        {selectedTerritory && (
          <div className="map-overlay-panel glass-card" style={{ padding: '20px', borderLeft: `3px solid ${selectedTerritory.color}` }}>
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
                  fontSize: '1.1rem'
                }}
              >
                ×
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '0.9rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Joueur :</span>
                <span style={{ fontWeight: 600, color: 'var(--text-white)' }}>{selectedTerritory.joueur}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Clan / Empire :</span>
                <span style={{ fontWeight: 600, color: selectedTerritory.color }}>{selectedTerritory.clan}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Superficie :</span>
                <span style={{ fontWeight: 600, color: 'var(--text-white)' }}>{(selectedTerritory.superficie / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '4px' }}>
                <span style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Calendar size={14} /> Mis à jour :
                </span>
                <span style={{ color: 'var(--text-white)', fontSize: '0.85rem' }}>{selectedTerritory.date_mise_a_jour}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
