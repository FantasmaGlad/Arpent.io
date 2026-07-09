'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Map, Users, Shield, LogOut, Grid, BarChart3, Rss } from 'lucide-react';
import { supabase } from '@/lib/supabase';

export default function Sidebar() {
  const pathname = usePathname();
  const [adminUser, setAdminUser] = useState<{ email?: string; nom_complet?: string; initials?: string }>({});

  useEffect(() => {
    supabase.auth.getUser().then(async ({ data: { user } }) => {
      if (user) {
        const { data: adminRecord } = await supabase
          .from('admins')
          .select('nom_complet')
          .eq('id', user.id)
          .maybeSingle();

        const email = user.email || '';
        const nom_complet = adminRecord?.nom_complet || user.user_metadata?.full_name || email.split('@')[0];
        const initials = nom_complet
          .split(' ')
          .map((n: string) => n[0])
          .join('')
          .toUpperCase()
          .slice(0, 2);

        setAdminUser({
          email,
          nom_complet,
          initials: initials || 'AD',
        });
      }
    });
  }, []);

  const handleSignOut = async () => {
    if (confirm('Voulez-vous vous déconnecter de la console admin ?')) {
      await supabase.auth.signOut();
    }
  };

  const navItems = [
    { name: 'Carte Globale', href: '/', icon: Map },
    { name: 'Statistiques analytiques', href: '/stats', icon: BarChart3 },
    { name: 'Profils Utilisateurs', href: '/profiles', icon: Users },
    { name: 'Groupes / Équipes', href: '/clans', icon: Grid },
    { name: 'Modération Feed', href: '/feed', icon: Rss },
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
          color: 'var(--primary-green)',
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
          color: 'var(--text-white)'
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
              <Icon size={20} style={{ color: isActive ? 'var(--primary-green)' : 'inherit' }} />
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
        background: '#FAFAFA'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: 'var(--primary-green-subtle)',
            border: '1px solid var(--primary-green)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '0.85rem',
            fontWeight: 800,
            color: 'var(--primary-green)'
          }}>
            {adminUser.initials || 'AD'}
          </div>
          <div style={{ overflow: 'hidden' }}>
            <p style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-white)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {adminUser.nom_complet || 'Admin'}
            </p>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {adminUser.email || 'admin@arpent.io'}
            </p>
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
          onMouseEnter={(e) => e.currentTarget.style.color = '#FF4B4B'}
          onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-muted)'}
        >
          <LogOut size={16} />
          <span>Déconnexion</span>
        </button>
      </div>
    </aside>
  );
}
