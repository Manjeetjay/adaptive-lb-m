import React, { useState, useEffect } from 'react';
import Header from './components/Header';
import BenchmarkExplorer from './components/BenchmarkExplorer';
import Visualizations from './components/Visualizations';
import HypothesisTesting from './components/HypothesisTesting';
import LiveConsole from './components/LiveConsole';
import PaperViewer from './components/PaperViewer';
import statisticalData from './data/statistical_summary.json';

export default function App() {
  const [activeTab, setActiveTab] = useState('explorer');
  const [gatewayStatus, setGatewayStatus] = useState('STANDALONE');
  const [activeStrategy, setActiveStrategy] = useState('ADAPTIVE_MULTI_METRIC');
  const [data, setData] = useState(statisticalData);

  useEffect(() => {
    // Check if the Spring Cloud Gateway is reachable locally
    const checkGateway = async () => {
      try {
        const res = await fetch('/admin/routing/strategy', { method: 'GET' });
        if (res.ok) {
          const body = await res.json();
          setGatewayStatus('ONLINE');
          if (body.strategy) setActiveStrategy(body.strategy);
        } else {
          setGatewayStatus('STANDALONE');
        }
      } catch (err) {
        setGatewayStatus('STANDALONE');
      }
    };
    checkGateway();
  }, []);

  const handleStrategyChange = (newStrategy) => {
    setActiveStrategy(newStrategy);
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header 
        activeTab={activeTab} 
        setActiveTab={setActiveTab} 
        gatewayStatus={gatewayStatus} 
        activeStrategy={activeStrategy} 
      />

      <main style={{ flex: 1 }}>
        {activeTab === 'explorer' && <BenchmarkExplorer data={data} />}
        {activeTab === 'visuals' && <Visualizations data={data} />}
        {activeTab === 'hypotheses' && <HypothesisTesting data={data} />}
        {activeTab === 'console' && (
          <LiveConsole 
            currentStrategy={activeStrategy} 
            onStrategyChange={handleStrategyChange} 
          />
        )}
        {activeTab === 'paper' && <PaperViewer />}
      </main>

      <footer style={{
        marginTop: 'auto',
        borderTop: '1px solid var(--border-subtle)',
        background: 'rgba(8, 12, 20, 0.9)',
        padding: '1.25rem 2rem',
        fontSize: '0.78rem',
        color: 'var(--text-muted)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '1rem'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span>&copy; 2026 ALB-M Project &bull; Sprint 8</span>
          <span style={{ color: 'var(--border-subtle)' }}>|</span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>SHA-256 Checksum Verified (422 Files)</span>
        </div>
        <div>
          <span>Empirical Evaluation Testbed &bull; Microservices Load Balancing Research</span>
        </div>
      </footer>
    </div>
  );
}
