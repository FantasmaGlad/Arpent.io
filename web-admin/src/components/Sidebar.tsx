'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Map, Users, Shield, LogOut, Swords } from 'lucide-react';
import { supabase } from '@/lib/supabase';

export default function Sidebar() {
  const pathname = usePathname();

  const handleSignOut = async () => {
    if (confirm('Voulez-vous vous déconnecter de la console admin ?')) {
      await supabase.auth.signOut();
    }
  };

  const navItems = [
    { name: 'Carte Globale', href: '/', icon: Map },
    { name: 'Profils Joueurs', href: '/profiles', icon: Users },
    { name: 'Clans / Guildes', href: '/clans', icon: Swords },
  ];

  return (
    <aside className="sidebar">
      {/* Brand Header */}
      <div style={{
        padding: '28px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        borderBottom: '1px solid var(--border-color)'
      }} className="sidebar-logo">
        <div style={{
          color: 'var(--neon-volt)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <Shield size={24} />
        </div>
        <span style={{
          fontSize: '1.25rem',
          fontWeight: 800,
          textTransform: 'uppercase',
          letterSpacing: '1px',
          background: 'linear-gradient(90deg, #FFFFFF 0%, #B8C6DB 100%)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent'
        }}>
          Arpent.io
        </span>
      </div>

      {/* Nav List */}
      <nav style={{ flexGrow: 1, paddingTop: '24px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.name}
              href={item.href}
              className={`nav-link ${isActive ? 'active' : ''}`}
            >
              <Icon size={20} style={{ color: isActive ? 'var(--electric-blue)' : 'inherit' }} />
              <span>{item.name}</span>
            </Link>
          );
        })}
      </nav>

      {/* Admin Account & Logout footer */}
      <div style={{
        padding: '20px 24px',
        borderTop: '1px solid var(--border-color)',
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        background: 'rgba(15, 19, 24, 0.4)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: 'rgba(204, 255, 0, 0.1)',
            border: '1px solid var(--neon-volt)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '0.85rem',
            fontWeight: 800,
            color: 'var(--neon-volt)'
          }}>
            AD
          </div>
          <div style={{ overflow: 'hidden' }}>
            <p style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-white)' }}>Clément B.</p>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>clement.barillot3901@gmail.com</p>
          </div>
        </div>

        <button
          onClick={handleSignOut}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            background: 'none',
            border: 'none',
            color: 'var(--text-muted)',
            fontSize: '0.85rem',
            fontWeight: 500,
            cursor: 'pointer',
            padding: '4px 0',
            transition: 'color 0.2s'
          }}
          onMouseEnter={(e) => e.currentTarget.style.color = 'var(--active-orange)'}
          onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-muted)'}
        >
          <LogOut size={16} />
          <span>Déconnexion</span>
        </button>
      </div>
    </aside>
  );
}
