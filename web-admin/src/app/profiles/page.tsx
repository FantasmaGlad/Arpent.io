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
  ghost_mode: boolean;
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
  const [streak, setStreak] = useState<number>(0);

  // Tabs for user inspector modal
  const [activeTab, setActiveTab] = useState<'apercu' | 'conquete' | 'entrainement' | 'amis' | 'parametres'>('apercu');
  const [friends, setFriends] = useState<any[]>([]);
  const [loadingFriends, setLoadingFriends] = useState(false);

  // Modal filters
  const [minDistance, setMinDistance] = useState<number>(0);
  const [loopOnlyFilter, setLoopOnlyFilter] = useState<'all' | 'loops' | 'noloops'>('all');

  // Edit fields for Admin moderation
  const [isEditing, setIsEditing] = useState(false);
  const [newPseudonyme, setNewPseudonyme] = useState('');
  const [newTag, setNewTag] = useState('');
  const [newGhostMode, setNewGhostMode] = useState(false);
  const [newXp, setNewXp] = useState(0);
  const [newLevel, setNewLevel] = useState(1);
  const [newEmpireColor, setNewEmpireColor] = useState('#CCFF00');
  const [newTotalArea, setNewTotalArea] = useState(0);
  const [newAllTimeArea, setNewAllTimeArea] = useState(0);
  const [newMaxArea, setNewMaxArea] = useState(0);
  const [newAreaLost, setNewAreaLost] = useState(0);
  const [newLoopCount, setNewLoopCount] = useState(0);
  const [newMaxLoopDistance, setNewMaxLoopDistance] = useState(0);
  const [newShareLocation, setNewShareLocation] = useState(false);
  const [newGrade, setNewGrade] = useState('');
  const [newGuildeId, setNewGuildeId] = useState<string>('');
  
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

  // Fetch details (courses & friends) when a profile is selected
  useEffect(() => {
    if (!selectedProfile) return;

    async function fetchDetails() {
      setLoadingCourses(true);
      setLoadingFriends(true);
      setFriends([]);
      try {
        // 1. Fetch courses
        const { data: coursesData, error: coursesError } = await supabase
          .from('courses')
          .select('*')
          .eq('utilisateur_id', selectedProfile!.id)
          .order('date_debut', { ascending: false });

        if (coursesError) throw coursesError;
        setCourses((coursesData || []) as Course[]);

        // 2. Fetch friends from public.amis where relationship status is 'accepte'
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

        // 3. Fetch activity streak
        const { data: streakVal, error: streakError } = await supabase
          .rpc('get_user_streak', { p_user_id: selectedProfile!.id });
        if (!streakError && streakVal !== null) {
          setStreak(Number(streakVal));
        } else {
          setStreak(0);
        }
      } catch (err) {
        console.error('Error fetching details:', err);
      } finally {
        setLoadingCourses(false);
        setLoadingFriends(false);
      }
    }

    fetchDetails();
    setIsEditing(false);
    setNewPseudonyme(selectedProfile.pseudonyme || '');
    setNewTag(selectedProfile.tag || '');
    setNewGhostMode(selectedProfile.ghost_mode || false);
    setNewXp(selectedProfile.xp || 0);
    setNewLevel(selectedProfile.level || 1);
    setNewEmpireColor(selectedProfile.empire_color || '#CCFF00');
    setNewTotalArea(selectedProfile.total_area_m2 || 0);
    setNewAllTimeArea(selectedProfile.all_time_area_m2 || 0);
    setNewMaxArea(selectedProfile.max_area_m2 || 0);
    setNewAreaLost(selectedProfile.area_lost_m2 || 0);
    setNewLoopCount(selectedProfile.loop_count || 0);
    setNewMaxLoopDistance(selectedProfile.max_loop_distance_km || 0);
    setNewShareLocation(selectedProfile.share_location || false);
    setNewGrade(selectedProfile.grade || '');
    setNewGuildeId(selectedProfile.guilde_id || '');
    setActiveTab('apercu');
    setMinDistance(0);
    setLoopOnlyFilter('all');
  }, [selectedProfile]);

  const handleUpdateProfile = async () => {
    if (!selectedProfile) return;
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
          pseudonyme: newPseudonyme,
          tag: newTag || null,
          ghost_mode: newGhostMode,
          xp: Number(newXp),
          level: Number(newLevel),
          empire_color: newEmpireColor,
          total_area_m2: Number(newTotalArea),
          all_time_area_m2: Number(newAllTimeArea),
          max_area_m2: Number(newMaxArea),
          area_lost_m2: Number(newAreaLost),
          loop_count: Number(newLoopCount),
          max_loop_distance_km: Number(newMaxLoopDistance),
          share_location: newShareLocation,
          grade: newGrade || null,
          guilde_id: newGuildeId || null
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la mise à jour.');
      }

      setMessage({ type: 'success', text: 'Profil mis à jour avec succès.' });
      
      const updatedProfile = { 
        ...selectedProfile, 
        pseudonyme: newPseudonyme,
        tag: newTag || null,
        ghost_mode: newGhostMode,
        xp: Number(newXp),
        level: Number(newLevel),
        empire_color: newEmpireColor,
        total_area_m2: Number(newTotalArea),
        all_time_area_m2: Number(newAllTimeArea),
        max_area_m2: Number(newMaxArea),
        area_lost_m2: Number(newAreaLost),
        loop_count: Number(newLoopCount),
        max_loop_distance_km: Number(newMaxLoopDistance),
        share_location: newShareLocation,
        grade: newGrade || null,
        guilde_id: newGuildeId || null
      };
      setSelectedProfile(updatedProfile);
      setProfiles(prev => prev.map(p => p.id === selectedProfile.id ? updatedProfile : p));
      setIsEditing(false);
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

  const handleDeleteUser = async (userId: string) => {
    if (!confirm("ATTENTION : Cette action supprimera définitivement le compte utilisateur, ses relations, ses zones couvertes et ses sessions d'activité.\n\nCette action est irréversible. Continuer ?")) {
      return;
    }

    setActionLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch(`/api/admin/profiles?userId=${userId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la suppression.');
      }

      alert('Utilisateur supprimé avec succès.');
      setSelectedProfile(null);
      setProfiles(prev => prev.filter(p => p.id !== userId));
      setTotalCount(prev => Math.max(0, prev - 1));
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCourse = async (courseId: string) => {
    if (!confirm("Voulez-vous vraiment supprimer cette activité ? Cette action est irréversible et modifiera le score et les statistiques de l'utilisateur.")) {
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
        throw new Error(resData.error || "Erreur lors de la suppression de l'activité.");
      }

      setMessage({ type: 'success', text: "L'activité a été supprimée avec succès." });
      setCourses(prev => prev.filter(c => c.id !== courseId));
      
      // Update local selected profile view statistics
      if (selectedProfile) {
        const deletedCourse = courses.find(c => c.id === courseId);
        if (deletedCourse) {
          const distKm = deletedCourse.distance_totale / 1000.0;
          const isLoop = deletedCourse.est_bouclee;
          const loopBonus = isLoop ? 200 : 0;
          const xpLoss = Math.floor(100.0 * distKm) + loopBonus;
          
          const newXpVal = Math.max(0, (selectedProfile.xp || 0) - xpLoss);
          const newLvlVal = Math.floor(Math.sqrt(newXpVal / 250.0)) + 1;
          const newLoopCountVal = Math.max(0, (selectedProfile.loop_count || 0) - (isLoop ? 1 : 0));
          
          const updatedProfile = {
            ...selectedProfile,
            xp: newXpVal,
            level: newLvlVal,
            loop_count: newLoopCountVal
          };
          setSelectedProfile(updatedProfile);
          setProfiles(prev => prev.map(p => p.id === selectedProfile.id ? updatedProfile : p));
        }
      }
    } catch (err: any) {
      setMessage({ type: 'error', text: `Erreur lors de la suppression de l'activité : ${err.message}` });
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

  const formatDurationText = (sec: number) => {
    const hrs = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const remainingSecs = sec % 60;
    if (hrs > 0) return `${hrs}h ${mins}m`;
    return `${mins}m ${remainingSecs}s`;
  };

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
                    <th>Surface Couverte (km²)</th>
                    <th>Inscription</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {profiles.map((p) => {
                    const guild = guilds.find(g => g.id === p.guilde_id);
                    const color = 'var(--primary-green)';
                    return (
                      <tr key={p.id}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            {p.avatar_url ? (
                              <img src={p.avatar_url} alt="" className="avatar" style={{ borderColor: color }} />
                            ) : (
                              <div className="avatar avatar-placeholder" style={{ borderColor: color, color: color }}>
                                {p.pseudonyme ? p.pseudonyme.substring(0, 2).toUpperCase() : 'US'}
                              </div>
                            )}
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
                          <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: 'var(--primary-green)', fontWeight: 600 }}>
                            {p.tag || '—'}
                          </span>
                        </td>
                        <td>
                          {guild ? (
                            <span className="badge" style={{ backgroundColor: 'rgba(255, 255, 255, 0.02)', color: 'var(--text-white)', border: '1px solid var(--border-color)' }}>
                              {guild.nom} <span style={{ fontFamily: 'monospace', fontSize: '0.7rem', opacity: 0.8, marginLeft: '4px' }}>{guild.tag}</span>
                            </span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Indépendant</span>
                          )}
                        </td>
                        <td>
                          {p.grade === 'chef' ? (
                            <span className="badge" style={{ backgroundColor: 'rgba(204, 255, 0, 0.05)', color: 'var(--primary-green)', border: '1px solid var(--primary-green)' }}>Responsable</span>
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
                              style={{ color: 'var(--primary-green)' }}
                            >
                              <Activity size={16} />
                            </button>
                            <button 
                              className="btn-icon" 
                              title="Supprimer l'utilisateur"
                              onClick={() => handleDeleteUser(p.id)}
                              style={{ color: '#FF4B4B' }}
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

      {/* Inspector Modal with Custom Tabs */}
      {selectedProfile && (
        <div className="modal-overlay">
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
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {isEditing ? (
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <input 
                          type="text" 
                          className="input-field" 
                          value={newPseudonyme}
                          onChange={(e) => setNewPseudonyme(e.target.value)}
                          style={{ padding: '4px 10px', fontSize: '1.1rem', width: '180px' }}
                        />
                        <button className="btn btn-primary" onClick={handleUpdateProfile} disabled={actionLoading} style={{ padding: '6px 12px' }}>
                          <Save size={14} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <h2 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-white)' }}>{selectedProfile.pseudonyme || 'Utilisateur'}</h2>
                        <button className="btn-icon" onClick={() => setIsEditing(true)} style={{ padding: '4px' }}>
                          <Edit2 size={12} style={{ color: 'var(--text-muted)' }} />
                        </button>
                      </>
                    )}
                  </div>
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
                          {selectedProfile.ghost_mode ? 'ACTIVÉ (INVISIBLE)' : 'DÉSACTIVÉ (VISIBLE)'}
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
                            {new Date(selectedProfile.date_inscription).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
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
                        Mode Fantôme (Invisible sur la carte)
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
