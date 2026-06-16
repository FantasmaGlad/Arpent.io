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
  const [activeTab, setActiveTab] = useState<'dossier' | 'amis' | 'entrainement'>('dossier');
  const [friends, setFriends] = useState<any[]>([]);
  const [loadingFriends, setLoadingFriends] = useState(false);

  // Modal filters
  const [minDistance, setMinDistance] = useState<number>(0);
  const [loopOnlyFilter, setLoopOnlyFilter] = useState<'all' | 'loops' | 'noloops'>('all');

  // Edit fields
  const [isEditing, setIsEditing] = useState(false);
  const [newPseudonyme, setNewPseudonyme] = useState('');
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
    setActiveTab('dossier');
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
          pseudonyme: newPseudonyme
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la mise à jour.');
      }

      setMessage({ type: 'success', text: 'Profil mis à jour avec succès.' });
      
      const updatedProfile = { ...selectedProfile, pseudonyme: newPseudonyme };
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
    if (!confirm("Voulez-vous vraiment supprimer cette activité ? Cette action est irréversible.")) {
      return;
    }

    setActionLoading(true);
    setMessage(null);
    try {
      const { error } = await supabase
        .from('courses')
        .delete()
        .eq('id', courseId);

      if (error) throw error;

      setMessage({ type: 'success', text: "L'activité a été supprimée avec succès." });
      setCourses(prev => prev.filter(c => c.id !== courseId));
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
              padding: '0 24px'
            }}>
              <button 
                onClick={() => setActiveTab('dossier')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'dossier' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'dossier' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Fiche Utilisateur
              </button>
              <button 
                onClick={() => setActiveTab('amis')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'amis' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'amis' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Relations ({friends.length})
              </button>
              <button 
                onClick={() => setActiveTab('entrainement')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'entrainement' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'entrainement' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Activités ({courses.length})
              </button>
            </div>

            {/* Modal Body Container */}
            <div style={{ padding: '24px', maxHeight: '550px', overflowY: 'auto' }}>
              
              {/* TAB 1: USER DOSSIER */}
              {activeTab === 'dossier' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  {/* Account Actions */}
                  <div style={{ display: 'flex', gap: '12px', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)' }}>
                    {selectedProfile.avatar_url && (
                      <button className="btn btn-secondary" onClick={handleRemoveAvatar} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem' }}>
                        <ImageIcon size={12} /> Supprimer la photo
                      </button>
                    )}
                    <button className="btn btn-danger" onClick={() => handleDeleteUser(selectedProfile.id)} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem', marginLeft: 'auto' }}>
                      <Trash2 size={12} /> Supprimer définitivement l'utilisateur
                    </button>
                  </div>

                  {/* Double column details */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                      <h4 style={{ fontSize: '0.9rem', color: 'var(--primary-green)', textTransform: 'uppercase', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px' }}>Identité Réseau</h4>
                      
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>UUID Base de données</span>
                        <p style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px', wordBreak: 'break-all' }}>{selectedProfile.id}</p>
                      </div>

                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Date d'inscription</span>
                        <p style={{ fontSize: '0.9rem', color: 'var(--text-white)', marginTop: '2px' }}>
                          {new Date(selectedProfile.date_inscription).toLocaleDateString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
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
                      <h4 style={{ fontSize: '0.9rem', color: 'var(--primary-green)', textTransform: 'uppercase', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px' }}>Positionnement GPS</h4>
                      
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Partage de position</span>
                        <p style={{ fontSize: '0.9rem', fontWeight: 'bold', color: selectedProfile.share_location ? 'var(--primary-green)' : '#FF4B4B', marginTop: '2px' }}>
                          {selectedProfile.share_location ? 'ACTIVÉ (EN LIGNE)' : 'DÉSACTIVÉ (HORS LIGNE)'}
                        </p>
                      </div>

                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Dernière position connue</span>
                        <p style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px' }}>
                          {selectedProfile.latitude && selectedProfile.longitude ? (
                            `${selectedProfile.latitude.toFixed(6)}, ${selectedProfile.longitude.toFixed(6)}`
                          ) : (
                            'Aucune coordonnée enregistrée'
                          )}
                        </p>
                      </div>

                      {selectedProfile.share_location && selectedProfile.latitude && selectedProfile.longitude && (
                        <button 
                          className="btn btn-secondary" 
                          onClick={() => centerPlayerOnMap(selectedProfile.latitude!, selectedProfile.longitude!)}
                          style={{ width: '100%', marginTop: '8px', padding: '8px 12px', fontSize: '0.8rem' }}
                        >
                          <MapPin size={12} /> Centrer sur la carte globale
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Area Section */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-color)', marginTop: '10px' }}>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Couverte</span>
                      <p style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '4px' }}>
                        {(selectedProfile.total_area_m2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Rôle du Groupe</span>
                      <p style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-white)', marginTop: '4px' }}>
                        {selectedProfile.grade === 'chef' ? '👑 Responsable' : selectedProfile.grade === 'adjoint' ? '👥 Adjoint' : selectedProfile.guilde_id ? 'Membre' : 'Utilisateur Indépendant'}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 2: SOCIAL NETWORK (FRIENDS) */}
              {activeTab === 'amis' && (
                <div>
                  <h4 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Users size={18} style={{ color: 'var(--primary-green)' }} /> Liste des relations ({friends.length})
                  </h4>

                  {loadingFriends ? (
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chargement des relations...</p>
                  ) : friends.length === 0 ? (
                    <div style={{ padding: '32px', textAlign: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Cet utilisateur n'a pas encore établi de relations sur le réseau.</p>
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
                            <img src={f.avatar_url} alt="" className="avatar" style={{ width: '36px', height: '36px', borderColor: 'var(--primary-green)' }} />
                          ) : (
                            <div className="avatar avatar-placeholder" style={{ width: '36px', height: '36px', borderColor: 'var(--primary-green)', color: 'var(--primary-green)', fontSize: '0.8rem' }}>
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
                            <p style={{ fontSize: '0.75rem', fontWeight: 'bold', color: 'var(--primary-green)' }}>
                              {(f.total_area_m2 / 1000000).toFixed(3)} km²
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* TAB 3: TRAINING SESSIONS AND STATS */}
              {activeTab === 'entrainement' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Cumulative stats block */}
                  <div>
                    <h4 style={{ fontSize: '0.95rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 600 }}>Statistiques d'Activité Cumulées</h4>
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
                    <h4 style={{ fontSize: '0.95rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 600 }}>Graphique d'Évolution (Distance & Temps)</h4>
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
                    <h4 style={{ fontSize: '0.95rem', color: 'var(--primary-green)', textTransform: 'uppercase', marginBottom: '12px', fontWeight: 600 }}>Historique des activités ({filteredCourses.length})</h4>
                    
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

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
