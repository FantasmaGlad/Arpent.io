'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Search, 
  Trash2, 
  Edit2, 
  Users,
  Calendar,
  X,
  User,
  Save,
  Image as ImageIcon,
  MapPin,
  ChevronRight,
  Activity
} from 'lucide-react';

interface Guild {
  id: string;
  nom: string;
  tag: string | null;
  couleur_hex: string;
  avatar_url: string | null;
  chef_id: string | null;
  date_creation: string;
}

interface Profile {
  id: string;
  pseudonyme: string | null;
  tag: string | null;
  guilde_id: string | null;
  grade: string | null;
  avatar_url: string | null;
}

interface Territory {
  id: string;
  guilde_id: string | null;
  superficie_m2: number;
  points: string[];
}

export default function ClansPage() {
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Filters and sorting
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('total_area_desc');

  // Selected guild details modal
  const [selectedGuild, setSelectedGuild] = useState<Guild | null>(null);
  const [guildMembers, setGuildMembers] = useState<Profile[]>([]);
  const [guildTerritories, setGuildTerritories] = useState<Territory[]>([]);
  
  // Tab inside modal
  const [activeTab, setActiveTab] = useState<'generale' | 'membres' | 'territoires'>('generale');
  const [memberSearchTerm, setMemberSearchTerm] = useState('');

  // Edit fields
  const [isEditing, setIsEditing] = useState(false);
  const [newNom, setNewNom] = useState('');
  const [newCouleur, setNewCouleur] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  useEffect(() => {
    fetchData();
  }, []);

  async function fetchData() {
    setLoading(true);
    try {
      const [
        { data: guildsData },
        { data: profilesData },
        { data: territoriesData }
      ] = await Promise.all([
        supabase.from('guildes').select('*').order('date_creation', { ascending: false }),
        supabase.from('profiles').select('id, pseudonyme, tag, guilde_id, grade, avatar_url'),
        supabase.from('territoires').select('id, guilde_id, superficie_m2, points')
      ]);

      setGuilds((guildsData || []) as Guild[]);
      setProfiles((profilesData || []) as Profile[]);
      setTerritories((territoriesData || []) as Territory[]);
    } catch (err) {
      console.error('Error fetching clans:', err);
    } finally {
      setLoading(false);
    }
  }

  // Calculate guild specific details when a guild is selected
  useEffect(() => {
    if (!selectedGuild) return;

    // Filter members and territories
    const members = profiles.filter(p => p.guilde_id === selectedGuild.id);
    setGuildMembers(members);

    const guildTerrs = territories.filter(t => t.guilde_id === selectedGuild.id);
    setGuildTerritories(guildTerrs);

    setIsEditing(false);
    setNewNom(selectedGuild.nom);
    setNewCouleur(selectedGuild.couleur_hex);
    setMessage(null);
    setActiveTab('generale');
    setMemberSearchTerm('');
  }, [selectedGuild, profiles, territories]);

  const handleUpdateGuild = async () => {
    if (!selectedGuild) return;
    setActionLoading(true);
    setMessage(null);

    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch('/api/admin/guilds', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          guildId: selectedGuild.id,
          nom: newNom,
          couleurHex: newCouleur
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la mise à jour.');
      }

      setMessage({ type: 'success', text: 'Groupe mis à jour avec succès.' });

      const updatedGuild = { ...selectedGuild, nom: newNom, couleur_hex: newCouleur };
      setSelectedGuild(updatedGuild);
      setGuilds(prev => prev.map(g => g.id === selectedGuild.id ? updatedGuild : g));
      setIsEditing(false);
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveAvatar = async () => {
    if (!selectedGuild || !confirm("Voulez-vous supprimer l'emblème de ce groupe ?")) return;
    setActionLoading(true);
    setMessage(null);

    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch('/api/admin/guilds', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          guildId: selectedGuild.id,
          avatarUrl: null
        })
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || "Erreur lors de la suppression de l'emblème.");
      }

      setMessage({ type: 'success', text: "L'emblème a été réinitialisé." });

      const updatedGuild = { ...selectedGuild, avatar_url: null };
      setSelectedGuild(updatedGuild);
      setGuilds(prev => prev.map(g => g.id === selectedGuild.id ? updatedGuild : g));
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteGuild = async (guildId: string) => {
    if (!confirm("Voulez-vous vraiment dissoudre ce groupe ?\n\nTous ses membres deviendront indépendants, et les zones enregistrées par ce groupe ne lui seront plus rattachées.\n\nCette action est irréversible. Continuer ?")) {
      return;
    }

    setActionLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const token = session?.access_token;

      const response = await fetch(`/api/admin/guilds?guildId=${guildId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      const resData = await response.json();
      if (!response.ok) {
        throw new Error(resData.error || 'Erreur lors de la suppression.');
      }

      alert('Groupe supprimé avec succès.');
      setSelectedGuild(null);
      setGuilds(prev => prev.filter(g => g.id !== guildId));
      
      setProfiles(prev => prev.map(p => p.guilde_id === guildId ? { ...p, guilde_id: null } : p));
      setTerritories(prev => prev.map(t => t.guilde_id === guildId ? { ...t, guilde_id: null } : t));
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const centerTerritoryOnMap = (t: Territory) => {
    const pointStr = t.points?.[0];
    if (pointStr) {
      const parts = pointStr.trim().split(' ');
      if (parts.length >= 2) {
        const lng = parseFloat(parts[0]);
        const lat = parseFloat(parts[1]);
        if (!isNaN(lat) && !isNaN(lng)) {
          localStorage.setItem('map_center_lat', lat.toString());
          localStorage.setItem('map_center_lng', lng.toString());
          window.location.href = '/';
        }
      }
    }
  };

  // Filter and sort guilds list
  const filteredGuilds = guilds
    .filter(g => {
      const name = g.nom.toLowerCase();
      const tag = (g.tag || '').toLowerCase();
      const search = searchTerm.toLowerCase();
      return name.includes(search) || tag.includes(search);
    })
    .sort((a, b) => {
      const getGuildArea = (gid: string) => territories.filter(t => t.guilde_id === gid).reduce((acc, curr) => acc + curr.superficie_m2, 0);
      const getGuildMembers = (gid: string) => profiles.filter(p => p.guilde_id === gid).length;

      if (sortBy === 'nom') {
        return a.nom.localeCompare(b.nom);
      } else if (sortBy === 'total_area_desc') {
        return getGuildArea(b.id) - getGuildArea(a.id);
      } else if (sortBy === 'total_area_asc') {
        return getGuildArea(a.id) - getGuildArea(b.id);
      } else if (sortBy === 'members_desc') {
        return getGuildMembers(b.id) - getGuildMembers(a.id);
      } else if (sortBy === 'members_asc') {
        return getGuildMembers(a.id) - getGuildMembers(b.id);
      } else if (sortBy === 'date_creation_desc') {
        return new Date(b.date_creation).getTime() - new Date(a.date_creation).getTime();
      } else if (sortBy === 'date_creation_asc') {
        return new Date(a.date_creation).getTime() - new Date(b.date_creation).getTime();
      }
      return 0;
    });

  // Filter members inside the selected guild modal
  const filteredGuildMembers = guildMembers.filter(m => {
    const pseudo = (m.pseudonyme || '').toLowerCase();
    const tag = (m.tag || '').toLowerCase();
    const s = memberSearchTerm.toLowerCase();
    return pseudo.includes(s) || tag.includes(s);
  });

  const totalGuildAreaM2 = guildTerritories.reduce((acc, curr) => acc + curr.superficie_m2, 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header */}
      <div>
        <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Groupes & Équipes</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Modération des groupes et équipes d'Arpent.io</p>
      </div>

      {/* Filter and search bar */}
      <div className="glass-card" style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <Search size={20} style={{ color: 'var(--text-muted)' }} />
          <input 
            type="text" 
            placeholder="Rechercher un groupe par nom ou tag (#)..." 
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '1rem', width: '100%' }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <select 
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="input-field"
            style={{ padding: '8px 12px', fontSize: '0.85rem' }}
          >
            <option value="total_area_desc">Superficie couverte (Max)</option>
            <option value="total_area_asc">Superficie couverte (Min)</option>
            <option value="members_desc">Membres (Max)</option>
            <option value="members_asc">Membres (Min)</option>
            <option value="date_creation_desc">Créations récentes</option>
            <option value="date_creation_asc">Créations anciennes</option>
            <option value="nom">Nom (A-Z)</option>
          </select>
        </div>
      </div>

      {/* Guilds Grid */}
      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Chargement des groupes et équipes...
        </div>
      ) : filteredGuilds.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Aucun groupe n'a été trouvé.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
          {filteredGuilds.map((g) => {
            const membersCount = profiles.filter(p => p.guilde_id === g.id).length;
            const area = territories.filter(t => t.guilde_id === g.id).reduce((acc, curr) => acc + curr.superficie_m2, 0);
            const leader = profiles.find(p => p.id === g.chef_id);

            return (
              <div 
                key={g.id} 
                className="glass-card interactive" 
                style={{ 
                  borderLeft: `4px solid ${g.couleur_hex || 'var(--border-color)'}`,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '16px'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexGrow: 1 }}>
                    {g.avatar_url ? (
                      <img src={g.avatar_url} alt="" className="avatar" style={{ borderColor: g.couleur_hex }} />
                    ) : (
                      <div className="avatar avatar-placeholder" style={{ color: g.couleur_hex || 'var(--primary-green)', borderColor: g.couleur_hex || 'var(--border-color)' }}>
                        {g.nom.substring(0, 2).toUpperCase()}
                      </div>
                    )}
                    <div>
                      <h3 style={{ fontWeight: 800, fontSize: '1.2rem', color: 'var(--text-white)' }}>{g.nom}</h3>
                      <p style={{ fontSize: '0.8rem', color: 'var(--primary-green)', fontFamily: 'monospace', fontWeight: 700, marginTop: '2px' }}>
                        {g.tag || ''}
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '4px' }}>
                        <User size={12} style={{ color: g.couleur_hex || 'var(--primary-green)' }} /> Responsable : {leader?.pseudonyme || 'Inconnu'}
                      </p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '6px', marginLeft: '12px' }}>
                    <button className="btn-icon" onClick={() => setSelectedGuild(g)} title="Inspecter le groupe" style={{ color: 'var(--primary-green)' }}>
                      <Activity size={16} />
                    </button>
                    <button className="btn-icon" onClick={() => handleDeleteGuild(g.id)} title="Dissoudre le groupe" style={{ color: '#FF4B4B' }}>
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', background: 'rgba(255, 255, 255, 0.01)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Membres</span>
                    <p style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Users size={14} style={{ color: 'var(--primary-green)' }} /> {membersCount}
                    </p>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Superficie</span>
                    <p style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '2px' }}>
                      {(area / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Inspector Modal with Custom Tabs */}
      {selectedGuild && (
        <div className="modal-overlay">
          <div className="modal-content glass-card" style={{ 
            border: `1px solid var(--border-color)`,
            maxWidth: '650px',
            padding: '0',
            overflow: 'hidden'
          }}>
            
            {/* Header Banner */}
            <div style={{
              background: 'var(--card-bg)',
              padding: '24px',
              borderBottom: '1px solid var(--border-color)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                {selectedGuild.avatar_url ? (
                  <img src={selectedGuild.avatar_url} alt="" className="avatar avatar-large" style={{ borderColor: selectedGuild.couleur_hex }} />
                ) : (
                  <div className="avatar avatar-large avatar-placeholder" style={{ color: selectedGuild.couleur_hex || 'var(--primary-green)', borderColor: selectedGuild.couleur_hex || 'var(--border-color)' }}>
                    {selectedGuild.nom.substring(0, 2).toUpperCase()}
                  </div>
                )}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {isEditing ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <input 
                          type="text" 
                          className="input-field" 
                          value={newNom}
                          onChange={(e) => setNewNom(e.target.value)}
                          style={{ padding: '4px 10px', fontSize: '1.1rem', width: '180px' }}
                        />
                        <input 
                          type="color" 
                          value={newCouleur}
                          onChange={(e) => setNewCouleur(e.target.value)}
                          style={{ width: '32px', height: '32px', border: 'none', background: 'transparent', cursor: 'pointer' }}
                        />
                        <button className="btn btn-primary" onClick={handleUpdateGuild} disabled={actionLoading} style={{ padding: '6px 12px' }}>
                          <Save size={14} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <h2 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-white)' }}>{selectedGuild.nom}</h2>
                        <button className="btn-icon" onClick={() => setIsEditing(true)} style={{ padding: '4px' }}>
                          <Edit2 size={12} style={{ color: 'var(--text-muted)' }} />
                        </button>
                      </>
                    )}
                  </div>
                  <p style={{ color: 'var(--primary-green)', fontSize: '0.85rem', fontFamily: 'monospace', fontWeight: 700, marginTop: '2px' }}>
                    TAG GROUPE: {selectedGuild.tag || 'NON DÉFINI'}
                  </p>
                </div>
              </div>
              
              <button 
                onClick={() => setSelectedGuild(null)}
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

            {/* Notification messages */}
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

            {/* Tabs Row */}
            <div style={{ 
              display: 'flex', 
              background: 'rgba(255, 255, 255, 0.01)', 
              borderBottom: '1px solid var(--border-color)',
              padding: '0 24px'
            }}>
              <button 
                onClick={() => setActiveTab('generale')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'generale' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'generale' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Général
              </button>
              <button 
                onClick={() => setActiveTab('membres')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'membres' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'membres' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Membres ({guildMembers.length})
              </button>
              <button 
                onClick={() => setActiveTab('territoires')}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === 'territoires' ? '2px solid var(--primary-green)' : '2px solid transparent',
                  color: activeTab === 'territoires' ? 'var(--text-white)' : 'var(--text-muted)',
                  padding: '14px 20px',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontFamily: 'var(--font-outfit)',
                  transition: 'all 0.2s'
                }}
              >
                Zones Enregistrées ({guildTerritories.length})
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: '24px', maxHeight: '500px', overflowY: 'auto' }}>
              
              {/* TAB 1: GENERAL */}
              {activeTab === 'generale' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  
                  {/* Action buttons */}
                  <div style={{ display: 'flex', gap: '12px', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                    {selectedGuild.avatar_url && (
                      <button className="btn btn-secondary" onClick={handleRemoveAvatar} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem' }}>
                        <ImageIcon size={12} /> Supprimer l'emblème
                      </button>
                    )}
                    <button className="btn btn-danger" onClick={() => handleDeleteGuild(selectedGuild.id)} disabled={actionLoading} style={{ padding: '6px 12px', fontSize: '0.8rem', marginLeft: 'auto' }}>
                      <Trash2 size={12} /> Dissoudre le groupe
                    </button>
                  </div>

                  {/* Summary details */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Identifiant de Groupe</span>
                        <p style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)', marginTop: '2px', wordBreak: 'break-all' }}>{selectedGuild.id}</p>
                      </div>

                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Date de création</span>
                        <p style={{ fontSize: '0.9rem', color: 'var(--text-white)', marginTop: '2px' }}>
                          {new Date(selectedGuild.date_creation).toLocaleDateString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </p>
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Couleur de Groupe</span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                          <span style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '3px', backgroundColor: selectedGuild.couleur_hex }} />
                          <span style={{ fontSize: '0.85rem', fontFamily: 'monospace', color: 'var(--text-white)' }}>{selectedGuild.couleur_hex}</span>
                        </div>
                      </div>

                      <div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Responsable Principal</span>
                        <p style={{ fontSize: '0.9rem', color: 'var(--text-white)', marginTop: '2px', fontWeight: 'bold' }}>
                          {profiles.find(p => p.id === selectedGuild.chef_id)?.pseudonyme || 'Inconnu / Aucun'}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Territory totals */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', background: 'rgba(255, 255, 255, 0.01)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Superficie Totale</span>
                      <p style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--primary-green)', marginTop: '4px' }}>
                        {(totalGuildAreaM2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                      </p>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Zones Enregistrées</span>
                      <p style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '4px' }}>
                        {guildTerritories.length}
                      </p>
                    </div>
                  </div>

                </div>
              )}

              {/* TAB 2: MEMBERS */}
              {activeTab === 'membres' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  
                  {/* Search inside members */}
                  <div className="glass-card" style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px 16px' }}>
                    <Search size={16} style={{ color: 'var(--text-muted)' }} />
                    <input 
                      type="text" 
                      placeholder="Filtrer les membres..." 
                      className="input-field" 
                      value={memberSearchTerm}
                      onChange={(e) => setMemberSearchTerm(e.target.value)}
                      style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '0.85rem' }}
                    />
                  </div>

                  {filteredGuildMembers.length === 0 ? (
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center', padding: '20px' }}>Aucun membre ne correspond.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {filteredGuildMembers.map((m) => (
                        <div 
                          key={m.id}
                          style={{
                            background: 'rgba(255, 255, 255, 0.01)',
                            border: '1px solid var(--border-color)',
                            borderRadius: '8px',
                            padding: '10px 14px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            {m.avatar_url ? (
                               <img src={m.avatar_url} alt="" className="avatar" style={{ width: '28px', height: '28px' }} />
                            ) : (
                              <div className="avatar avatar-placeholder" style={{ width: '28px', height: '28px', fontSize: '0.75rem' }}>
                                {m.pseudonyme?.substring(0, 2).toUpperCase() || 'US'}
                              </div>
                            )}
                            <div>
                              <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-white)' }}>{m.pseudonyme || 'Utilisateur'}</span>
                              <span style={{ marginLeft: '8px', fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>{m.tag || ''}</span>
                            </div>
                          </div>

                          <div>
                            {m.grade === 'chef' ? (
                              <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: '12px', backgroundColor: 'rgba(204, 255, 0, 0.05)', color: 'var(--primary-green)', border: '1px solid var(--primary-green)', fontWeight: 700 }}>Responsable</span>
                            ) : m.grade === 'adjoint' ? (
                              <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: '12px', backgroundColor: 'rgba(255, 255, 255, 0.05)', color: 'var(--text-white)', border: '1px solid var(--border-color)', fontWeight: 700 }}>Adjoint</span>
                            ) : (
                              <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: '12px', backgroundColor: 'rgba(255, 255, 255, 0.02)', color: 'var(--text-muted)', border: '1px solid var(--border-color)' }}>Membre</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                </div>
              )}

              {/* TAB 3: TERRITORIES */}
              {activeTab === 'territoires' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  
                  {guildTerritories.length === 0 ? (
                    <div style={{ padding: '32px', textAlign: 'center', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Ce groupe ne possède aucune zone enregistrée sur la carte.</p>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {guildTerritories.map((t, index) => (
                        <div 
                          key={t.id}
                          style={{
                            background: 'rgba(255, 255, 255, 0.01)',
                            border: '1px solid var(--border-color)',
                            borderRadius: '8px',
                            padding: '12px 16px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                          }}
                        >
                          <div>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-white)' }}>Zone #{index + 1}</p>
                            <p style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)', marginTop: '2px' }}>ID: {t.id.substring(0, 8)}...</p>
                          </div>

                          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                            <span style={{ fontWeight: 800, color: 'var(--primary-green)', fontSize: '0.95rem' }}>
                              {(t.superficie_m2 / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} km²
                            </span>
                            
                            {t.points && t.points.length > 0 && (
                              <button 
                                className="btn-icon" 
                                title="Localiser sur la carte"
                                onClick={() => centerTerritoryOnMap(t)}
                                style={{ color: 'var(--primary-green)' }}
                              >
                                <MapPin size={16} />
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                </div>
              )}

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
