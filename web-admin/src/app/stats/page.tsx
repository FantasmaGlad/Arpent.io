'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Users, 
  Map as MapIcon, 
  Activity, 
  TrendingUp, 
  Zap,
  Layers
} from 'lucide-react';
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

export default function StatsPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [courses, setCourses] = useState<CourseWithProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeChartTab, setActiveChartTab] = useState<'users' | 'activity'>('users');
  const [mounted, setMounted] = useState(false);
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
      } catch (err) {
        console.error('Error fetching stats page data:', err);
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

  // Aggregated charts computations
  const getTopUsersData = () => {
    return [...profiles]
      .filter(p => p.total_area_m2 > 0)
      .sort((a, b) => b.total_area_m2 - a.total_area_m2)
      .slice(0, 5)
      .map(p => ({
        name: p.pseudonyme || 'Inconnu',
        area: parseFloat((p.total_area_m2 / 1000000).toFixed(4)),
        color: '#CCFF00'
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

  const topUsersData = getTopUsersData();
  const dailyActivityData = getDailyActivityData();

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh', gap: '16px' }}>
        <div className="avatar avatar-placeholder" style={{ width: '48px', height: '48px', border: '1px solid var(--primary-green)', animation: 'pulse 1.5s infinite' }} />
        <p style={{ color: 'var(--text-muted)' }}>Initialisation des statistiques...</p>
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

      {/* Corporate Header with Status Banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 className="title-cyber" style={{ fontSize: '2.2rem' }}>Statistiques & Cartographie Réseau</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Supervision et suivi cartographique du réseau Arpent.io</p>
        </div>

        {/* Status indicator */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '12px', 
          background: 'rgba(204, 255, 0, 0.03)', 
          border: '1px solid rgba(204, 255, 0, 0.1)', 
          padding: '8px 16px', 
          borderRadius: '6px' 
        }}>
          <span style={{ 
            display: 'inline-block', 
            width: '8px', 
            height: '8px', 
            borderRadius: '50%', 
            backgroundColor: 'var(--primary-green)'
          }} />
          <span style={{ color: 'var(--primary-green)', fontSize: '0.8rem', fontWeight: 800, fontFamily: 'monospace', textTransform: 'uppercase' }}>
            SYSTÈME OPÉRATIONNEL / EN LIGNE
          </span>
        </div>
      </div>

      {/* Stats Cards Row */}
      <div className="dashboard-grid">
        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '8px',
            backgroundColor: 'rgba(255, 255, 255, 0.04)',
            color: 'var(--text-white)'
          }}>
            <Users size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Utilisateurs Inscrits</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{profiles.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '8px',
            backgroundColor: 'rgba(204, 255, 0, 0.08)',
            color: 'var(--primary-green)'
          }}>
            <Layers size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Groupes Actifs</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{guilds.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '8px',
            backgroundColor: 'rgba(255, 255, 255, 0.04)',
            color: 'var(--text-white)'
          }}>
            <MapIcon size={24} />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600, textTransform: 'uppercase' }}>Zones Enregistrées</p>
            <p style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{territories.length}</p>
          </div>
        </div>

        <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div style={{
            padding: '12px',
            borderRadius: '8px',
            backgroundColor: 'rgba(204, 255, 0, 0.08)',
            color: 'var(--primary-green)'
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

      {/* Double Column Row (Charts & Live Activities) */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: '32px', minHeight: '450px', flexWrap: 'wrap' }}>
        
        {/* Recharts Analytics Panel */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '12px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Zap size={20} style={{ color: 'var(--primary-green)' }} /> Statistiques Analytiques
            </h3>
            
            <div style={{ display: 'flex', background: 'rgba(255, 255, 255, 0.02)', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '2px' }}>
              <button 
                onClick={() => setActiveChartTab('users')} 
                style={{
                  background: activeChartTab === 'users' ? 'rgba(204, 255, 0, 0.1)' : 'none',
                  border: 'none',
                  color: activeChartTab === 'users' ? 'var(--primary-green)' : 'var(--text-muted)',
                  padding: '6px 12px',
                  borderRadius: '4px',
                  fontFamily: 'var(--font-outfit)',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
              >
                Top Utilisateurs
              </button>
              <button 
                onClick={() => setActiveChartTab('activity')} 
                style={{
                  background: activeChartTab === 'activity' ? 'rgba(204, 255, 0, 0.1)' : 'none',
                  border: 'none',
                  color: activeChartTab === 'activity' ? 'var(--primary-green)' : 'var(--text-muted)',
                  padding: '6px 12px',
                  borderRadius: '4px',
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
                {activeChartTab === 'users' ? (
                  <BarChart data={topUsersData} margin={{ top: 20, right: 10, left: -10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.03)" />
                    <XAxis dataKey="name" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} unit=" km²" />
                    <Tooltip 
                      contentStyle={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                      labelStyle={{ color: '#FFFFFF', fontWeight: 'bold' }}
                    />
                    <Bar dataKey="area" radius={[4, 4, 0, 0]}>
                      {topUsersData.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                ) : (
                  <AreaChart data={dailyActivityData} margin={{ top: 20, right: 10, left: -10, bottom: 5 }}>
                    <defs>
                      <linearGradient id="colorDist" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--primary-green)" stopOpacity={0.2}/>
                        <stop offset="95%" stopColor="var(--primary-green)" stopOpacity={0.0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.03)" />
                    <XAxis dataKey="date" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} unit=" km" />
                    <Tooltip 
                      contentStyle={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                      labelStyle={{ color: '#FFFFFF', fontWeight: 'bold' }}
                    />
                    <Area type="monotone" dataKey="distance" stroke="var(--primary-green)" strokeWidth={2} fillOpacity={1} fill="url(#colorDist)" />
                  </AreaChart>
                )}
              </ResponsiveContainer>
            ) : (
              <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
                Chargement des graphiques...
              </div>
            )}
          </div>
        </div>

        {/* Live Network Activity Feed */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Activity size={20} style={{ color: 'var(--primary-green)' }} /> Flux d'Activité Réseau
          </h3>

          {courses.length === 0 ? (
            <div style={{ flexGrow: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px', padding: '24px' }}>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center' }}>
                Aucune session d'activité en cours sur le réseau Arpent.
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto', maxHeight: '330px', paddingRight: '4px' }}>
              {courses.map((item) => {
                const distanceKm = item.distance_totale / 1000;
                const min = Math.floor(item.duree_secondes / 60);
                const rawSec = item.duree_secondes % 60;
                const sec = Math.round(rawSec * 100) / 100;
                const avatar = item.profiles?.avatar_url;
                const color = 'var(--primary-green)';
                return (
                  <div 
                    key={item.id}
                    style={{
                      background: 'rgba(255, 255, 255, 0.01)',
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
                        {item.profiles?.pseudonyme?.substring(0, 2).toUpperCase() || 'US'}
                      </div>
                    )}
                    
                    <div style={{ flexGrow: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                        <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-white)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {item.profiles?.pseudonyme || 'Utilisateur'}
                        </span>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                          {new Date(item.date_debut).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', gap: '8px', marginTop: '2px' }}>
                        <span>🏃 {distanceKm.toFixed(2)} km</span>
                        <span>⏱️ {min}m {sec}s</span>
                        {item.est_bouclee && <span style={{ color: 'var(--primary-green)' }}>🔁 Terminé</span>}
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
