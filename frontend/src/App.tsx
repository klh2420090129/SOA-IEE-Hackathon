import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NavigationHeader } from './components/common/NavigationHeader';
import { LoginPage } from './pages/LoginPage';
import { ShipperPortalPage } from './pages/ShipperPortalPage';
import { PlannerReviewPage } from './pages/PlannerReviewPage';
import { RulesAndAuditPage } from './pages/RulesAndAuditPage';
import { Loader2 } from 'lucide-react';

const MainShell: React.FC = () => {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState<'shipper' | 'planner' | 'rules' | 'audit'>('planner');

  // Synchronize initial active tab with user's primary role
  React.useEffect(() => {
    if (user && user.roles) {
      if (user.roles.includes('ROLE_LOAD_PLANNER')) {
        setActiveTab('planner');
      } else if (user.roles.includes('ROLE_SHIPPER')) {
        setActiveTab('shipper');
      }
    }
  }, [user]);

  if (loading) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        gap: '12px',
        color: 'var(--text-muted)'
      }}>
        <Loader2 size={32} className="spin" style={{ color: 'var(--accent-primary)' }} />
        <span style={{ fontSize: '0.9rem' }}>Connecting to API Gateway & Discovery Service...</span>
      </div>
    );
  }

  if (!user) {
    return <LoginPage />;
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <NavigationHeader activeTab={activeTab} onSelectTab={setActiveTab} />
      <main style={{ flex: 1, padding: '28px 24px' }}>
        {activeTab === 'shipper' && <ShipperPortalPage />}
        {activeTab === 'planner' && <PlannerReviewPage />}
        {(activeTab === 'rules' || activeTab === 'audit') && <RulesAndAuditPage />}
      </main>
    </div>
  );
};

export function App() {
  return (
    <AuthProvider>
      <MainShell />
    </AuthProvider>
  );
}

export default App;
