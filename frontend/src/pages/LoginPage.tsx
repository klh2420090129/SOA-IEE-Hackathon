import React, { useState } from 'react';
import { Lock, LogIn, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const LoginPage: React.FC = () => {
  const { login, loading, error } = useAuth();
  const [username, setUsername] = useState('planner@soa8.org');
  const [selectedRole, setSelectedRole] = useState<'planner' | 'shipper'>('planner');

  const handleRoleSelect = (role: 'planner' | 'shipper') => {
    setSelectedRole(role);
    setUsername(role === 'planner' ? 'planner@soa8.org' : 'shipper@soa8.org');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login(username, selectedRole);
    } catch {
      // AuthContext sets error state which is rendered in the alert box
    }
  };

  return (
    <div style={{
      minHeight: '80vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px'
    }}>
      <div className="glass-panel" style={{ width: '100%', maxWidth: '440px', padding: '36px' }}>
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <div style={{
            width: '48px',
            height: '48px',
            borderRadius: '12px',
            background: 'linear-gradient(135deg, #3b82f6, #06b6d4)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 12px'
          }}>
            <Lock size={24} color="#fff" />
          </div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 6px 0' }}>
            Freight Platform Authentication
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', margin: 0 }}>
            Sign in with verified role credentials to access hazardous cargo load planning.
          </p>
        </div>

        {error && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid var(--accent-danger)',
            borderRadius: '8px',
            padding: '12px',
            color: '#f87171',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '0.85rem',
            marginBottom: '20px'
          }}>
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
              Select Operational Role Persona
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <button
                type="button"
                onClick={() => handleRoleSelect('planner')}
                style={{
                  padding: '10px',
                  borderRadius: '6px',
                  background: selectedRole === 'planner' ? 'rgba(59, 130, 246, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                  color: selectedRole === 'planner' ? '#60a5fa' : 'var(--text-muted)',
                  border: selectedRole === 'planner' ? '1px solid #3b82f6' : '1px solid var(--border-color)',
                  fontWeight: 600,
                  fontSize: '0.85rem'
                }}
              >
                Load Planner
              </button>
              <button
                type="button"
                onClick={() => handleRoleSelect('shipper')}
                style={{
                  padding: '10px',
                  borderRadius: '6px',
                  background: selectedRole === 'shipper' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                  color: selectedRole === 'shipper' ? '#34d399' : 'var(--text-muted)',
                  border: selectedRole === 'shipper' ? '1px solid #10b981' : '1px solid var(--border-color)',
                  fontWeight: 600,
                  fontSize: '0.85rem'
                }}
              >
                Shipper
              </button>
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
              Username
            </label>
            <input
              type="text"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              padding: '12px',
              background: 'var(--accent-primary)',
              color: '#fff',
              borderRadius: '6px',
              fontWeight: 600,
              fontSize: '0.95rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              marginTop: '6px'
            }}
          >
            <LogIn size={16} />
            {loading ? 'Authenticating with Gateway...' : 'Sign In to Freight System'}
          </button>
        </form>
      </div>
    </div>
  );
};
