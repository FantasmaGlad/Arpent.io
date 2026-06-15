'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { 
  Search, 
  Trash2, 
  Edit2, 
  Swords, 
  Users,
  Calendar,
  X,
  User,
  Save,
  Image as ImageIcon,
  MapPin
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
}

interface Territory {
  id: string;
  guilde_id: string | null;
  superficie_m2: number;
}

export default function ClansPage() {
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  // Selected clan details modal
  const [selectedGuild, setSelectedGuild] = useState<Guild | null>(null);
  const [guildMembers, setGuildMembers] = useState<Profile[]>([]);
  const [guildArea, setGuildArea] = useState(0);

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
        supabase.from('profiles').select('id, pseudonyme, tag, guilde_id, grade'),
        supabase.from('territoires').select('id, guilde_id, superficie_m2')
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

  // Calculate clan specific details when a guild is selected
  useEffect(() => {
    if (!selectedGuild) return;

    // Filter members
    const members = profiles.filter(p => p.guilde_id === selectedGuild.id);
    setGuildMembers(members);

    // Sum area of territories owned by this clan
    const area = territories
      .filter(t => t.guilde_id === selectedGuild.id)
      .reduce((acc, curr) => acc + curr.superficie_m2, 0);
    setGuildArea(area);

    setIsEditing(false);
    setNewNom(selectedGuild.nom);
    setNewCouleur(selectedGuild.couleur_hex);
    setMessage(null);
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

      setMessage({ type: 'success', text: 'Clan mis à jour avec succès.' });

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
    if (!selectedGuild || !confirm("Voulez-vous supprimer l'emblème de ce clan ?")) return;
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
    if (!confirm("Voulez-vous vraiment supprimer ce clan ?\n\nTous ses membres deviendront autonomes, et les territoires conquis par ce clan ne lui seront plus rattachés.\n\nCette action est irréversible. Continuer ?")) {
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

      alert('Clan supprimé avec succès.');
      setSelectedGuild(null);
      setGuilds(prev => prev.filter(g => g.id !== guildId));
      
      // Update local profiles association
      setProfiles(prev => prev.map(p => p.guilde_id === guildId ? { ...p, guilde_id: null } : p));
      // Update local territories association
      setTerritories(prev => prev.map(t => t.guilde_id === guildId ? { ...t, guilde_id: null } : t));
    } catch (err: any) {
      alert(`Erreur : ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  // Filter guilds
  const filteredGuilds = guilds.filter(g => {
    const name = g.nom.toLowerCase();
    const tag = (g.tag || '').toLowerCase();
    const search = searchTerm.toLowerCase();
    return name.includes(search) || tag.includes(search);
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="cyber-bg" />

      {/* Header */}
      <div>
        <h1 className="title-cyber" style={{ fontSize: '2rem' }}>Gestion des Clans</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>Modération des empires et guildes militaires d'Arpent.io</p>
      </div>

      {/* Search Bar */}
      <div className="glass-card" style={{ padding: '16px 24px', display: 'flex', alignItems: 'center', gap: '16px' }}>
        <Search size={20} style={{ color: 'var(--text-muted)' }} />
        <input 
          type="text" 
          placeholder="Rechercher par nom ou tag (#)..." 
          className="input-field"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ border: 'none', background: 'transparent', padding: '0', fontSize: '1rem' }}
        />
      </div>

      {/* Clans Grid */}
      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Chargement des guildes territoriales...
        </div>
      ) : filteredGuilds.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Aucun clan ne correspond à votre recherche.
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
                  borderLeft: `4px solid ${g.couleur_hex}`,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '16px'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    {g.avatar_url ? (
                      <img src={g.avatar_url} alt="" className="avatar" />
                    ) : (
                      <div className="avatar avatar-placeholder" style={{ color: g.couleur_hex }}>
                        {g.nom.substring(0, 2).toUpperCase()}
                      </div>
                    )}
                    <div>
                      <h3 style={{ fontWeight: 700, fontSize: '1.15rem', color: 'var(--text-white)' }}>{g.nom}</h3>
                      <p style={{ fontSize: '0.8rem', color: 'var(--electric-blue)', fontFamily: 'monospace', fontWeight: 600, marginTop: '2px' }}>
                        {g.tag || ''}
                      </p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                        <User size={12} /> Chef : {leader?.pseudonyme || 'Inconnu'}
                      </p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button className="btn-icon" onClick={() => setSelectedGuild(g)} title="Inspecter le clan">
                      <Swords size={14} style={{ color: 'var(--electric-blue)' }} />
                    </button>
                    <button className="btn-icon" onClick={() => handleDeleteGuild(g.id)} title="Supprimer le clan">
                      <Trash2 size={14} style={{ color: 'var(--active-orange)' }} />
                    </button>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', background: 'rgba(15, 19, 24, 0.3)', padding: '12px', borderRadius: '8px' }}>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Membres</span>
                    <p style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--text-white)', marginTop: '2px' }}>
                      <Users size={14} style={{ marginRight: '6px', color: 'var(--electric-blue)' }} /> {membersCount}
                    </p>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Empire</span>
                    <p style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--neon-volt)', marginTop: '2px' }}>
                      {(area / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Inspector Modal */}
      {selectedGuild && (
        <div className="modal-overlay">
          <div className="modal-content glass-card" style={{ border: `1px solid ${selectedGuild.couleur_hex}` }}>
            {/* Modal Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                {selectedGuild.avatar_url ? (
                  <img src={selectedGuild.avatar_url} alt="" className="avatar avatar-large" />
                ) : (
                  <div className="avatar avatar-large avatar-placeholder" style={{ color: selectedGuild.couleur_hex, fontSize: '1.8rem' }}>
                    {selectedGuild.nom.substring(0, 2).toUpperCase()}
                  </div>
                )}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {isEditing ? (
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <input 
                          type="text" 
                          className="input-field" 
                          value={newNom}
                          onChange={(e) => setNewNom(e.target.value)}
                          style={{ padding: '6px 12px', fontSize: '1.25rem', width: '200px' }}
                        />
                        <input 
                          type="color" 
                          value={newCouleur}
                          onChange={(e) => setNewCouleur(e.target.value)}
                          style={{ width: '40px', height: '40px', border: 'none', background: 'transparent', cursor: 'pointer' }}
                        />
                      </div>
                    ) : (
                      <h2 style={{ fontSize: '1.5rem', fontWeight: 800 }}>{selectedGuild.nom}</h2>
                    )}

                    {isEditing ? (
                      <button className="btn-icon" onClick={handleUpdateGuild} disabled={actionLoading}>
                        <Save size={16} style={{ color: 'var(--neon-volt)' }} />
                      </button>
                    ) : (
                      <button className="btn-icon" onClick={() => setIsEditing(true)}>
                        <Edit2 size={14} style={{ color: 'var(--text-muted)' }} />
                      </button>
                    )}
                  </div>
                  <p style={{ color: 'var(--electric-blue)', fontSize: '0.95rem', marginTop: '4px', fontFamily: 'monospace', fontWeight: 700 }}>
                    {selectedGuild.tag || 'Aucun tag'}
                  </p>
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: '2px', fontFamily: 'monospace' }}>
                    ID: {selectedGuild.id}
                  </p>
                </div>
              </div>
              <button className="btn-icon" onClick={() => setSelectedGuild(null)}>
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

            {/* Guild Actions Panel */}
            <div style={{ display: 'flex', gap: '12px', marginBottom: '24px' }}>
              {selectedGuild.avatar_url && (
                <button className="btn btn-secondary" onClick={handleRemoveAvatar} disabled={actionLoading} style={{ padding: '8px 16px', fontSize: '0.85rem' }}>
                  <ImageIcon size={14} /> Supprimer l'emblème
                </button>
              )}
              <button className="btn btn-danger" onClick={() => handleDeleteGuild(selectedGuild.id)} disabled={actionLoading} style={{ padding: '8px 16px', fontSize: '0.85rem', marginLeft: 'auto' }}>
                <Trash2 size={14} /> Dissoudre le clan
              </button>
            </div>

            {/* Stats Summary */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '32px' }}>
              <div style={{ background: 'rgba(15, 19, 24, 0.4)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', textTransform: 'uppercase', fontWeight: 600 }}>Territoire Contrôlé</span>
                <p style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--neon-volt)', marginTop: '4px' }}>
                  {(guildArea / 1000000).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} km²
                </p>
              </div>
              <div style={{ background: 'rgba(15, 19, 24, 0.4)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border-color)' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', textTransform: 'uppercase', fontWeight: 600 }}>Date de création</span>
                <p style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-white)', marginTop: '4px' }}>
                  {new Date(selectedGuild.date_creation).toLocaleDateString('fr-FR')}
                </p>
              </div>
            </div>

            {/* Member List */}
            <div>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Users size={18} style={{ color: 'var(--electric-blue)' }} /> Liste des Membres Actifs ({guildMembers.length})
              </h3>

              {guildMembers.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', padding: '16px', border: '1px dashed var(--border-color)', borderRadius: '8px', textAlign: 'center' }}>
                  Cette guilde ne contient aucune recrue pour le moment.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '250px', overflowY: 'auto', paddingRight: '8px' }}>
                  {guildMembers.map((m) => (
                    <div 
                      key={m.id}
                      style={{
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid var(--border-color)',
                        borderRadius: '8px',
                        padding: '12px 16px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                      }}
                    >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <div style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          backgroundColor: selectedGuild.couleur_hex
                        }} />
                        <span style={{ fontWeight: 600, fontSize: '0.95rem' }}>{m.pseudonyme || 'Recrue'}</span>
                        {m.grade === 'chef' ? (
                          <span style={{ fontSize: '0.7rem', padding: '1px 8px', borderRadius: '12px', backgroundColor: 'rgba(255, 215, 0, 0.15)', color: '#FFD700', border: '1px solid #FFD700', fontWeight: 700 }}>👑 Chef</span>
                        ) : m.grade === 'adjoint' ? (
                          <span style={{ fontSize: '0.7rem', padding: '1px 8px', borderRadius: '12px', backgroundColor: 'rgba(192, 192, 192, 0.15)', color: '#C0C0C0', border: '1px solid #C0C0C0', fontWeight: 700 }}>⚔️ Adjoint</span>
                        ) : (
                          <span style={{ fontSize: '0.7rem', padding: '1px 8px', borderRadius: '12px', backgroundColor: 'rgba(255,255,255,0.05)', color: 'var(--text-muted)', fontWeight: 600 }}>Membre</span>
                        )}
                      </div>
                    </div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--electric-blue)', fontFamily: 'monospace', fontWeight: 600 }}>
                      {m.tag || `ID: ${m.id.substring(0, 8)}...`}
                    </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
