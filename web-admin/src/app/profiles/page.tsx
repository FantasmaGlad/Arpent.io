'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Search, 
  Trash2, 
  Edit2, 
  Activity, 
  Clock, 
  Navigation, 
  Calendar,
  X,
  User,
  ShieldAlert,
  Save,
  Image as ImageIcon,
  Award,
  MapPin,
  Users,
  ChevronRight,
  Flame,
  EyeOff,
  Sparkles,
  Zap,
  RotateCcw,
  Heart,
  TrendingUp,
  Sliders,
  Settings
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

interface Profile {
  id: string;
  pseudonyme: string | null;
  tag: string | null;
  guilde_id: string | null;
  total_area_m2: number;
  all_time_area_m2?: number;
  max_area_m2?: number;
  area_lost_m2?: number;
  xp?: number;
  level?: number;
  loop_count?: number;
  max_loop_distance_km?: number;
  ghost_mode?: boolean;
  avatar_url: string | null;
  empire_color: string;
  date_inscription: string;
  grade: string | null;
  share_location: boolean;
  latitude: number | null;
  longitude: number | null;
}

interface Guild {
  id: string;
  nom: string;
  couleur_hex: string;
  tag: string | null;
}

interface Course {
  id: string;
  date_debut: string;
  distance_totale: number;
  duree_secondes: number;
  est_bouclee: boolean;
  vitesse_moyenne: number;
  vitesse_max: number;
  allure_moyenne: number;
  calories_estimees: number;
  denivele_positif: number;
  denivele_negatif: number;
}

export default function ProfilesPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [mounted, setMounted] = useState(false);
  
  // Advanced filters and sorting
  const [clanFilter, setClanFilter] = useState('all');
  const [gradeFilter, setGradeFilter] = useState('all');
  const [sortBy, setSortBy] = useState('total_area_desc');

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const pageSize = 10;

  // Selected profile details modal
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loadingCourses, setLoadingCourses] = useState(false);

  // Tabs for user inspector modal
  const [activeTab, setActiveTab] = useState<'apercu' | 'conquete' | 'physique' | 'social' | 'admin'>('apercu');
  const [friends, setFriends] = useState<any[]>([]);
  const [loadingFriends, setLoadingFriends] = useState(false);
  const [streak, setStreak] = useState<number>(0);

  // Modal filters
  const [minDistance, setMinDistance] = useState<number>(0);
  const [loopOnlyFilter, setLoopOnlyFilter] = useState<'all' | 'loops' | 'noloops'>('all');

  // Edit fields
  const [newPseudonyme, setNewPseudonyme] = useState('');
  const [newEmpireColor, setNewEmpireColor] = useState('#CCFF00');
  const [newGhostMode, setNewGhostMode] = useState(false);
  
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  // Debounce search term to limit queries
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchTerm);
    }, 300);
    return () => clearTimeout(handler);
  }, [searchTerm]);

  // Reset to page 1 on filter or search changes
  useEffect(() => {
    setCurrentPage(1);
  }, [debouncedSearch, clanFilter, gradeFilter, sortBy]);

  // Initial load: mount state and guilds fetch
  useEffect(() => {
    setMounted(true);
    fetchGuilds();
  }, []);

  // Fetch paginated profiles whenever filters, search, or page changes
  useEffect(() => {
    if (mounted) {
      fetchProfiles();
    }
  }, [currentPage, debouncedSearch, clanFilter, gradeFilter, sortBy, mounted]);

  // Read URL query parameter on mount or when profiles list changes
  useEffect(() => {
    if (mounted && profiles.length > 0 && typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      const userIdParam = params.get('userId');
      if (userIdParam) {
        const found = profiles.find(p => p.id === userIdParam);
        if (found) {
          setSelectedProfile(found);
          // Clean the query parameter without reload
          const url = new URL(window.location.href);
          url.searchParams.delete('userId');
          window.history.replaceState({}, '', url.toString());
        }
      }
    }
  }, [profiles, mounted]);

  async function fetchGuilds() {
    try {
      const { data } = await supabase.from('guildes').select('id, nom, couleur_hex, tag');
      setGuilds((data || []) as Guild[]);
    } catch (err) {
      console.error('Error fetching guilds:', err);
    }
  }

  async function fetchProfiles() {
    setLoading(true);
    try {
      const from = (currentPage - 1) * pageSize;
      const to = from + pageSize - 1;

      let query = supabase.from('profiles').select('*', { count: 'exact' });

      if (debouncedSearch) {
        query = query.or(`pseudonyme.ilike.%${debouncedSearch}%,tag.ilike.%${debouncedSearch}%`);
      }
      if (clanFilter !== 'all') {
        query = query.eq('guilde_id', clanFilter);
      }
      if (gradeFilter === 'autonome') {
        query = query.is('guilde_id', null);
      } else if (gradeFilter === 'chef') {
        query = query.eq('grade', 'chef');
      } else if (gradeFilter === 'adjoint') {
        query = query.eq('grade', 'adjoint');
      } else if (gradeFilter === 'membre') {
        query = query.not('guilde_id', 'is', null)
                     .or('grade.is.null,grade.neq.chef')
                     .or('grade.is.null,grade.neq.adjoint');
      }

      if (sortBy === 'pseudonyme') {
        query = query.order('pseudonyme', { ascending: true });
      } else if (sortBy === 'total_area_desc') {
        query = query.order('total_area_m2', { ascending: false });
      } else if (sortBy === 'total_area_asc') {
        query = query.order('total_area_m2', { ascending: true });
      } else if (sortBy === 'date_inscription_desc') {
        query = query.order('date_inscription', { ascending: false });
      } else if (sortBy === 'date_inscription_asc') {
        query = query.order('date_inscription', { ascending: true });
      }

      const { data, count, error } = await query.range(from, to);
      if (error) throw error;

      setProfiles((data || []) as Profile[]);
      setTotalCount(count || 0);
    } catch (err) {
      console.error('Error fetching profiles:', err);
    } finally {
      setLoading(false);
    }
  }

  // Fetch details (courses, friends & streak) when a profile is selected
  useEffect(() => {
    if (!selectedProfile) return;

    async function fetchDetails() {
      setLoadingCourses(true);
      setLoadingFriends(true);
      setFriends([]);
      setStreak(0);
      try {
        // 1. Fetch streak
        const { data: streakData } = await supabase.rpc('get_user_streak', { p_user_id: selectedProfile!.id });
        if (streakData !== null && streakData !== undefined) {
          setStreak(streakData);
        }

        // 2. Fetch courses
        const { data: coursesData, error: coursesError } = await supabase
          .from('courses')
          .select('*')
          .eq('utilisateur_id', selectedProfile!.id)
          .order('date_debut', { ascending: false });

        if (coursesError) throw coursesError;
        setCourses((coursesData || []) as Course[]);

        // 3. Fetch friends from public.amis
        const { data: amisData, error: amisError } = await supabase
          .from('amis')
          .select('demandeur_id, destinataire_id')
          .or(`demandeur_id.eq.${selectedProfile!.id},destinataire_id.eq.${selectedProfile!.id}`)
          .eq('statut', 'accepte');

        if (amisError) throw amisError;

        const friendIds = (amisData || []).map(a => 
          a.demandeur_id === selectedProfile!.id ? a.destinataire_id : a.demandeur_id
        );

        if (friendIds.length > 0) {
          const { data: friendProfiles, error: profilesError } = await supabase
            .from('profiles')
            .select('id, pseudonyme, tag, avatar_url, empire_color, total_area_m2')
            .in('id', friendIds);

          if (profilesError) throw profilesError;
          setFriends(friendProfiles || []);
        }
      } catch (err) {
        console.error('Error fetching details:', err);
      } finally {
        setLoadingCourses(false);
        setLoadingFriends(false);
      }
    }

    fetchDetails();
    setNewPseudonyme(selectedProfile.pseudonyme || '');
    setNewEmpireColor(selectedProfile.empire_color || '#CCFF00');
    setNewGhostMode(selectedProfile.ghost_mode || false);
    setActiveTab('apercu');
    setMinDistance(0);
    setLoopOnlyFilter('all');
    setMessage(null);
  }, [selectedProfile]);

  const handleUpdateProfile = async (updatesObj?: { pseudonyme?: string; empireColor?: string; ghostMode?: boolean }) => {
    if (!selectedProfile) return;
    setActionLoading(true);
    setMessage(null);

    const payload = updatesObj || {
      userId: selectedProfile.id,
      pseudonyme: newPseudonyme,
      empireColor: newEmpireColor,
      ghostMode: newGhostMode
    };

    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch('/api/admin/profiles', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          userId: selectedProfile.id,
          ...payload
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la mise à jour.');
      }

      setMessage({ type: 'success', text: 'Profil mis à jour avec succès.' });
      
      const updatedProfile = { 
        ...selectedProfile, 
        pseudonyme: payload.pseudonyme !== undefined ? payload.pseudonyme : selectedProfile.pseudonyme,
        empire_color: payload.empireColor !== undefined ? payload.empireColor : selectedProfile.empire_color,
        ghost_mode: payload.ghostMode !== undefined ? payload.ghostMode : selectedProfile.ghost_mode
      };
      setSelectedProfile(updatedProfile);
      setProfiles(prev => prev.map(p => p.id === selectedProfile.id ? updatedProfile : p));
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveAvatar = async () => {
    if (!selectedProfile || !confirm("Voulez-vous supprimer la photo de profil de cet utilisateur ?")) return;
    setActionLoading(true);
    setMessage(null);

    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch('/api/admin/profiles', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          userId: selectedProfile.id,
          avatarUrl: null
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || "Erreur lors de la suppression de l'avatar.");
      }

      setMessage({ type: 'success', text: "L'avatar a été réinitialisé." });

      const updatedProfile = { ...selectedProfile, avatar_url: null };
      setSelectedProfile(updatedProfile);
      setProfiles(prev => prev.map(p => p.id === selectedProfile.id ? updatedProfile : p));
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (userId: string, onlyData: boolean = false) => {
    const isGuest = profiles.find(p => p.id === userId)?.pseudonyme?.startsWith('Invité_');
    const confirmMessage = onlyData 
      ? "ATTENTION : Cette action supprimera TOUTES les données de jeu (courses, territoires, statistiques, relations) de cet utilisateur.\nSon compte de connexion sera conservé.\n\nCette action est irréversible. Continuer ?"
      : isGuest
        ? "ATTENTION : Cette action supprimera définitivement ce compte invité ainsi que toutes ses données de jeu.\n\nCette action est irréversible. Continuer ?"
        : "ATTENTION : Cette action supprimera définitivement le compte utilisateur, ses relations, ses zones couvertes et ses sessions d'activité.\n\nCette action est irréversible. Continuer ?";

    if (!confirm(confirmMessage)) {
      return;
    }

    setActionLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch(`/api/admin/profiles?userId=${userId}${onlyData ? '&onlyData=true' : ''}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la suppression.');
      }

      alert(onlyData ? 'Données de jeu de l\'utilisateur réinitialisées avec succès.' : 'Utilisateur supprimé avec succès.');
      
      if (onlyData) {
        setProfiles(prev => prev.map(p => p.id === userId ? {
          ...p,
          total_area_m2: 0,
          all_time_area_m2: 0,
          max_area_m2: 0,
          area_lost_m2: 0,
          xp: 0,
          level: 1,
          loop_count: 0,
          max_loop_distance_km: 0,
          total_steps: 0,
          average_cadence: 0,
          guilde_id: null,
          grade: 'membre',
          avatar_url: null,
          latitude: null,
          longitude: null
        } : p));
        if (selectedProfile?.id === userId) {
          setSelectedProfile(prev => prev ? {
            ...prev,
            total_area_m2: 0,
            all_time_area_m2: 0,
            max_area_m2: 0,
            area_lost_m2: 0,
            xp: 0,
            level: 1,
            loop_count: 0,
            max_loop_distance_km: 0,
            total_steps: 0,
            average_cadence: 0,
            guilde_id: null,
            grade: 'membre',
            avatar_url: null,
            latitude: null,
            longitude: null
          } : null);
        }
      } else {
        setSelectedProfile(null);
        setProfiles(prev => prev.filter(p => p.id !== userId));
        setTotalCount(prev => Math.max(0, prev - 1));
      }
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCourse = async (courseId: string) => {
    if (!confirm("Voulez-vous vraiment supprimer cette activité ? Cette action est irréversible et recalculera les statistiques du joueur.")) {
      return;
    }

    setActionLoading(true);
    setMessage(null);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch(`/api/admin/courses?courseId=${courseId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || "Erreur lors de la suppression.");
      }

      setMessage({ type: 'success', text: "L'activité a été supprimée avec succès." });
      setCourses(prev => prev.filter(c => c.id !== courseId));
    } catch (err: any) {
      setMessage({ type: 'error', text: `Erreur : ${err.message}` });
    } finally {
      setActionLoading(false);
    }
  };

  const centerPlayerOnMap = (lat: number, lng: number) => {
    localStorage.setItem('map_center_lat', lat.toString());
    localStorage.setItem('map_center_lng', lng.toString());
    window.location.href = '/';
  };

  // Filter courses in modal
  const filteredCourses = courses.filter(c => {
    const distKm = c.distance_totale / 1000;
    const matchesDistance = distKm >= minDistance;
    const matchesLoop = loopOnlyFilter === 'all' || 
      (loopOnlyFilter === 'loops' && c.est_bouclee) ||
      (loopOnlyFilter === 'noloops' && !c.est_bouclee);
    return matchesDistance && matchesLoop;
  });

  const chartData = [...filteredCourses].reverse().map(c => ({
    date: new Date(c.date_debut).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
    distance: parseFloat((c.distance_totale / 1000).toFixed(2)),
    duration: parseFloat((c.duree_secondes / 60).toFixed(1))
  }));

  // Cumulative course stats
  const totalRuns = courses.length;
  const totalDistanceKm = courses.reduce((acc, c) => acc + c.distance_totale, 0) / 1000;
  const totalDurationSec = courses.reduce((acc, c) => acc + c.duree_secondes, 0);
  const avgSpeed = courses.length > 0 ? courses.reduce((acc, c) => acc + (c.vitesse_moyenne || 0), 0) / courses.length : 0;
  const totalCalories = courses.reduce((acc, c) => acc + (c.calories_estimees || 0), 0);
  const totalElevationPos = courses.reduce((acc, c) => acc + (c.denivele_positif || 0), 0);
  const totalElevationNeg = courses.reduce((acc, c) => acc + (c.denivele_negatif || 0), 0);

  // Rolling activity volumes: 7 days and 30 days
  const now = new Date();
  const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

  const last7DaysCourses = courses.filter(c => new Date(c.date_debut) >= sevenDaysAgo);
  const last30DaysCourses = courses.filter(c => new Date(c.date_debut) >= thirtyDaysAgo);

  const rollingDist7 = last7DaysCourses.reduce((acc, c) => acc + c.distance_totale, 0) / 1000;
  const rollingElev7 = last7DaysCourses.reduce((acc, c) => acc + (c.denivele_positif || 0), 0);

  const rollingDist30 = last30DaysCourses.reduce((acc, c) => acc + c.distance_totale, 0) / 1000;
  const rollingElev30 = last30DaysCourses.reduce((acc, c) => acc + (c.denivele_positif || 0), 0);

  const formatDurationText = (sec: number) => {
    const hrs = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const remainingSecs = Math.round(sec % 60);
    if (hrs > 0) return `${hrs}h ${mins}m`;
    return `${mins}m ${remainingSecs}s`;
  };

  // XP progression calculation helper
  const getXpProgress = (xpValue: number, levelValue: number) => {
    const minCurrent = Math.pow(levelValue - 1, 2) * 250;
    const minNext = Math.pow(levelValue, 2) * 250;
    const progress = xpValue - minCurrent;
    const target = minNext - minCurrent;
    const percent = Math.min(100, Math.max(0, (progress / target) * 100));
    return {
      percent,
      progress: Math.floor(progress),
      target: Math.floor(target)
    };
  };

  const xpStats = getXpProgress(selectedProfile?.xp || 0, selectedProfile?.level || 1);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header */}
      <div>
        <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Profils Utilisateurs</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Supervision des profils membres, suivi des activités physiques et modération</p>
      </div>

      {/* Command Search and Filters */}
      <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', borderBottom: '1px solid var(--border-color)', paddingBottom: '14px' }}>
          <Search size={20} style={{ color: 'var(--text-muted)' }} />
          <input 
            type="text" 
            placeholder="Rechercher un utilisateur par pseudo ou identifiant..." 
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '1rem', flexGrow: 1 }}
          />
        </div>

        {/* Dropdown filters row */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Groupe / Équipe</span>
            <select 
              value={clanFilter}
              onChange={(e) => setClanFilter(e.target.value)}
              className="input-field"
              style={{ padding: '8px 12px', fontSize: '0.85rem' }}
            >
              <option value="all">Tous les groupes</option>
              {guilds.map(g => (
                <option key={g.id} value={g.id}>{g.nom}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Rôle de Groupe</span>
            <select 
              value={gradeFilter}
              onChange={(e) => setGradeFilter(e.target.value)}
              className="input-field"
              style={{ padding: '8px 12px', fontSize: '0.85rem' }}
            >
              <option value="all">Tous les rôles</option>
              <option value="chef">👑 Responsables</option>
              <option value="adjoint">👥 Responsables Adjoints</option>
              <option value="membre">Membres ordinaires</option>
              <option value="autonome">Utilisateurs Indépendants</option>
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Trier</span>
            <select 
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="input-field"
              style={{ padding: '8px 12px', fontSize: '0.85rem' }}
            >
              <option value="total_area_desc">Superficie couverte (Max)</option>
              <option value="total_area_asc">Superficie couverte (Min)</option>
              <option value="date_inscription_desc">Dernières inscriptions</option>
              <option value="date_inscription_asc">Premières inscriptions</option>
              <option value="pseudonyme">Pseudonyme (A-Z)</option>
            </select>
          </div>
        </div>
      </div>

      {/* Profiles Table */}
      <div className="glass-card" style={{ padding: '0', overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Chargement de la base des utilisateurs...
          </div>
        ) : profiles.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Aucun utilisateur ne correspond à ces critères.
          </div>
        ) : (
          <>
            <div className="table-container">
              <table className="cyber-table">
                <thead>
                  <tr>
                    <th>Utilisateur</th>
                    <th>Tag</th>
                    <th>Groupe / Équipe</th>
                    <th>Rôle</th>
                    <th>Surface Couverte</th>
                    <th>Inscription</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {profiles.map((p) => {
                    const guild = guilds.find(g => g.id === p.guilde_id);
                    const color = p.empire_color || '#CCFF00';
                    return (
                      <tr key={p.id}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <div style={{ position: 'relative' }}>
                              {p.avatar_url ? (
                                <img src={p.avatar_url} alt="" className="avatar" style={{ borderColor: color, borderWidth: '2px' }} />
                              ) : (
                                <div className="avatar avatar-placeholder" style={{ borderColor: color, color: color, borderWidth: '2px' }}>
                                  {p.pseudonyme ? p.pseudonyme.substring(0, 2).toUpperCase() : 'US'}
                                </div>
                              )}
                              {p.ghost_mode && (
                                <span style={{ position: 'absolute', bottom: '-2px', right: '-2px', background: '#000000', borderRadius: '50%', padding: '2px', fontSize: '0.7rem' }} title="Mode Fantôme activé">👻</span>
                              )}
                            </div>
                            <div>
                              <span style={{ fontWeight: 700, color: 'var(--text-white)' }}>
                                {p.pseudonyme || 'Utilisateur Anonyme'}
                              </span>
                              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                                ID: {p.id.substring(0, 8)}...
                              </p>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: '#CCFF00', fontWeight: 600 }}>
                            {p.tag || '—'}
                          </span>
                        </td>
                        <td>
                          {guild ? (
                            <span className="badge animate-hover" style={{ backgroundColor: 'rgba(255, 255, 255, 0.02)', color: 'var(--text-white)', border: '1px solid var(--border-color)' }}>
                              {guild.nom} <span style={{ fontFamily: 'monospace', fontSize: '0.7rem', opacity: 0.8, marginLeft: '4px' }}>{guild.tag}</span>
                            </span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Indépendant</span>
                          )}
                        </td>
                        <td>
                          {p.grade === 'chef' ? (
                            <span className="badge" style={{ backgroundColor: 'rgba(204, 255, 0, 0.05)', color: '#CCFF00', border: '1px solid #CCFF00' }}>Responsable</span>
                          ) : p.grade === 'adjoint' ? (
                            <span className="badge" style={{ backgroundColor: 'rgba(255, 255, 255, 0.05)', color: 'var(--text-white)', border: '1px solid var(--border-color)' }}>Adjoint</span>
                          ) : p.guilde_id ? (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Membre</span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>—</span>
                          )}
                        </td>
                        <td style={{ fontWeight: 700, color: 'var(--text-white)' }}>
                          {(p.total_area_m2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                        </td>
                        <td>
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                            {new Date(p.date_inscription).toLocaleDateString('fr-FR')}
                          </span>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', gap: '8px' }}>
                            <button 
                              className="btn-icon" 
                              title="Ouvrir la fiche utilisateur"
                              onClick={() => setSelectedProfile(p)}
                              style={{ color: '#CCFF00', borderColor: 'rgba(204, 255, 0, 0.2)' }}
                            >
                              <Activity size={16} />
                            </button>
                            <button 
                              className="btn-icon" 
                              title="Supprimer l'utilisateur"
                              onClick={() => handleDeleteUser(p.id)}
                              style={{ color: '#FF4B4B', borderColor: 'rgba(255, 75, 75, 0.2)' }}
                            >
                              <Trash2 size={16} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center', 
              padding: '16px 24px', 
              borderTop: '1px solid var(--border-color)',
              background: 'rgba(0, 0, 0, 0.2)',
              flexWrap: 'wrap',
              gap: '12px'
            }}>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Affichage de {totalCount > 0 ? (currentPage - 1) * pageSize + 1 : 0} à {Math.min(currentPage * pageSize, totalCount)} sur {totalCount} utilisateurs
              </span>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button 
                  className="btn btn-secondary"
                  onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                  disabled={currentPage === 1}
                  style={{ padding: '6px 12px', fontSize: '0.8rem' }}
                >
                  Précédent
                </button>
                <button 
                  className="btn btn-secondary"
                  onClick={() => setCurrentPage(prev => Math.min(Math.ceil(totalCount / pageSize), prev + 1))}
                  disabled={currentPage >= Math.ceil(totalCount / pageSize)}
                  style={{ padding: '6px 12px', fontSize: '0.8rem' }}
                >
                  Suivant
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Strava-Style Inspector Modal with Custom Tabs */}
      {selectedProfile && (
        <div className="modal-overlay">
          <div className="modal-content glass-card" style={{ 
            border: '1px solid var(--border-color)',
            maxWidth: '800px',
            padding: '0',
            overflow: 'hidden',
            backgroundColor: '#07090C'
          }}>
            {/* Strava Premium Banner Header */}
            <div style={{
              background: 'linear-gradient(180deg, #10151E 0%, #0A0D14 100%)',
              padding: '28px',
              borderBottom: '1px solid var(--border-color)',
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
              position: 'relative'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                  <div style={{ position: 'relative' }}>
                    {selectedProfile.avatar_url ? (
                      <img src={selectedProfile.avatar_url} alt="" className="avatar avatar-large" style={{ borderColor: selectedProfile.empire_color || '#CCFF00', borderWidth: '3px', boxShadow: '0 4px 14px rgba(0,0,0,0.6)' }} />
                    ) : (
                      <div className="avatar avatar-large avatar-placeholder" style={{ borderColor: selectedProfile.empire_color || '#CCFF00', color: selectedProfile.empire_color || '#CCFF00', borderWidth: '3px', boxShadow: '0 4px 14px rgba(0,0,0,0.6)', fontSize: '1.8rem' }}>
                        {selectedProfile.pseudonyme ? selectedProfile.pseudonyme.substring(0, 2).toUpperCase() : 'US'}
                      </div>
                    )}
                    {selectedProfile.ghost_mode && (
                      <span style={{ position: 'absolute', bottom: '0px', right: '0px', background: '#000000', borderRadius: '50%', padding: '4px', fontSize: '1.1rem', border: '2px solid #CCFF00' }} title="Invisible (Mode Fantôme)">👻</span>
                    )}
                  </div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <h2 style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-white)' }}>
                        {selectedProfile.pseudonyme || 'Athlète'}
                      </h2>
                      <span className="badge" style={{ backgroundColor: 'rgba(255, 255, 255, 0.05)', color: 'var(--text-muted)', border: '1px solid var(--border-color)', fontSize: '0.7rem' }}>
                        Niveau {selectedProfile.level || 1}
                      </span>
                      {selectedProfile.grade === 'chef' && (
                        <span className="badge" style={{ backgroundColor: 'rgba(204, 255, 0, 0.05)', color: '#CCFF00', border: '1px solid #CCFF00', fontSize: '0.7rem' }}>👑 Responsable</span>
                      )}
                    </div>
                    <p style={{ color: '#CCFF00', fontSize: '0.85rem', fontFamily: 'monospace', fontWeight: 700, marginTop: '4px', letterSpacing: '0.5px' }}>
                      TAG: {selectedProfile.tag || 'NON DÉFINI'}
                    </p>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Calendar size={12} /> Membre depuis le {new Date(selectedProfile.date_inscription).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })}
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
                    width: '36px',
                    height: '36px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    fontSize: '1.3rem',
                    transition: 'all 0.2s',
                    position: 'absolute',
                    top: '20px',
                    right: '20px'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.color = '#FFFFFF'}
                  onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-muted)'}
                >
                  <X size={18} />
                </button>
              </div>

              {/* XP Level progress bar */}
              <div style={{ marginTop: '8px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '12px 16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '6px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Progression XP</span>
                  <span style={{ color: '#CCFF00', fontWeight: 'bold' }}>{selectedProfile.xp || 0} XP <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}>({xpStats.progress} / {xpStats.target} XP pour Niv { (selectedProfile.level || 1) + 1 })</span></span>
                </div>
                <div style={{ width: '100%', height: '6px', backgroundColor: 'rgba(255, 255, 255, 0.05)', borderRadius: '3px', overflow: 'hidden' }}>
                  <div style={{ width: `${xpStats.percent}%`, height: '100%', backgroundColor: '#CCFF00', borderRadius: '3px', transition: 'width 0.4s ease' }}></div>
                </div>
              </div>
            </div>

            {/* Notification messages inside modal */}
            {message && (
              <div style={{
                margin: '16px 24px 0 24px',
                padding: '10px 16px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                backgroundColor: message.type === 'success' ? 'rgba(204, 255, 0, 0.05)' : 'rgba(255, 75, 75, 0.05)',
                border: message.type === 'success' ? '1px solid #CCFF00' : '1px solid #FF4B4B',
                color: message.type === 'success' ? '#CCFF00' : '#FF4B4B'
              }}>
                {message.text}
              </div>
            )}

            {/* Modal Tabs Panel (Sleek Strava-Style Orange/Volt Accent) */}
            <div style={{ 
              display: 'flex', 
              background: 'rgba(0, 0, 0, 0.4)', 
              borderBottom: '1px solid var(--border-color)',
              padding: '0 24px',
              overflowX: 'auto'
            }}>
              {[
                { id: 'apercu', label: 'Aperçu', icon: User },
                { id: 'conquete', label: 'Conquête', icon: Heart },
                { id: 'physique', label: 'Physique', icon: Activity },
                { id: 'social', label: 'Social', icon: Users },
                { id: 'admin', label: 'Paramètres Admin', icon: Settings },
              ].map(t => {
                const ActiveIcon = t.icon;
                return (
                  <button 
                    key={t.id}
                    onClick={() => setActiveTab(t.id as any)}
                    style={{
                      background: 'none',
                      border: 'none',
                      borderBottom: activeTab === t.id ? '2px solid #CCFF00' : '2px solid transparent',
                      color: activeTab === t.id ? '#FFFFFF' : 'var(--text-muted)',
                      padding: '14px 16px',
                      fontSize: '0.9rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      fontFamily: 'var(--font-outfit)',
                      transition: 'all 0.2s',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      whiteSpace: 'nowrap'
                    }}
                  >
                    <ActiveIcon size={14} style={{ color: activeTab === t.id ? '#CCFF00' : 'var(--text-muted)' }} />
                    {t.label}
                  </button>
                );
              })}
            </div>

            {/* Modal Body Container */}
            <div style={{ padding: '24px', maxHeight: '500px', overflowY: 'auto' }}>
              
              {/* TAB 1: OVERVIEW / APERCU */}
              {activeTab === 'apercu' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Two column grid layout */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', alignItems: 'stretch' }}>
                    
                    {/* Activity Streak Widget */}
                    <div style={{ 
                      background: 'rgba(255,255,255,0.01)', 
                      padding: '20px', 
                      borderRadius: '8px', 
                      border: '1px solid var(--border-color)',
                      display: 'flex', 
                      flexDirection: 'column', 
                      justifyContent: 'center', 
                      alignItems: 'center', 
                      gap: '12px',
                      textAlign: 'center'
                    }}>
                      <div style={{ 
                        background: 'rgba(204,255,0,0.08)', 
                        border: '1px solid rgba(204,255,0,0.2)', 
                        padding: '12px', 
                        borderRadius: '50%',
                        color: '#CCFF00',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        <Flame size={28} style={{ fill: streak > 0 ? '#CCFF00' : 'none' }} />
                      </div>
                      <div>
                        <h4 style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                          Série d'Activité Actuelle
                        </h4>
                        <p style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '4px' }}>
                          {streak} {streak > 1 ? 'jours' : 'jour'}
                        </p>
                        <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                          {streak > 0 ? 'Continuez votre lancée quotidienne !' : 'Aucune course hier ni aujourd\'hui.'}
                        </p>
                      </div>
                    </div>

                    {/* Empire custom color & status info */}
                    <div style={{ 
                      background: 'rgba(255,255,255,0.01)', 
                      padding: '20px', 
                      borderRadius: '8px', 
                      border: '1px solid var(--border-color)',
                      display: 'flex', 
                      flexDirection: 'column', 
                      justifyContent: 'space-between',
                      gap: '16px'
                    }}>
                      <div>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Empire / Couleur</span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '6px' }}>
                          <span style={{ display: 'inline-block', width: '18px', height: '18px', borderRadius: '4px', backgroundColor: selectedProfile.empire_color || '#CCFF00', border: '1px solid rgba(255,255,255,0.2)' }} />
                          <span style={{ fontSize: '0.9rem', fontFamily: 'monospace', color: 'var(--text-white)', fontWeight: 'bold' }}>{selectedProfile.empire_color || '#CCFF00'}</span>
                        </div>
                      </div>

                      <div>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Partage de Position GPS</span>
                        <p style={{ fontSize: '0.9rem', fontWeight: 'bold', color: selectedProfile.share_location ? '#CCFF00' : '#FF4B4B', marginTop: '4px' }}>
                          {selectedProfile.share_location ? 'ACTIVÉ (Position partagée)' : 'DÉSACTIVÉ (Position cachée)'}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* GPS Coordinates & Map Centering Block */}
                  <div style={{ 
                    background: 'rgba(255, 255, 255, 0.01)', 
                    padding: '20px', 
                    borderRadius: '8px', 
                    border: '1px solid var(--border-color)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px'
                  }}>
                    <h4 style={{ fontSize: '0.9rem', color: 'var(--text-white)', fontWeight: 700, borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
                      Géolocalisation & Centrage
                    </h4>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Dernière position connue</span>
                        <p style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px' }}>
                          {selectedProfile.latitude && selectedProfile.longitude ? (
                            `LAT: ${selectedProfile.latitude.toFixed(6)} | LNG: ${selectedProfile.longitude.toFixed(6)}`
                          ) : (
                            'Aucune coordonnée GPS enregistrée.'
                          )}
                        </p>
                      </div>
                      
                      {selectedProfile.share_location && selectedProfile.latitude && selectedProfile.longitude && (
                        <button 
                          className="btn btn-secondary" 
                          onClick={() => centerPlayerOnMap(selectedProfile.latitude!, selectedProfile.longitude!)}
                          style={{ padding: '8px 14px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '6px' }}
                        >
                          <MapPin size={12} style={{ color: '#CCFF00' }} /> Centrer sur la carte
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 2: CONQUEST / CONQUETE */}
              {activeTab === 'conquete' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Performance stats cards grid */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    
                    <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Contrôlée Actuelle</span>
                      <p style={{ fontSize: '1.4rem', fontWeight: 800, color: '#CCFF00', marginTop: '6px' }}>
                        {(selectedProfile.total_area_m2).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} m²
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        {((selectedProfile.total_area_m2 || 0) / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>

                    <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Maximale Historique</span>
                      <p style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '6px' }}>
                        {(selectedProfile.max_area_m2 || selectedProfile.total_area_m2 || 0).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} m²
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        {(((selectedProfile.max_area_m2 || selectedProfile.total_area_m2 || 0)) / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>

                    <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Cumulée Conquise (All-Time)</span>
                      <p style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '6px' }}>
                        {(selectedProfile.all_time_area_m2 || selectedProfile.total_area_m2 || 0).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} m²
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        {(((selectedProfile.all_time_area_m2 || selectedProfile.total_area_m2 || 0)) / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>

                    <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Perdue au profit des rivaux</span>
                      <p style={{ fontSize: '1.4rem', fontWeight: 800, color: '#FF4B4B', marginTop: '6px' }}>
                        {(selectedProfile.area_lost_m2 || 0).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} m²
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        {((selectedProfile.area_lost_m2 || 0) / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>
                  </div>

                  {/* Conquest Yield analysis */}
                  <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '20px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                      <TrendingUp size={18} style={{ color: '#CCFF00' }} />
                      <h4 style={{ fontSize: '0.9rem', color: 'var(--text-white)', fontWeight: 700 }}>
                        Rendement de Conquête
                      </h4>
                    </div>
                    <p style={{ fontSize: '1.4rem', fontWeight: 800, color: '#CCFF00', margin: '4px 0' }}>
                      {((selectedProfile.all_time_area_m2 || selectedProfile.total_area_m2 || 0) / (totalDistanceKm || 1)).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} m² / km
                    </p>
                    <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Exprime le rapport de surface historiquement conquise sur la distance parcourue. Un rendement élevé indique que l'athlète boucle efficacement des parcours denses ou élargit intelligemment son territoire.
                    </p>
                  </div>
                </div>
              )}

              {/* TAB 3: PHYSICAL / ATHLETIC (ENTRAINEMENT) */}
              {activeTab === 'physique' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Cumulative physical statistics */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>Statistiques d'Activité Cumulées</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Courses Totales</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{totalRuns}</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Distance</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{totalDistanceKm.toFixed(2)} km</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Temps Cumulé</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: '#CCFF00', marginTop: '2px' }}>{formatDurationText(totalDurationSec)}</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Vitesse Moyenne</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{avgSpeed.toFixed(1)} km/h</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Dénivelé Cumulé</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: '#CCFF00', marginTop: '2px' }}>+{Math.round(totalElevationPos)}m</p>
                      </div>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', textAlign: 'center' }}>
                        <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Calories</span>
                        <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>{Math.round(totalCalories)} kcal</p>
                      </div>
                    </div>
                  </div>

                  {/* Loop Conquest Statistics (Strava style) */}
                  <div style={{ background: 'rgba(255, 255, 255, 0.01)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '16px' }}>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>Métriques de Boucle</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Boucles complétées</span>
                        <p style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '4px' }}>
                          {selectedProfile.loop_count || 0}
                        </p>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Distance de la plus grande boucle</span>
                        <p style={{ fontSize: '1.3rem', fontWeight: 800, color: '#CCFF00', marginTop: '4px' }}>
                          {(selectedProfile.max_loop_distance_km || 0).toFixed(2)} km
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Rolling Activity volumes cards */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>Volumes Glissants</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Sur les 7 derniers jours</span>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '8px' }}>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Distance</span>
                            <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)' }}>{rollingDist7.toFixed(1)} km</p>
                          </div>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Dénivelé</span>
                            <p style={{ fontSize: '1.2rem', fontWeight: 800, color: '#CCFF00' }}>+{Math.round(rollingElev7)}m</p>
                          </div>
                        </div>
                      </div>

                      <div style={{ background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', textTransform: 'uppercase', fontWeight: 600 }}>Sur les 30 derniers jours</span>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '8px' }}>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Distance</span>
                            <p style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-white)' }}>{rollingDist30.toFixed(1)} km</p>
                          </div>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Dénivelé</span>
                            <p style={{ fontSize: '1.2rem', fontWeight: 800, color: '#CCFF00' }}>+{Math.round(rollingElev30)}m</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Graphic Performance */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>Graphique d'Évolution (Distance & Temps)</h4>
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
                              <Line yAxisId="left" type="monotone" dataKey="distance" name="Distance" stroke="#CCFF00" strokeWidth={2} dot={{ fill: '#CCFF00', strokeWidth: 1, r: 3 }} activeDot={{ r: 5 }} />
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
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>Historique des activités ({filteredCourses.length})</h4>
                    
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
                                      <span style={{ color: '#CCFF00' }}>Bouclée (Terminée)</span>
                                    ) : (
                                      <span style={{ color: 'var(--text-muted)' }}>Non bouclée</span>
                                    )}
                                  </p>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                  <div style={{ textAlign: 'right' }}>
                                    <p style={{ fontWeight: 800, color: 'var(--text-white)', fontSize: '1.05rem' }}>{distKm.toFixed(2)} km</p>
                                    <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                      {durationMin}m {durationSec.toFixed(2)}s
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
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: '#CCFF00' }}>{(c.vitesse_moyenne || 0).toFixed(1)} km/h</p>
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
                                  <p style={{ fontWeight: 700, fontSize: '0.85rem', color: '#CCFF00' }}>+{Math.round(c.denivele_positif || 0)}/-{Math.round(c.denivele_negatif || 0)}m</p>
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

              {/* TAB 4: SOCIAL NETWORK (GUILDS & AMIS) */}
              {activeTab === 'social' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Guild Info Block if available */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px' }}>
                      Affiliation Clan/Guilde
                    </h4>
                    {selectedProfile.guilde_id ? (
                      (() => {
                        const guild = guilds.find(g => g.id === selectedProfile.guilde_id);
                        return (
                          <div style={{ 
                            background: 'rgba(255,255,255,0.01)', 
                            border: '1px solid var(--border-color)', 
                            borderRadius: '8px', 
                            padding: '16px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                          }}>
                            <div>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <span style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '50%', backgroundColor: guild?.couleur_hex || '#CCFF00' }} />
                                <span style={{ fontWeight: 'bold', fontSize: '1.05rem', color: 'var(--text-white)' }}>{guild?.nom}</span>
                                <span style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>{guild?.tag}</span>
                              </div>
                              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                                Rôle : {selectedProfile.grade === 'chef' ? '👑 Chef de Clan / Responsable' : selectedProfile.grade === 'adjoint' ? '👥 Responsable Adjoint' : 'Membre actif'}
                              </p>
                            </div>
                          </div>
                        );
                      })()
                    ) : (
                      <div style={{ padding: '16px', textAlign: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Cet utilisateur est un éclaireur indépendant (aucun clan).</p>
                      </div>
                    )}
                  </div>

                  {/* Friends section */}
                  <div>
                    <h4 style={{ fontSize: '0.85rem', color: '#CCFF00', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 700, letterSpacing: '0.5px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Users size={16} /> Relations Réseau ({friends.length})
                    </h4>

                    {loadingFriends ? (
                      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chargement des relations...</p>
                    ) : friends.length === 0 ? (
                      <div style={{ padding: '32px', textAlign: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Cet utilisateur n'a pas encore établi de relations d'amitié sur le réseau.</p>
                      </div>
                    ) : (
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        {friends.map(f => (
                          <div 
                            key={f.id}
                            style={{
                              background: 'rgba(255, 255, 255, 0.01)',
                              border: '1px solid var(--border-color)',
                              borderRadius: '8px',
                              padding: '12px',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '12px'
                            }}
                          >
                            {f.avatar_url ? (
                              <img src={f.avatar_url} alt="" className="avatar" style={{ width: '36px', height: '36px', borderColor: f.empire_color || '#CCFF00', borderWidth: '2px' }} />
                            ) : (
                              <div className="avatar avatar-placeholder" style={{ width: '36px', height: '36px', borderColor: f.empire_color || '#CCFF00', color: f.empire_color || '#CCFF00', fontSize: '0.8rem', borderWidth: '2px' }}>
                                {f.pseudonyme?.substring(0, 2).toUpperCase() || 'US'}
                              </div>
                            )}
                            <div style={{ minWidth: 0, flexGrow: 1 }}>
                              <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-white)', overflow: 'hidden', textOverflow: 'ellipsis', display: 'block', whiteSpace: 'nowrap' }}>
                                {f.pseudonyme || 'Utilisateur'}
                              </span>
                              <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                                {f.tag || ''}
                              </span>
                            </div>
                            <div style={{ textAlign: 'right' }}>
                              <p style={{ fontSize: '0.75rem', fontWeight: 'bold', color: '#CCFF00' }}>
                                {(f.total_area_m2 / 1000000).toFixed(3)} km²
                              </p>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* TAB 5: ADMIN CONTROLS / PARAMETRES */}
              {activeTab === 'admin' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Account Moderation Edit Fields */}
                  <div style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <h4 style={{ fontSize: '0.9rem', color: 'var(--text-white)', fontWeight: 700, borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
                      Modification du Profil
                    </h4>

                    {/* Pseudonyme */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Pseudonyme de l'Athlète</label>
                      <input 
                        type="text"
                        className="input-field"
                        value={newPseudonyme}
                        onChange={(e) => setNewPseudonyme(e.target.value)}
                        placeholder="Nouveau pseudonyme..."
                      />
                    </div>

                    {/* Empire Color */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Couleur Hexadécimale d'Empire</label>
                      <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                        <input 
                          type="color"
                          value={newEmpireColor}
                          onChange={(e) => setNewEmpireColor(e.target.value)}
                          style={{ width: '42px', height: '42px', border: '1px solid var(--border-color)', borderRadius: '6px', background: 'transparent', cursor: 'pointer' }}
                        />
                        <input 
                          type="text"
                          className="input-field"
                          value={newEmpireColor}
                          onChange={(e) => setNewEmpireColor(e.target.value)}
                          placeholder="#CCFF00"
                          style={{ flexGrow: 1 }}
                        />
                      </div>
                    </div>

                    {/* Ghost Mode Toggle */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: '6px', marginTop: '6px' }}>
                      <div>
                        <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-white)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <EyeOff size={14} style={{ color: '#CCFF00' }} /> Mode Fantôme (Invisible)
                        </span>
                        <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                          Une fois activé, l'utilisateur est masqué de la carte publique mais reste administrable.
                        </p>
                      </div>
                      <label className="switch-container" style={{ display: 'inline-flex', alignItems: 'center', cursor: 'pointer' }}>
                        <input 
                          type="checkbox"
                          checked={newGhostMode}
                          onChange={(e) => setNewGhostMode(e.target.checked)}
                          style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: '#CCFF00' }}
                        />
                      </label>
                    </div>

                    <button 
                      className="btn btn-primary"
                      onClick={() => handleUpdateProfile()}
                      disabled={actionLoading}
                      style={{ marginTop: '8px', width: '100%', fontWeight: 'bold' }}
                    >
                      <Save size={14} /> Sauvegarder les modifications
                    </button>
                  </div>

                  {/* Destructive / Sensitive Actions Block */}
                  <div style={{ background: 'rgba(255, 75, 75, 0.02)', border: '1px solid rgba(255, 75, 75, 0.1)', borderRadius: '8px', padding: '20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <h4 style={{ fontSize: '0.9rem', color: '#FF4B4B', fontWeight: 700, borderBottom: '1px solid rgba(255, 75, 75, 0.2)', paddingBottom: '8px' }}>
                      Actions Administratives Sensibles
                    </h4>
                    
                    <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                      {selectedProfile.avatar_url && (
                        <button 
                          className="btn btn-secondary" 
                          onClick={handleRemoveAvatar} 
                          disabled={actionLoading}
                          style={{ fontSize: '0.8rem', padding: '8px 14px' }}
                        >
                          <ImageIcon size={12} /> Réinitialiser la photo de profil
                        </button>
                      )}
                      
                      {!selectedProfile.pseudonyme?.startsWith('Invité_') && (
                        <button 
                          className="btn btn-secondary" 
                          onClick={() => handleDeleteUser(selectedProfile.id, true)} 
                          disabled={actionLoading}
                          style={{ fontSize: '0.8rem', padding: '8px 14px', border: '1px solid rgba(255, 75, 75, 0.4)', color: '#FF4B4B' }}
                        >
                          <Trash2 size={12} /> Réinitialiser uniquement les données
                        </button>
                      )}
                      
                      <button 
                        className="btn btn-danger" 
                        onClick={() => handleDeleteUser(selectedProfile.id, false)} 
                        disabled={actionLoading}
                        style={{ fontSize: '0.8rem', padding: '8px 14px', marginLeft: selectedProfile.pseudonyme?.startsWith('Invité_') ? 'auto' : '0' }}
                      >
                        <Trash2 size={12} /> Supprimer définitivement le compte
                      </button>
                    </div>
                  </div>

                </div>
              )}

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
