import React from 'react';
import { 
  Truck, 
  LogOut, 
  UserCheck, 
  Layers, 
  FileText, 
  Sliders
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

interface NavigationHeaderProps {
  activeTab: 'shipper' | 'planner' | 'rules' | 'audit';
  onSelectTab: (tab: 'shipper' | 'planner' | 'rules' | 'audit') => void;
}

export const NavigationHeader: React.FC<NavigationHeaderProps> = ({ activeTab, onSelectTab }) => {
  const { user, logout, hasRole, login } = useAuth();

  return (
    <header style={{
      background: 'rgba(15, 23, 42, 0.95)',
      borderBottom: '1px solid var(--border-color)',
      padding: '12px 24px',
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      backdropFilter: 'blur(10px)',
      position: 'sticky',
      top: 0,
      zIndex: 50
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            background: 'linear-gradient(135deg, #3b82f6, #06b6d4)',
            padding: '8px',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Truck size={20} color="#fff" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, letterSpacing: '-0.01em' }}>
              SOA Team 8 &bull; Hazardous Cargo Freight Platform
            </h1>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              3D Co-Loading, Zero-Trust Validation & IMDG Segregation Engine
            </span>
          </div>
        </div>

        {/* Primary Role-Aware Navigation Tabs */}
        {user && (
          <nav style={{ display: 'flex', gap: '8px', marginLeft: '24px' }}>
            <button
              onClick={() => onSelectTab('shipper')}
              style={{
                background: activeTab === 'shipper' ? 'var(--bg-secondary)' : 'transparent',
                color: activeTab === 'shipper' ? 'var(--text-main)' : 'var(--text-muted)',
                padding: '6px 14px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                border: activeTab === 'shipper' ? '1px solid var(--border-color)' : '1px solid transparent'
              }}
            >
              <FileText size={15} /> Shipper Declarations
            </button>

            <button
              onClick={() => onSelectTab('planner')}
              style={{
                background: activeTab === 'planner' ? 'var(--bg-secondary)' : 'transparent',
                color: activeTab === 'planner' ? 'var(--text-main)' : 'var(--text-muted)',
                padding: '6px 14px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                border: activeTab === 'planner' ? '1px solid var(--border-color)' : '1px solid transparent'
              }}
            >
              <Layers size={15} /> Load Planner Console
              {hasRole('ROLE_LOAD_PLANNER') && (
                <span style={{
                  fontSize: '0.65rem',
                  padding: '1px 5px',
                  borderRadius: '4px',
                  background: 'rgba(59, 130, 246, 0.2)',
                  color: '#60a5fa'
                }}>
                  DECISION AUTH
                </span>
              )}
            </button>

            <button
              onClick={() => onSelectTab('rules')}
              style={{
                background: activeTab === 'rules' ? 'var(--bg-secondary)' : 'transparent',
                color: activeTab === 'rules' ? 'var(--text-main)' : 'var(--text-muted)',
                padding: '6px 14px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                border: activeTab === 'rules' ? '1px solid var(--border-color)' : '1px solid transparent'
              }}
            >
              <Sliders size={15} /> Safety Rules & Snapshots
            </button>
          </nav>
        )}
      </div>

      {/* User Session & Role Controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        {user ? (
          <>
            <div style={{ textAlign: 'right', fontSize: '0.8rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'flex-end' }}>
                <UserCheck size={14} style={{ color: 'var(--accent-secondary)' }} />
                <span style={{ fontWeight: 600 }}>{user.username}</span>
              </div>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
                {user.roles.join(', ')}
              </div>
            </div>

            {/* Quick Demo Role Switcher */}
            <div style={{ display: 'flex', gap: '6px' }}>
              <button
                onClick={() => login('shipper@soa8.org', 'shipper')}
                title="Switch to Shipper User"
                style={{
                  padding: '4px 8px',
                  fontSize: '0.75rem',
                  borderRadius: '4px',
                  background: hasRole('ROLE_SHIPPER') ? 'rgba(16, 185, 129, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                  color: hasRole('ROLE_SHIPPER') ? '#34d399' : 'var(--text-muted)',
                  border: '1px solid var(--border-color)'
                }}
              >
                Shipper
              </button>
              <button
                onClick={() => login('planner@soa8.org', 'planner')}
                title="Switch to Load Planner User"
                style={{
                  padding: '4px 8px',
                  fontSize: '0.75rem',
                  borderRadius: '4px',
                  background: hasRole('ROLE_LOAD_PLANNER') ? 'rgba(59, 130, 246, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                  color: hasRole('ROLE_LOAD_PLANNER') ? '#60a5fa' : 'var(--text-muted)',
                  border: '1px solid var(--border-color)'
                }}
              >
                Planner
              </button>
            </div>

            <button
              onClick={logout}
              title="Logout"
              style={{
                padding: '6px',
                borderRadius: '6px',
                background: 'rgba(239, 68, 68, 0.1)',
                color: '#f87171',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <LogOut size={16} />
            </button>
          </>
        ) : (
          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Not Authenticated
          </div>
        )}
      </div>
    </header>
  );
};
