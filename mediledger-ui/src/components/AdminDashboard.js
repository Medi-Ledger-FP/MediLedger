import React, { useState, useEffect } from 'react';
import api from '../services/api';
import './Dashboard.css';

function AdminDashboard() {
    const username = localStorage.getItem('username') || 'Administrator';

    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Audit log state
    const [auditLogs, setAuditLogs] = useState([]);
    const [auditLoading, setAuditLoading] = useState(false);
    const [auditFilter, setAuditFilter] = useState('ALL');
    const [auditMsg, setAuditMsg] = useState('');

    useEffect(() => {
        const token = localStorage.getItem('token');
        if (!token) { window.location.href = '/'; return; }

        fetch('http://localhost:8080/api/admin/stats', {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
            .then((data) => { setStats(data); setLoading(false); })
            .catch((err) => { setError(err.message); setLoading(false); });

        // Load audit logs
        loadAuditLogs();
    }, []);

    const loadAuditLogs = async () => {
        setAuditLoading(true);
        setAuditMsg('');
        try {
            const data = await api.getRecentAuditLogs(50);
            setAuditLogs(Array.isArray(data) ? data : []);
            if (Array.isArray(data) && data.length === 0) {
                setAuditMsg('No audit events recorded yet in this session.');
            }
        } catch (err) {
            setAuditMsg('Could not load audit logs: ' + err.message);
            setAuditLogs([]);
        } finally {
            setAuditLoading(false);
        }
    };

    const filteredLogs = auditFilter === 'ALL'
        ? auditLogs
        : auditLogs.filter(e => e.result === auditFilter || e.action === auditFilter);

    const handleLogout = () => {
        localStorage.clear();
        window.location.href = '/';
    };

    const formatTime = (iso) => {
        if (!iso) return '';
        try {
            const d = new Date(iso);
            const diff = Math.floor((Date.now() - d.getTime()) / 1000);
            if (diff < 60) return `${diff}s ago`;
            if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
            if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
            return d.toLocaleDateString();
        } catch { return iso; }
    };

    const typeLabel = (type) => {
        switch (type) {
            case 'CREATE_RECORD': return { icon: '📤', cls: 'success' };
            case 'VIEW_RECORD': return { icon: '🔍', cls: 'info' };
            case 'EMERGENCY_ACCESS': return { icon: '🚨', cls: 'warning' };
            case 'CONSENT_GRANT': return { icon: '✅', cls: 'success' };
            default: return { icon: '📋', cls: 'info' };
        }
    };

    return (
        <div className="dashboard">
            <nav className="navbar">
                <h2>⚙️ Admin Dashboard</h2>
                <div>
                    <span className="username">{username}</span>
                    <button onClick={handleLogout} className="btn-logout">Logout</button>
                </div>
            </nav>

            <div className="dashboard-content">
                {loading && <p style={{ textAlign: 'center', padding: '2rem' }}>Loading blockchain data…</p>}

                {error && (
                    <div className="card" style={{ borderLeft: '4px solid #ef4444' }}>
                        <p>⚠️ Failed to load stats: {error}</p>
                    </div>
                )}

                {stats && (
                    <>
                        {/* Statistics */}
                        <div className="stats-grid">
                            <div className="stat-card">
                                <div className="stat-number">{stats.totalRecords}</div>
                                <div className="stat-label">Medical Records</div>
                            </div>
                            <div className="stat-card">
                                <div className="stat-number">{stats.totalAuditEntries || auditLogs.length}</div>
                                <div className="stat-label">Audit Events (session)</div>
                            </div>
                            <div className="stat-card">
                                <div className="stat-number">{auditLogs.filter(e => e.result === 'DENIED').length}</div>
                                <div className="stat-label">Denied Accesses</div>
                            </div>
                            <div className="stat-card">
                                <div className="stat-number"
                                    style={{ color: stats.blockchainOnline ? '#22c55e' : '#ef4444' }}>
                                    {stats.blockchainOnline ? '●' : '○'}
                                </div>
                                <div className="stat-label">Blockchain Status</div>
                            </div>
                        </div>

                        {/* System Health */}
                        <div className="card">
                            <h3>💚 System Health</h3>
                            <div className="health-grid">
                                {Object.entries(stats.systemHealth || {}).map(([key, val]) => {
                                    const ok = val === 'ONLINE' || val === 'OPERATIONAL' || val === 'ACTIVE' || val === 'SYNCED';
                                    return (
                                        <div className="health-item" key={key}>
                                            <span className={`health-indicator ${ok ? 'green' : 'yellow'}`}></span>
                                            <strong>{key.charAt(0).toUpperCase() + key.slice(1)}:</strong>&nbsp;{val}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    </>
                )}

                {/* HIPAA Audit Log */}
                <div className="card">
                    <h3>📋 HIPAA Audit Log
                        <button onClick={loadAuditLogs} disabled={auditLoading}
                            style={{ marginLeft: '1rem', fontSize: '0.8rem', padding: '4px 10px', cursor: 'pointer', border: '1px solid #d1d5db', borderRadius: '4px', background: '#f9fafb' }}>
                            {auditLoading ? '⏳' : '↺ Refresh'}
                        </button>
                    </h3>

                    {/* Filter Bar */}
                    <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
                        {['ALL', 'SUCCESS', 'DENIED', 'CREATE_RECORD', 'VIEW_RECORD', 'EMERGENCY_ACCESS'].map(f => (
                            <button key={f} onClick={() => setAuditFilter(f)}
                                style={{
                                    padding: '4px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem',
                                    border: auditFilter === f ? '2px solid #667eea' : '1px solid #d1d5db',
                                    background: auditFilter === f ? '#ede9fe' : '#f9fafb',
                                    fontWeight: auditFilter === f ? 600 : 400
                                }}>
                                {f}
                            </button>
                        ))}
                    </div>

                    {auditMsg && <p style={{ color: '#9ca3af', fontSize: '0.85rem' }}>{auditMsg}</p>}

                    {filteredLogs.length === 0 && !auditMsg ? (
                        <p className="empty-state">No matching audit events.</p>
                    ) : (
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
                                <thead>
                                    <tr style={{ background: '#f3f4f6', textAlign: 'left' }}>
                                        <th style={{ padding: '8px 12px' }}>Time</th>
                                        <th style={{ padding: '8px 12px' }}>Action</th>
                                        <th style={{ padding: '8px 12px' }}>User</th>
                                        <th style={{ padding: '8px 12px' }}>Role</th>
                                        <th style={{ padding: '8px 12px' }}>Record ID</th>
                                        <th style={{ padding: '8px 12px' }}>Patient</th>
                                        <th style={{ padding: '8px 12px' }}>Result</th>
                                        <th style={{ padding: '8px 12px' }}>Reason</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredLogs.map((log, i) => {
                                        const { icon } = typeLabel(log.action);
                                        const isSuccess = log.result === 'SUCCESS';
                                        return (
                                            <tr key={i} style={{ borderBottom: '1px solid #e5e7eb', background: i % 2 === 0 ? '#fff' : '#fafafa' }}>
                                                <td style={{ padding: '8px 12px', color: '#6b7280' }}>{formatTime(log.timestamp)}</td>
                                                <td style={{ padding: '8px 12px' }}>{icon} {log.action}</td>
                                                <td style={{ padding: '8px 12px' }}>{log.userId}</td>
                                                <td style={{ padding: '8px 12px' }}>{log.userRole}</td>
                                                <td style={{ padding: '8px 12px', color: '#6b7280', fontFamily: 'monospace', fontSize: '0.75rem' }}>
                                                    {log.recordId && log.recordId !== 'UNKNOWN' ? log.recordId.substring(0, 12) + '…' : '—'}
                                                </td>
                                                <td style={{ padding: '8px 12px' }}>{log.patientId !== 'UNKNOWN' ? log.patientId : '—'}</td>
                                                <td style={{ padding: '8px 12px' }}>
                                                    <span style={{
                                                        padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                                                        background: isSuccess ? '#dcfce7' : '#fee2e2',
                                                        color: isSuccess ? '#16a34a' : '#dc2626'
                                                    }}>
                                                        {log.result}
                                                    </span>
                                                </td>
                                                <td style={{ padding: '8px 12px', color: '#6b7280', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                                    {log.reason || '—'}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                    <p style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '0.75rem' }}>
                        ℹ️ Showing last 50 events from blockchain (falling back to in-memory log when blockchain is unavailable).
                    </p>
                </div>
            </div>
        </div>
    );
}

export default AdminDashboard;
