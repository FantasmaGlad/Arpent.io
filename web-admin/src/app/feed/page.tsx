'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Search, 
  Trash2, 
  Edit2, 
  Calendar,
  X,
  User,
  Save,
  MapPin,
  Activity,
  Clock,
  Compass,
  Footprints,
  Zap,
  Info
} from 'lucide-react';

interface Course {
  id: string;
  utilisateur_id: string;
  date_debut: string;
  date_fin: string | null;
  distance_totale: number;
  duree_secondes: number;
  est_bouclee: boolean;
  vitesse_moyenne: number | null;
  vitesse_max: number | null;
  allure_moyenne: number | null;
  calories_estimees: number | null;
  denivele_positif: number | null;
  denivele_negatif: number | null;
  points_gps_count: number;
  nom: string | null;
  legende: string | null;
  superficie_conquise: number | null;
  total_steps: number | null;
  average_cadence: number | null;
  profiles?: {
    id: string;
    pseudonyme: string | null;
    tag: string | null;
    avatar_url: string | null;
    empire_color: string;
  } | null;
}

export default function FeedModerationPage() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  
  // Filters, sorting, and pagination
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('date_debut_desc');
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 12;

  // Edit Modal State
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null);
  const [editNom, setEditNom] = useState('');
  const [editLegende, setEditLegende] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  useEffect(() => {
    fetchCourses();
  }, []);

  async function fetchCourses() {
    setLoading(true);
    try {
      const { data, error } = await supabase
        .from('courses')
        .select(`
          *,
          profiles:utilisateur_id (
            id,
            pseudonyme,
            tag,
            avatar_url,
            empire_color
          )
        `)
        .order('date_debut', { ascending: false });

      if (error) throw error;
      setCourses((data || []) as Course[]);
    } catch (err) {
      console.error('Error fetching courses:', err);
    } finally {
      setLoading(false);
    }
  }

  // Reset page when filtering or sorting changes
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, sortBy]);

  // Handle Edit Action Setup
  const handleOpenEdit = (course: Course) => {
    setSelectedCourse(course);
    setEditNom(course.nom || '');
    setEditLegende(course.legende || '');
    setMessage(null);
  };

  const handleUpdateCourse = async () => {
    if (!selectedCourse) return;
    setActionLoading(true);
    setMessage(null);

    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch('/api/admin/courses', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          courseId: selectedCourse.id,
          nom: editNom,
          legende: editLegende
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la mise à jour.');
      }

      setMessage({ type: 'success', text: 'Course mise à jour avec succès.' });

      const updatedCourse = { ...selectedCourse, nom: editNom, legende: editLegende };
      setSelectedCourse(updatedCourse);
      setCourses(prev => prev.map(c => c.id === selectedCourse.id ? updatedCourse : c));
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCourse = async (courseId: string) => {
    if (!confirm("Voulez-vous vraiment supprimer cette course ?\nCette action est irréversible et supprimera également les zones et XP associés.")) {
      return;
    }

    setActionLoading(true);
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
        throw new Error(resData.error || 'Erreur lors de la suppression.');
      }

      alert('Course supprimée avec succès.');
      if (selectedCourse?.id === courseId) {
        setSelectedCourse(null);
      }
      setCourses(prev => prev.filter(c => c.id !== courseId));
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const centerCourseOnMap = async (courseId: string) => {
    try {
      const { data, error } = await supabase
        .from('points_gps')
        .select('latitude, longitude')
        .eq('course_id', courseId)
        .order('date_creation', { ascending: true })
        .limit(1);

      if (error) throw error;

      if (data && data.length > 0) {
        const { latitude, longitude } = data[0];
        localStorage.setItem('map_center_lat', latitude.toString());
        localStorage.setItem('map_center_lng', longitude.toString());
        window.location.href = '/';
      } else {
        alert("Aucun point GPS enregistré pour cette course.");
      }
    } catch (err: any) {
      alert(`Erreur lors de la récupération des coordonnées : ${err.message}`);
    }
  };

  const formatDuration = (seconds: number) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.round(seconds % 60);
    if (hrs > 0) {
      return `${hrs}h ${mins}m ${secs}s`;
    }
    return `${mins}m ${secs}s`;
  };

  // Filter and sort courses
  const filteredCourses = courses
    .filter(c => {
      const search = searchTerm.toLowerCase();
      const nom = (c.nom || '').toLowerCase();
      const legende = (c.legende || '').toLowerCase();
      const pseudo = (c.profiles?.pseudonyme || '').toLowerCase();
      const tag = (c.profiles?.tag || '').toLowerCase();
      return nom.includes(search) || legende.includes(search) || pseudo.includes(search) || tag.includes(search);
    })
    .sort((a, b) => {
      if (sortBy === 'date_debut_desc') {
        return new Date(b.date_debut).getTime() - new Date(a.date_debut).getTime();
      } else if (sortBy === 'date_debut_asc') {
        return new Date(a.date_debut).getTime() - new Date(b.date_debut).getTime();
      } else if (sortBy === 'distance_desc') {
        return b.distance_totale - a.distance_totale;
      } else if (sortBy === 'distance_asc') {
        return a.distance_totale - b.distance_totale;
      } else if (sortBy === 'duree_desc') {
        return b.duree_secondes - a.duree_secondes;
      } else if (sortBy === 'speed_desc') {
        return (b.vitesse_moyenne || 0) - (a.vitesse_moyenne || 0);
      }
      return 0;
    });

  // Pagination
  const totalPages = Math.ceil(filteredCourses.length / pageSize);
  const paginatedCourses = filteredCourses.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header */}
      <div>
        <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Modération du Feed</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>
          Gérer et modérer les publications d'activités de course de la communauté
        </p>
      </div>

      {/* Filter and search bar */}
      <div className="glass-card" style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <Search size={20} style={{ color: 'var(--text-muted)' }} />
          <input 
            type="text" 
            placeholder="Rechercher une course par nom, description ou athlète..." 
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '1rem', width: '100%' }}
          />
        </div>

        <div>
          <select 
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="input-field"
            style={{ padding: '8px 12px', fontSize: '0.85rem' }}
          >
            <option value="date_debut_desc">Les plus récentes</option>
            <option value="date_debut_asc">Les plus anciennes</option>
            <option value="distance_desc">Distance (Max)</option>
            <option value="distance_asc">Distance (Min)</option>
            <option value="duree_desc">Durée de course (Max)</option>
            <option value="speed_desc">Vitesse Moyenne (Max)</option>
          </select>
        </div>
      </div>

      {/* Courses List Grid */}
      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Chargement du feed communautaire...
        </div>
      ) : filteredCourses.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Aucune course trouvée.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(350px, 1fr))', gap: '24px' }}>
            {paginatedCourses.map((c) => {
              const distanceKm = c.distance_totale / 1000;
              const athleteColor = c.profiles?.empire_color || '#CCFF00';
              const dateText = new Date(c.date_debut).toLocaleDateString('fr-FR', {
                day: 'numeric',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
              });

              return (
                <div 
                  key={c.id} 
                  className="glass-card interactive" 
                  style={{ 
                    borderLeft: `4px solid ${athleteColor}`,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    gap: '16px',
                    padding: '20px'
                  }}
                >
                  <div>
                    {/* User Header Info */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        {c.profiles?.avatar_url ? (
                          <img 
                            src={c.profiles.avatar_url} 
                            alt="" 
                            className="avatar" 
                            style={{ width: '36px', height: '36px', borderColor: athleteColor }} 
                          />
                        ) : (
                          <div 
                            className="avatar avatar-placeholder" 
                            style={{ width: '36px', height: '36px', color: athleteColor, borderColor: athleteColor, fontSize: '0.8rem' }}
                          >
                            {c.profiles?.pseudonyme?.substring(0, 2).toUpperCase() || 'US'}
                          </div>
                        )}
                        <div>
                          <span style={{ fontWeight: 800, fontSize: '0.95rem', color: 'var(--text-white)' }}>
                            {c.profiles?.pseudonyme || 'Athlète'}
                          </span>
                          <span style={{ marginLeft: '6px', fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                            {c.profiles?.tag || ''}
                          </span>
                          <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Calendar size={11} /> {dateText}
                          </p>
                        </div>
                      </div>

                      {/* Top Action Icons */}
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button 
                          className="btn-icon" 
                          onClick={() => handleOpenEdit(c)} 
                          title="Modifier le titre/description" 
                          style={{ color: 'var(--primary-green)' }}
                        >
                          <Edit2 size={15} />
                        </button>
                        <button 
                          className="btn-icon" 
                          onClick={() => centerCourseOnMap(c.id)} 
                          title="Voir sur la carte" 
                          style={{ color: '#00D8FF' }}
                        >
                          <MapPin size={15} />
                        </button>
                        <button 
                          className="btn-icon" 
                          onClick={() => handleDeleteCourse(c.id)} 
                          title="Supprimer la course" 
                          style={{ color: '#FF4B4B' }}
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>

                    {/* Course Title and Legend */}
                    <div style={{ marginTop: '14px' }}>
                      <h3 style={{ fontWeight: 800, fontSize: '1.15rem', color: 'var(--text-white)' }}>
                        {c.nom || 'Course sans nom'}
                      </h3>
                      {c.legende && (
                        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '4px', fontStyle: 'italic' }}>
                          « {c.legende} »
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Course Metrics Stats Grid */}
                  <div style={{ 
                    display: 'grid', 
                    gridTemplateColumns: 'repeat(3, 1fr)', 
                    gap: '10px', 
                    background: 'rgba(255, 255, 255, 0.01)', 
                    padding: '12px', 
                    borderRadius: '8px', 
                    border: '1px solid var(--border-color)' 
                  }}>
                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Distance</span>
                      <p style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                        {distanceKm.toFixed(2)} km
                      </p>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Durée</span>
                      <p style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                        {formatDuration(c.duree_secondes)}
                      </p>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Vitesse Moy</span>
                      <p style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>
                        {(c.vitesse_moyenne || 0).toFixed(1)} km/h
                      </p>
                    </div>

                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Dénivelé</span>
                      <p style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>
                        +{Math.round(c.denivele_positif || 0)}m
                      </p>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Surface Conq.</span>
                      <p style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                        {Math.round(c.superficie_conquise || 0)} m²
                      </p>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Type</span>
                      <p style={{ fontSize: '0.85rem', fontWeight: 700, color: c.est_bouclee ? 'var(--primary-green)' : 'var(--text-muted)', marginTop: '2px' }}>
                        {c.est_bouclee ? '🔄 Bouclée' : '📍 Ligne'}
                      </p>
                    </div>

                    {c.total_steps ? (
                      <div style={{ gridColumn: 'span 3', borderTop: '1px solid var(--border-color)', paddingTop: '6px', marginTop: '2px', display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><Footprints size={12} /> {c.total_steps.toLocaleString()} pas</span>
                        {c.average_cadence ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><Zap size={12} /> {c.average_cadence} ppm</span>
                        ) : null}
                        {c.calories_estimees ? (
                          <span>🔥 {Math.round(c.calories_estimees)} kcal</span>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px', marginTop: '16px' }}>
              <button
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                className="btn btn-secondary"
                style={{ padding: '8px 16px', opacity: currentPage === 1 ? 0.5 : 1, cursor: currentPage === 1 ? 'not-allowed' : 'pointer' }}
              >
                Précédent
              </button>
              <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem', fontFamily: 'monospace' }}>
                Page {currentPage} sur {totalPages} ({filteredCourses.length} courses)
              </span>
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                className="btn btn-secondary"
                style={{ padding: '8px 16px', opacity: currentPage === totalPages ? 0.5 : 1, cursor: currentPage === totalPages ? 'not-allowed' : 'pointer' }}
              >
                Suivant
              </button>
            </div>
          )}
        </div>
      )}

      {/* Moderation Edit Modal */}
      {selectedCourse && (
        <div className="modal-overlay">
          <div className="modal-content glass-card" style={{ 
            border: `1px solid var(--border-color)`,
            maxWidth: '500px',
            padding: '0',
            overflow: 'hidden'
          }}>
            
            {/* Header */}
            <div style={{
              background: 'var(--card-bg)',
              padding: '20px 24px',
              borderBottom: '1px solid var(--border-color)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-white)' }}>
                  Modération de Course
                </h2>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                  Athlète : {selectedCourse.profiles?.pseudonyme || 'Inconnu'} ({selectedCourse.profiles?.tag})
                </p>
              </div>
              
              <button 
                onClick={() => setSelectedCourse(null)}
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
                  cursor: 'pointer'
                }}
              >
                <X size={16} />
              </button>
            </div>

            {/* Notification Messages */}
            {message && (
              <div style={{
                margin: '16px 24px 0 24px',
                padding: '10px 16px',
                borderRadius: '8px',
                fontSize: '0.85rem',
                backgroundColor: message.type === 'success' ? 'rgba(204, 255, 0, 0.05)' : 'rgba(255, 75, 75, 0.05)',
                border: message.type === 'success' ? '1px solid var(--primary-green)' : '1px solid #FF4B4B',
                color: message.type === 'success' ? 'var(--primary-green)' : '#FF4B4B'
              }}>
                {message.text}
              </div>
            )}

            {/* Form */}
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Titre de la course</label>
                <input 
                  type="text"
                  className="input-field"
                  value={editNom}
                  onChange={(e) => setEditNom(e.target.value)}
                  placeholder="Nom de l'activité..."
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Description / Légende</label>
                <textarea 
                  className="input-field"
                  value={editLegende}
                  onChange={(e) => setEditLegende(e.target.value)}
                  placeholder="Description..."
                  rows={4}
                  style={{ resize: 'vertical', fontFamily: 'inherit', fontSize: '0.9rem' }}
                />
              </div>

              {/* ID & Date Information */}
              <div style={{ 
                background: 'rgba(255,255,255,0.01)', 
                border: '1px solid var(--border-color)', 
                borderRadius: '6px', 
                padding: '12px',
                fontSize: '0.75rem',
                color: 'var(--text-muted)',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px'
              }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Info size={12} /> ID Course : <span style={{ fontFamily: 'monospace', color: 'var(--text-white)' }}>{selectedCourse.id}</span>
                </span>
                <span>
                  Date : {new Date(selectedCourse.date_debut).toLocaleDateString('fr-FR', { dateStyle: 'full' })}
                </span>
              </div>

              <div style={{ display: 'flex', gap: '12px', marginTop: '6px' }}>
                <button 
                  className="btn btn-secondary" 
                  onClick={() => setSelectedCourse(null)} 
                  style={{ flex: 1 }}
                >
                  Fermer
                </button>
                <button 
                  className="btn btn-primary" 
                  onClick={handleUpdateCourse} 
                  disabled={actionLoading}
                  style={{ flex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                >
                  <Save size={14} /> Sauvegarder
                </button>
              </div>
            </div>

          </div>
        </div>
      )}
    </div>
  );
}
