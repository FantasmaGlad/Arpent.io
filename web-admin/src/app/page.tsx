'use client';

import React, { useEffect, useRef, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Calendar,
  Globe,
  Layers
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
          const color = t.guilde_couleur || t.empire_color || '#00875A';
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
          const color = guild?.couleur_hex || p.empire_color || '#00875A';

          const el = document.createElement('div');
          el.className = 'player-marker';
          el.style.width = '14px';
          el.style.height = '14px';
          el.style.borderRadius = '50%';
          el.style.backgroundColor = color;
          el.style.border = '2px solid #000000';
          el.style.boxShadow = `0 2px 6px rgba(0, 0, 0, 0.6)`;
          el.style.cursor = 'pointer';

          const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(`
            <div style="font-family: var(--font-outfit)">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${color}"></div>
                <strong style="font-size: 0.95rem; color: var(--text-white);">${p.pseudonyme || 'Utilisateur'}</strong>
                <span style="font-size: 0.7rem; color: var(--text-muted); font-family: monospace;">${p.tag || ''}</span>
              </div>
              <p style="font-size: 0.75rem; color: var(--text-muted); margin: 0 0 4px 0;">Groupe: ${guild?.nom || 'Indépendant'}</p>
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
            backgroundColor: 'var(--card-bg)',
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
            boxShadow: '0 4px 12px rgba(0,0,0,0.06)',
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
                  onClick={() => window.location.href = `/profiles?userId=${selectedTerritory.utilisateur_id}`}
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '12px', padding: '8px 12px', fontSize: '0.85rem', fontWeight: 'bold' }}
                >
                  Inspecter le profil 👤
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
