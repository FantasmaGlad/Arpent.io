'use client';

import React, { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabase';
import { Shield, Lock, User, Loader2 } from 'lucide-react';
import Sidebar from './Sidebar';

export default function AuthWrapper({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);

  useEffect(() => {
    // Check active session
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      if (session?.user) {
        const { data: adminRecord } = await supabase
          .from('admins')
          .select('id')
          .eq('id', session.user.id)
          .maybeSingle();

        if (adminRecord) {
          setSession(session);
        } else {
          await supabase.auth.signOut();
          setSession(null);
        }
      } else {
        setSession(null);
      }
      setLoading(false);
    });

    // Listen for auth changes
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_event, session) => {
      if (session?.user) {
        const { data: adminRecord } = await supabase
          .from('admins')
          .select('id')
          .eq('id', session.user.id)
          .maybeSingle();

        if (adminRecord) {
          setSession(session);
        } else {
          await supabase.auth.signOut();
          setSession(null);
        }
      } else {
        setSession(null);
      }
      setLoading(false);
    });

    return () => subscription.unsubscribe();
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    setLoginLoading(true);

    const { data, error } = await supabase.auth.signInWithPassword({
      email: email.trim().toLowerCase(),
      password,
    });

    if (error) {
      setAuthError("Identifiants incorrects. Veuillez réessayer.");
      setLoginLoading(false);
      return;
    }

    if (data.user) {
      const { data: adminRecord, error: adminError } = await supabase
        .from('admins')
        .select('id')
        .eq('id', data.user.id)
        .maybeSingle();

      if (adminError || !adminRecord) {
        setAuthError("Accès refusé. Cet utilisateur n'a pas les droits d'administration.");
        await supabase.auth.signOut();
      } else {
        setSession(data.session);
      }
    }
    setLoginLoading(false);
  };

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        gap: '16px',
        backgroundColor: 'var(--bg-dark)',
        fontFamily: 'var(--font-outfit)'
      }}>
        <Loader2 style={{ color: 'var(--primary-green)', animation: 'spin 1s linear infinite' }} size={40} />
        <p style={{ color: 'var(--text-muted)' }}>Initialisation de la console sécurisée...</p>
        <style jsx global>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  if (!session) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        padding: '20px',
        backgroundColor: 'var(--bg-dark)',
        position: 'relative'
      }}>
        <div style={{
          width: '100%',
          maxWidth: '420px',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px',
          backgroundColor: 'var(--card-bg)',
          border: '1px solid var(--border-color)',
          borderRadius: '12px',
          padding: '32px',
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)'
        }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{
              display: 'inline-flex',
              padding: '12px',
              borderRadius: '12px',
              backgroundColor: 'var(--primary-green-subtle)',
              color: 'var(--primary-green)',
              marginBottom: '16px'
            }}>
              <Shield size={32} />
            </div>
            <h1 style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--text-white)', letterSpacing: '0.5px' }}>Arpent.io</h1>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '4px' }}>Console d'Administration Réseau</p>
          </div>

          {authError && (
            <div style={{
              backgroundColor: 'rgba(255, 75, 75, 0.08)',
              border: '1px solid rgba(255, 75, 75, 0.2)',
              color: '#FF4B4B',
              padding: '12px',
              borderRadius: '8px',
              fontSize: '0.85rem',
              fontWeight: 500
            }}>
              {authError}
            </div>
          )}

          <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Email Admin</label>
              <div style={{ position: 'relative' }}>
                <User size={18} style={{ position: 'absolute', left: '14px', top: '14px', color: 'var(--text-muted)' }} />
                <input
                  type="email"
                  className="input-field"
                  placeholder="admin@arpent.io"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  style={{ paddingLeft: '44px' }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Mot de passe</label>
              <div style={{ position: 'relative' }}>
                <Lock size={18} style={{ position: 'absolute', left: '14px', top: '14px', color: 'var(--text-muted)' }} />
                <input
                  type="password"
                  className="input-field"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  style={{ paddingLeft: '44px' }}
                />
              </div>
            </div>

            <button type="submit" className="btn btn-primary" disabled={loginLoading} style={{ marginTop: '8px', width: '100%' }}>
              {loginLoading ? (
                <>
                  <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} />
                  <span>Connexion en cours...</span>
                </>
              ) : (
                'Se connecter'
              )}
            </button>
          </form>
        </div>
        <style jsx global>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  return (
    <div className="admin-layout">
      <Sidebar />
      <main className="main-content">
        {children}
      </main>
    </div>
  );
}
