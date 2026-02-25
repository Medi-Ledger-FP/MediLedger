import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './Dashboard.css';

function AdminDashboard() {
    const navigate = useNavigate();
    const username = localStorage.getItem('username') || 'Administrator';

    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const token = localStorage.getItem('token');
        if (!token) {
            navigate('/login');
            return;
        }

        fetch('http://localhost:8080/api/admin/stats', {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((res) => {
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                return res.json();
            })
            .then((data) => {
                setStats(data);
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    }, [navigate]);

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
        } catch {
            return iso;
        }
    };

    const typeLabel = (type) => {
        switch (type) {
            case 'RECORD_UPLOAD': return { icon: '✅', cls: 'success', text: 'RECORD_UPLOAD' };
            case 'RECORD_ACCESS': return { icon: '🔍', cls: 'info', text: 'RECORD_ACCESS' };
            default: return { icon: '⚠️', cls: 'warning', text: type };
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
                                <div className="stat-number">{stats.totalAuditEntries}</div>
                                <div className="stat-label">Audit Entries (30d)</div>
                            </div>
                            <div className="stat-card">
                                <div className="stat-number">
                                    {stats.recentActivity ? stats.recentActivity.length : 0}
                                </div>
                                <div className="stat-label">Activity (24h)</div>
                            </div>
                            <div className="stat-card">
                                <div className="stat-number"
                                    style={{ color: stats.blockchainOnline ? '#22c55e' : '#ef4444' }}>
                                    {stats.blockchainOnline ? '●' : '○'}
                                </div>
                                <div className="stat-label">Blockchain Status</div>
                            </div>
                        </div>

                        {/* Recent Activity */}
                        <div className="card">
                            <h3>📊 Recent System Activity (24h)</h3>
                            <div className="activity-list">
                                {stats.recentActivity && stats.recentActivity.length > 0
                                    ? stats.recentActivity.map((item, i) => {
                                        const { icon, cls, text } = typeLabel(item.type);
                                        return (
                                            <div className="activity-item" key={i}>
                                                <span className={`activity-type ${cls}`}>
                                                    {icon} {text}
                                                </span>
                                                <span>
                                                    {item.user}
                                                    {item.recordId && item.recordId !== 'UNKNOWN'
                                                        ? ` — record ${item.recordId.substring(0, 8)}…`
                                                        : ''}
                                                    {' '}
                                                    <span style={{
                                                        color: item.result === 'SUCCESS' ? '#22c55e' : '#ef4444',
                                                        fontWeight: 600
                                                    }}>
                                                        {item.result}
                                                    </span>
                                                </span>
                                                <span className="activity-time">
                                                    {formatTime(item.timestamp)}
                                                </span>
                                            </div>
                                        );
                                    })
                                    : <p style={{ color: '#9ca3af', padding: '1rem 0' }}>
                                        No activity in the last 24 hours.
                                    </p>
                                }
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
            </div>
        </div>
    );
}

export default AdminDashboard;
