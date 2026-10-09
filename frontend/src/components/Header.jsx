import React from 'react';
import { 
  Activity, 
  BarChart3, 
  LineChart, 
  FlaskConical, 
  SlidersHorizontal, 
  FileText, 
  Server, 
  CheckCircle2, 
  AlertCircle 
} from 'lucide-react';

export default function Header({ activeTab, setActiveTab, gatewayStatus, activeStrategy }) {
  const tabs = [
    { id: 'explorer', label: 'Benchmark Explorer', icon: BarChart3 },
    { id: 'visuals', label: 'Scientific Figures', icon: LineChart },
    { id: 'hypotheses', label: 'Hypothesis Tests (H1-H4)', icon: FlaskConical },
    { id: 'console', label: 'Live Gateway & Chaos', icon: SlidersHorizontal },
    { id: 'paper', label: 'Research Paper Draft', icon: FileText },
  ];

  const getStrategyBadge = (strat) => {
    switch (strat) {
      case 'ADAPTIVE_MULTI_METRIC':
        return 'badge-cyan';
      case 'ROUND_ROBIN':
        return 'badge-violet';
      case 'LEAST_CONNECTIONS':
        return 'badge-emerald';
      default:
        return 'badge-amber';
    }
  };

  return (
    <header className="glass-panel" style={{ margin: '1.25rem', padding: '1.1rem 1.75rem', position: 'sticky', top: '1.25rem', zIndex: 100 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        {/* Logo & Title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ 
            width: '44px', 
            height: '44px', 
            borderRadius: '12px', 
            background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.25), rgba(139, 92, 246, 0.35))',
            border: '1px solid rgba(6, 182, 212, 0.4)',
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            boxShadow: '0 0 16px rgba(6, 182, 212, 0.2)'
          }}>
            <Activity size={24} color="#06b6d4" />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <h1 style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em', background: 'linear-gradient(90deg, #f8fafc, #38bdf8)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                ALB-M Research Hub
              </h1>
              <span className="badge badge-cyan" style={{ fontSize: '0.7rem' }}>Sprint 8 Active</span>
            </div>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
              Adaptive Load Balancer for Microservices &bull; 140-Run Empirical Testbed
            </p>
          </div>
        </div>

        {/* Live Cluster Pill */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div style={{ 
            background: 'rgba(10, 15, 28, 0.7)', 
            border: '1px solid var(--border-subtle)', 
            borderRadius: '10px', 
            padding: '0.45rem 0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            fontSize: '0.8rem'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <div className="pulse-dot" style={{ backgroundColor: gatewayStatus === 'ONLINE' ? '#10b981' : '#f59e0b' }} />
              <span style={{ color: 'var(--text-secondary)' }}>Gateway:</span>
              <strong style={{ color: gatewayStatus === 'ONLINE' ? '#34d399' : '#fbbf24' }}>
                {gatewayStatus}
              </strong>
            </div>

            <div style={{ width: '1px', height: '14px', background: 'var(--border-subtle)' }} />

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <span style={{ color: 'var(--text-secondary)' }}>Active Strategy:</span>
              <span className={`badge ${getStrategyBadge(activeStrategy)}`}>
                {activeStrategy || 'ADAPTIVE_MULTI_METRIC'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <nav style={{ display: 'flex', gap: '0.5rem', marginTop: '1.2rem', borderTop: '1px solid var(--border-subtle)', paddingTop: '0.85rem', overflowX: 'auto' }}>
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`nav-tab ${isActive ? 'active' : ''}`}
            >
              <Icon size={16} color={isActive ? '#38bdf8' : 'currentColor'} />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </nav>
    </header>
  );
}
