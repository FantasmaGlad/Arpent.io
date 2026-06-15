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
  Image as ImageIcon
} from 'lucide-react';

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
}

interface Guild {
  id: string;
  nom: string;
  couleur_hex: string;
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
  points_gps_count: number;
}

export default function ProfilesPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  // Selected profile details modal
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loadingCourses, setLoadingCourses] = useState(false);

  // Edit fields
  const [isEditing, setIsEditing] = useState(false);
  const [newPseudonyme, setNewPseudonyme] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  useEffect(() => {
    fetchData();
  }, []);

  async function fetchData() {
    setLoading(true);
    try {
      const [
        { data: profilesData },
        { data: guildsData }
      ] = await Promise.all([
        supabase.from('profiles').select('*').order('total_area_m2', { ascending: false }),
        supabase.from('guildes').select('id, nom, couleur_hex')
      ]);

      setProfiles((profilesData || []) as Profile[]);
      setGuilds((guildsData || []) as Guild[]);
    } catch (err) {
      console.error('Error fetching profiles:', err);
    } finally {
      setLoading(false);
    }
  }

  // Fetch courses when a profile is selected
  useEffect(() => {
    if (!selectedProfile) return;

    async function fetchCourses() {
      setLoadingCourses(true);
      try {
        const { data, error } = await supabase
          .from('courses')
          .select('*')
          .eq('utilisateur_id', selectedProfile!.id)
          .order('date_debut', { ascending: false });

        if (error) throw error;
        setCourses((data || []) as Course[]);
      } catch (err) {
        console.error('Error fetching courses:', err);
      } finally {
        setLoadingCourses(false);
      }
    }

    fetchCourses();
    setIsEditing(false);
    setNewPseudonyme(selectedProfile.pseudonyme || '');
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
      
      // Update local state
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
    if (!selectedProfile || !confirm("Voulez-vous supprimer la photo de profil de ce joueur ?")) return;
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
    if (!confirm("ATTENTION : Cette action supprimera définitivement le compte d'authentification du joueur, son profil, ses territoires conquis, et ses sessions de course.\n\nCette action est irréversible. Voulez-vous continuer ?")) {
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

      alert('Joueur supprimé avec succès.');
      setSelectedProfile(null);
      setProfiles(prev => prev.filter(p => p.id !== userId));
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  // Filter profiles
  const filteredProfiles = profiles.filter(p => {
    const pseudo = (p.pseudonyme || '').toLowerCase();
    const tag = (p.tag || '').toLowerCase();
    const search = searchTerm.toLowerCase();
    return pseudo.includes(search) || tag.includes(search);
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header */}
      <div>
        <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Gestion des Profils</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Modération des joueurs, historique des courses et statistiques globales</p>
      </div>

      {/* Search Bar */}
      <div className="glass-card" style={{ padding: '16px 24px', display: 'flex', alignItems: 'center', gap: '16px' }}>
        <Search size={20} style={{ color: 'var(--text-muted)' }} />
        <input 
          type="text" 
          placeholder="Rechercher par pseudo ou tag (#)..." 
          className="input-field"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '1rem' }}
        />
      </div>

      {/* Profiles Table */}
      <div className="glass-card" style={{ padding: '0', overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Chargement de l'annuaire des recrues...
          </div>
        ) : filteredProfiles.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Aucun joueur ne correspond à votre recherche.
          </div>
        ) : (
          <div className="table-container">
            <table className="cyber-table">
              <thead>
                <tr>
                  <th>Joueur</th>
                  <th>Tag</th>
                  <th>Clan</th>
                  <th>Grade</th>
                  <th>Zone Conquise (km²)</th>
                  <th>Inscription</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredProfiles.map((p) => {
                  const guild = guilds.find(g => g.id === p.guilde_id);
                  return (
                    <tr key={p.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                          {p.avatar_url ? (
                            <img src={p.avatar_url} alt={p.pseudonyme || ''} className="avatar" />
                          ) : (
                            <div className="avatar avatar-placeholder">
                              {p.pseudonyme ? p.pseudonyme.substring(0, 2).toUpperCase() : 'U'}
                            </div>
                          )}
                          <div>
                            <span style={{ fontWeight: 600, color: 'var(--text-white)' }}>
                              {p.pseudonyme || 'Recrue Anonyme'}
                            </span>
                            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                              ID: {p.id.substring(0, 8)}...
                            </p>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: 'var(--electric-blue)', fontWeight: 600 }}>
                          {p.tag || '—'}
                        </span>
                      </td>
                      <td>
                        {guild ? (
                          <span className="badge" style={{ backgroundColor: 'rgba(255, 255, 255, 0.05)', color: guild.couleur_hex, border: `1px solid ${guild.couleur_hex}` }}>
                            {guild.nom}
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Autonome</span>
                        )}
                      </td>
                      <td>
                        {p.grade === 'chef' ? (
                          <span className="badge" style={{ backgroundColor: 'rgba(255, 215, 0, 0.1)', color: '#FFD700', border: '1px solid #FFD700' }}>👑 Chef</span>
                        ) : p.grade === 'adjoint' ? (
                          <span className="badge" style={{ backgroundColor: 'rgba(192, 192, 192, 0.1)', color: '#C0C0C0', border: '1px solid #C0C0C0' }}>⚔️ Adjoint</span>
                        ) : p.guilde_id ? (
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Membre</span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>—</span>
                        )}
                      </td>
                      <td style={{ fontWeight: 700, color: 'var(--neon-volt)' }}>
                        {(p.total_area_m2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
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
                            title="Inspecter le profil"
                            onClick={() => setSelectedProfile(p)}
                          >
                            <Activity size={16} style={{ color: 'var(--electric-blue)' }} />
                          </button>
                          <button 
                            className="btn-icon" 
                            title="Supprimer le joueur"
                            onClick={() => handleDeleteUser(p.id)}
                          >
                            <Trash2 size={16} style={{ color: 'var(--active-orange)' }} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Inspector Modal */}
      {selectedProfile && (
        <div className="modal-overlay">
          <div className="modal-content glass-card" style={{ border: `1px solid ${selectedProfile.empire_color || 'var(--electric-blue)'}` }}>
            {/* Modal Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                {selectedProfile.avatar_url ? (
                  <img src={selectedProfile.avatar_url} alt="" className="avatar avatar-large" />
                ) : (
                  <div className="avatar avatar-large avatar-placeholder" style={{ fontSize: '1.8rem' }}>
                    {selectedProfile.pseudonyme ? selectedProfile.pseudonyme.substring(0, 2).toUpperCase() : 'U'}
                  </div>
                )}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {isEditing ? (
                      <input 
                        type="text" 
                        className="input-field" 
                        value={newPseudonyme}
                        onChange={(e) => setNewPseudonyme(e.target.value)}
                        style={{ padding: '6px 12px', fontSize: '1.25rem', width: '200px' }}
                      />
                    ) : (
                      <h2 style={{ fontSize: '1.5rem', fontWeight: 800 }}>{selectedProfile.pseudonyme || 'Recrue'}</h2>
                    )}

                    {isEditing ? (
                      <button className="btn-icon" onClick={handleUpdateProfile} disabled={actionLoading}>
                        <Save size={16} style={{ color: 'var(--neon-volt)' }} />
                      </button>
                    ) : (
                      <button className="btn-icon" onClick={() => setIsEditing(true)}>
                        <Edit2 size={14} style={{ color: 'var(--text-muted)' }} />
                      </button>
                    )}
                  </div>
                  <p style={{ color: 'var(--electric-blue)', fontSize: '0.95rem', marginTop: '4px', fontFamily: 'monospace', fontWeight: 700 }}>
                    {selectedProfile.tag || 'Aucun tag'}
                  </p>
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: '2px', fontFamily: 'monospace' }}>
                    ID: {selectedProfile.id}
                  </p>
                  {selectedProfile.grade && selectedProfile.guilde_id && (
                    <span style={{
                      display: 'inline-block',
                      marginTop: '6px',
                      padding: '2px 10px',
                      borderRadius: '20px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      backgroundColor: selectedProfile.grade === 'chef' ? 'rgba(255, 215, 0, 0.15)' : selectedProfile.grade === 'adjoint' ? 'rgba(192, 192, 192, 0.15)' : 'rgba(255,255,255,0.05)',
                      color: selectedProfile.grade === 'chef' ? '#FFD700' : selectedProfile.grade === 'adjoint' ? '#C0C0C0' : 'var(--text-muted)',
                      border: `1px solid ${selectedProfile.grade === 'chef' ? '#FFD700' : selectedProfile.grade === 'adjoint' ? '#C0C0C0' : 'var(--border-color)'}`,
                    }}>
                      {selectedProfile.grade === 'chef' ? '👑 Chef' : selectedProfile.grade === 'adjoint' ? '⚔️ Adjoint' : 'Membre'}
                    </span>
                  )}
                </div>
              </div>
              <button className="btn-icon" onClick={() => setSelectedProfile(null)}>
                <X size={20} />
              </button>
            </div>

            {/* Notification message */}
            {message && (
              <div style={{
                padding: '10px 16px',
                borderRadius: '8px',
                marginBottom: '16px',
                fontSize: '0.9rem',
                backgroundColor: message.type === 'success' ? 'rgba(204, 255, 0, 0.1)' : 'rgba(255, 109, 0, 0.1)',
                border: message.type === 'success' ? '1px solid var(--neon-volt)' : '1px solid var(--active-orange)',
                color: message.type === 'success' ? 'var(--neon-volt)' : 'var(--active-orange)'
              }}>
                {message.text}
              </div>
            )}

            {/* Profile actions panel */}
            <div style={{ display: 'flex', gap: '12px', marginBottom: '24px' }}>
              {selectedProfile.avatar_url && (
                <button className="btn btn-secondary" onClick={handleRemoveAvatar} disabled={actionLoading} style={{ padding: '8px 16px', fontSize: '0.85rem' }}>
                  <ImageIcon size={14} /> Supprimer l'avatar
                </button>
              )}
              <button className="btn btn-danger" onClick={() => handleDeleteUser(selectedProfile.id)} disabled={actionLoading} style={{ padding: '8px 16px', fontSize: '0.85rem', marginLeft: 'auto' }}>
                <Trash2 size={14} /> Bannir le joueur
              </button>
            </div>

            {/* Stats Summary */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '32px' }}>
              <div style={{ background: 'rgba(15, 19, 24, 0.4)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', textTransform: 'uppercase', fontWeight: 600 }}>Surface Territoriale</span>
                <p style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--neon-volt)', marginTop: '4px' }}>
                  {(selectedProfile.total_area_m2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
                </p>
              </div>
              <div style={{ background: 'rgba(15, 19, 24, 0.4)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', textTransform: 'uppercase', fontWeight: 600 }}>Date d'enrôlement</span>
                <p style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-white)', marginTop: '4px' }}>
                  {new Date(selectedProfile.date_inscription).toLocaleDateString('fr-FR')}
                </p>
              </div>
            </div>

            {/* Run History */}
            <div>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Clock size={18} style={{ color: 'var(--electric-blue)' }} /> Historique des Sessions de Course
              </h3>

              {loadingCourses ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chargement du journal d'entraînement...</p>
              ) : courses.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', padding: '16px', border: '1px dashed var(--border-color)', borderRadius: '8px', textAlign: 'center' }}>
                  Aucune course enregistrée pour cette recrue.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '250px', overflowY: 'auto', paddingRight: '8px' }}>
                  {courses.map((c) => {
                    const durationMin = Math.floor(c.duree_secondes / 60);
                    const durationSec = Math.floor(c.duree_secondes % 60);
                    const distKm = c.distance_totale / 1000;
                    return (
                      <div 
                        key={c.id}
                        style={{
                          background: 'rgba(15, 19, 24, 0.2)',
                          border: '1px solid var(--border-color)',
                          borderRadius: '8px',
                          padding: '14px 16px',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                          <div>
                            <p style={{ fontWeight: 600, fontSize: '0.95rem' }}>
                              Course du {new Date(c.date_debut).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })}
                            </p>
                            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                              Statut : {c.est_bouclee ? (
                                <span style={{ color: 'var(--neon-volt)' }}>Terminée (Boucle)</span>
                              ) : (
                                <span style={{ color: 'var(--active-orange)' }}>Incomplète</span>
                              )}
                            </p>
                          </div>
                          <div style={{ textAlign: 'right' }}>
                            <p style={{ fontWeight: 700, color: 'var(--electric-blue)', fontSize: '1.1rem' }}>{distKm.toFixed(2)} km</p>
                            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                              {durationMin}m {durationSec}s
                            </p>
                          </div>
                        </div>
                        {/* Strava-like metrics */}
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px', marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--border-color)' }}>
                          <div style={{ textAlign: 'center' }}>
                            <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Vit. Moy</p>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--neon-volt)' }}>{(c.vitesse_moyenne || 0).toFixed(1)} km/h</p>
                          </div>
                          <div style={{ textAlign: 'center' }}>
                            <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Allure</p>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--electric-blue)' }}>{(c.allure_moyenne || 0).toFixed(1)} min/km</p>
                          </div>
                          <div style={{ textAlign: 'center' }}>
                            <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Calories</p>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--active-orange)' }}>{Math.round(c.calories_estimees || 0)} kcal</p>
                          </div>
                          <div style={{ textAlign: 'center' }}>
                            <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>D+/D-</p>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-white)' }}>+{Math.round(c.denivele_positif || 0)}/-{Math.round(c.denivele_negatif || 0)}m</p>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
