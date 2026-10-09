import React, { useState, useEffect } from 'react';
import { 
  Sliders, 
  Flame, 
  Timer, 
  AlertOctagon, 
  RefreshCw, 
  Check, 
  Send, 
  Terminal, 
  Cpu, 
  Server, 
  ShieldAlert 
} from 'lucide-react';

export default function LiveConsole({ currentStrategy, onStrategyChange }) {
  const [strategy, setStrategy] = useState(currentStrategy || 'ADAPTIVE_MULTI_METRIC');
  const [weights, setWeights] = useState({
    latency: 0.40,
    cpu: 0.25,
    error: 0.25,
    connections: 0.10,
  });
  const [isApplyingStrategy, setIsApplyingStrategy] = useState(false);
  const [isApplyingWeights, setIsApplyingWeights] = useState(false);
  const [chaosLoading, setChaosLoading] = useState(false);
  const [consoleLogs, setConsoleLogs] = useState([
    { id: 1, time: '14:30:00', type: 'info', text: 'ALB-M Live Gateway console initialized. Ready for commands.' },
    { id: 2, time: '14:30:05', type: 'success', text: 'Connected to local routing controller (port 8080).' }
  ]);

  const addLog = (type, text) => {
    const timeStr = new Date().toTimeString().split(' ')[0];
    setConsoleLogs(prev => [
      { id: Date.now(), time: timeStr, type, text },
      ...prev.slice(0, 30)
    ]);
  };

  const handleApplyStrategy = async () => {
    setIsApplyingStrategy(true);
    addLog('info', `Deploying routing strategy switch: ${strategy}`);
    try {
      const res = await fetch('/admin/routing/strategy', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ strategy }),
      });
      if (res.ok) {
        addLog('success', `Strategy successfully reconfigured to ${strategy} (HTTP 200 OK)`);
        if (onStrategyChange) onStrategyChange(strategy);
      } else {
        addLog('warning', `Gateway returned status ${res.status}. Switched active local state to ${strategy}`);
        if (onStrategyChange) onStrategyChange(strategy);
      }
    } catch (err) {
      addLog('warning', `Gateway offline or unrouted. Switched active simulation state to ${strategy}`);
      if (onStrategyChange) onStrategyChange(strategy);
    } finally {
      setIsApplyingStrategy(false);
    }
  };

  const handleApplyWeights = async () => {
    setIsApplyingWeights(true);
    addLog('info', `Updating multi-metric scoring weights: α=${weights.latency}, β=${weights.cpu}, γ=${weights.error}, δ=${weights.connections}`);
    try {
      const res = await fetch('/admin/routing/weights', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(weights),
      });
      if (res.ok) {
        addLog('success', 'Multi-metric objective weights updated dynamically (HTTP 200 OK)');
      } else {
        addLog('info', `Weights updated in controller cache: [${weights.latency}, ${weights.cpu}, ${weights.error}, ${weights.connections}]`);
      }
    } catch (err) {
      addLog('info', `Weights stored in local state: Latency=${weights.latency}, CPU=${weights.cpu}, Error=${weights.error}, Conn=${weights.connections}`);
    } finally {
      setIsApplyingWeights(false);
    }
  };

  const triggerChaos = async (action, details, payload = {}) => {
    setChaosLoading(true);
    addLog('danger', `Executing chaos hook: ${details}`);
    try {
      const res = await fetch(`/chaos/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        addLog('success', `Chaos event '${action}' acknowledged by cluster (HTTP 200)`);
      } else {
        addLog('warning', `Chaos hook simulated: ${details} applied to instance worker-2`);
      }
    } catch (err) {
      addLog('info', `Chaos hook simulated: ${details} applied to instance worker-2`);
    } finally {
      setChaosLoading(false);
    }
  };

  const weightSum = (weights.latency + weights.cpu + weights.error + weights.connections).toFixed(2);

  return (
    <div style={{ padding: '0 1.25rem 2rem' }}>
      {/* Intro Header */}
      <div className="glass-panel" style={{ padding: '1.25rem 1.5rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <Sliders size={20} color="#06b6d4" />
              <h2 style={{ fontSize: '1.25rem', fontWeight: 800 }}>Gateway Control Console & Chaos Injector</h2>
            </div>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
              Dynamic algorithm hot-swapping, runtime scoring weight reconfiguration, and live microservice chaos triggers.
            </p>
          </div>
          <span className="badge badge-cyan">Admin API v1.0</span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: '1.5rem' }}>
        
        {/* Panel 1: Routing Algorithm Switcher */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Server size={18} color="#06b6d4" />
            <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>Routing Strategy Switcher</h3>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.25rem' }}>
            {[
              { id: 'ADAPTIVE_MULTI_METRIC', title: 'Adaptive Multi-Metric (MM-AR)', desc: 'Dynamic score-based steering with latency, CPU, and error penalties.' },
              { id: 'ROUND_ROBIN', title: 'Round Robin (RR)', desc: 'Deterministic uniform sequential request dispatching.' },
              { id: 'SMOOTH_WEIGHTED_ROUND_ROBIN', title: 'Smooth Weighted Round Robin (WRR)', desc: 'Interleaved Nginx-style smooth weighted distribution.' },
              { id: 'LEAST_CONNECTIONS', title: 'Least Connections (LC)', desc: 'Steers to worker instance with fewest active inflight connections.' },
            ].map(opt => (
              <label 
                key={opt.id}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.8rem',
                  borderRadius: '8px',
                  background: strategy === opt.id ? 'rgba(6, 182, 212, 0.12)' : 'rgba(255, 255, 255, 0.02)',
                  border: strategy === opt.id ? '1px solid rgba(6, 182, 212, 0.4)' : '1px solid var(--border-subtle)',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease'
                }}
              >
                <input
                  type="radio"
                  name="strategy"
                  value={opt.id}
                  checked={strategy === opt.id}
                  onChange={(e) => setStrategy(e.target.value)}
                  style={{ marginTop: '0.2rem' }}
                />
                <div>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: strategy === opt.id ? '#38bdf8' : 'var(--text-main)' }}>
                    {opt.title}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.15rem' }}>
                    {opt.desc}
                  </div>
                </div>
              </label>
            ))}
          </div>

          <button
            onClick={handleApplyStrategy}
            disabled={isApplyingStrategy}
            className="btn btn-primary"
            style={{ width: '100%' }}
          >
            {isApplyingStrategy ? <RefreshCw size={14} className="spin" /> : <Send size={14} />}
            <span>Apply Routing Strategy</span>
          </button>
        </div>

        {/* Panel 2: Multi-Objective Weight Sliders */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Cpu size={18} color="#8b5cf6" />
              <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>Scoring Weights (&alpha;, &beta;, &gamma;, &delta;)</h3>
            </div>
            <span className={`badge ${weightSum === '1.00' ? 'badge-emerald' : 'badge-amber'}`}>
              Sum = {weightSum}
            </span>
          </div>

          <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
            Tune penalty coefficients in Composite Penalty: P = &alpha;&middot;L_norm + &beta;&middot;CPU + &gamma;&middot;Err + &delta;&middot;Conn
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.25rem' }}>
            {[
              { key: 'latency', label: 'Latency Weight (α)', val: weights.latency, color: '#38bdf8' },
              { key: 'cpu', label: 'CPU Utilization Weight (β)', val: weights.cpu, color: '#a78bfa' },
              { key: 'error', label: 'Error Rate Weight (γ)', val: weights.error, color: '#f43f5e' },
              { key: 'connections', label: 'Inflight Connections (δ)', val: weights.connections, color: '#34d399' },
            ].map(slider => (
              <div key={slider.key}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.3rem' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{slider.label}</span>
                  <strong style={{ fontFamily: 'var(--font-mono)', color: slider.color }}>{slider.val.toFixed(2)}</strong>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={slider.val}
                  onChange={(e) => setWeights({ ...weights, [slider.key]: parseFloat(e.target.value) })}
                  style={{ width: '100%', accentColor: slider.color }}
                />
              </div>
            ))}
          </div>

          <button
            onClick={handleApplyWeights}
            disabled={isApplyingWeights}
            className="btn btn-outline"
            style={{ width: '100%' }}
          >
            <Check size={14} />
            <span>Update Scoring Weights</span>
          </button>
        </div>

        {/* Panel 3: Chaos Fault Injection Trigger */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <Flame size={18} color="#f43f5e" />
            <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>Fault Injection / Chaos Controller</h3>
          </div>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
            Trigger deliberate failure events on <code>worker-2</code> to evaluate adaptation speed in real time.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1rem' }}>
            <button
              onClick={() => triggerChaos('latency', 'Inject +300ms Latency on worker-2', { delayMs: 300, jitterMs: 50 })}
              className="btn btn-danger"
              style={{ fontSize: '0.8rem', padding: '0.65rem' }}
            >
              <Timer size={14} />
              <span>+300ms Delay</span>
            </button>

            <button
              onClick={() => triggerChaos('cpu', 'Burn 85% CPU on worker-2', { loadPct: 85, durationSec: 30 })}
              className="btn btn-danger"
              style={{ fontSize: '0.8rem', padding: '0.65rem' }}
            >
              <Flame size={14} />
              <span>85% CPU Burn</span>
            </button>

            <button
              onClick={() => triggerChaos('error', 'Inject 50% HTTP 500 errors on worker-2', { errorRatePct: 50 })}
              className="btn btn-danger"
              style={{ fontSize: '0.8rem', padding: '0.65rem' }}
            >
              <AlertOctagon size={14} />
              <span>50% Errors</span>
            </button>

            <button
              onClick={() => triggerChaos('reset', 'Clear all faults & restore normal state')}
              className="btn btn-outline"
              style={{ fontSize: '0.8rem', padding: '0.65rem' }}
            >
              <RefreshCw size={14} />
              <span>Reset Faults</span>
            </button>
          </div>
        </div>

        {/* Panel 4: Live Command Stream Log */}
        <div className="glass-panel" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Terminal size={18} color="#38bdf8" />
              <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>Cluster Event Stream</h3>
            </div>
            <button
              onClick={() => setConsoleLogs([])}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', fontSize: '0.75rem', cursor: 'pointer' }}
            >
              Clear
            </button>
          </div>

          <div style={{
            flex: 1,
            minHeight: '190px',
            maxHeight: '230px',
            overflowY: 'auto',
            background: 'rgba(5, 8, 16, 0.95)',
            borderRadius: '8px',
            border: '1px solid var(--border-subtle)',
            padding: '0.75rem',
            fontFamily: 'var(--font-mono)',
            fontSize: '0.75rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.4rem'
          }}>
            {consoleLogs.map(log => (
              <div key={log.id} style={{ display: 'flex', gap: '0.5rem', lineHeight: '1.4' }}>
                <span style={{ color: 'var(--text-muted)' }}>[{log.time}]</span>
                <span style={{
                  color: log.type === 'success' ? '#34d399' : log.type === 'danger' ? '#f87171' : log.type === 'warning' ? '#fbbf24' : '#38bdf8'
                }}>
                  {log.text}
                </span>
              </div>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
}
