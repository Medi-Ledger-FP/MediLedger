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

    // Registration state
    const [regUsername, setRegUsername] = useState('');
    const [regPassword, setRegPassword] = useState('');
    const [regRole, setRegRole] = useState('DOCTOR');
    const [regMsg, setRegMsg] = useState('');
    const [regLoading, setRegLoading] = useState(false);

    // Emergency approval state
    const [emgRequests, setEmgRequests] = useState([]);
    const [emgLoading, setEmgLoading] = useState(false);
    const [emgMsg, setEmgMsg] = useState('');

    useEffect(() => {
        const token = localStorage.getItem('token');
        if (!token) { window.location.href = '/'; return; }

        fetch('http://localhost:8080/api/admin/stats', {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
            .then((data) => { setStats(data); setLoading(false); })
            .catch((err) => { setError(err.message); setLoading(false); });

        // Load audit logs and emergency requests
        loadAuditLogs();
        loadEmergencyRequests();
    }, []);

    const loadEmergencyRequests = async () => {
        setEmgLoading(true);
        try {
            const data = await api.getAllEmergencyRequests();
            setEmgRequests(Array.isArray(data) ? data : []);
        } catch (err) {
            setEmgMsg('Failed to load emergency requests: ' + err.message);
        } finally {
            setEmgLoading(false);
        }
    };

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

    const handleRegisterStaff = async (e) => {
        e.preventDefault();
        if (!regUsername || !regPassword) { setRegMsg('❌ All fields required'); return; }
        setRegLoading(true);
        setRegMsg('');
        try {
            const res = await api.adminRegister(regUsername, regPassword, regRole);
            setRegMsg(`✅ Successfully registered ${regRole}: ${regUsername}`);
            setRegUsername('');
            setRegPassword('');
        } catch (err) {
            setRegMsg('❌ Registration failed: ' + err.message);
        } finally {
            setRegLoading(false);
        }
    };

    const handleApproveEmergency = async (requestId, shareIndex) => {
        setEmgMsg(`⏳ Approving request ${requestId} with share ${shareIndex}...`);
        try {
            const res = await api.approveEmergencyRequest(requestId, shareIndex);
            if (res.granted) {
                setEmgMsg(`✅ Threshold met! Access completely GRANTED for ${requestId}.`);
            } else {
                setEmgMsg(`✅ Share ${shareIndex} accepted for ${requestId}. Current approvals: ${res.approvalCount || '?'}/${res.threshold || 3}`);
            }
            loadEmergencyRequests(); // refresh list
            loadAuditLogs(); // refresh logs to show the new approval
        } catch (err) {
            setEmgMsg('❌ Approval failed: ' + err.message);
        }
    };

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

                <div className="admin-actions-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', gap: '1.5rem', marginBottom: '1.5rem' }}>

                    {/* Register Medical Staff */}
                    <div className="card" style={{ marginBottom: 0 }}>
                        <h3>👨‍⚕️ Register Medical Staff</h3>
                        <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '1rem' }}>
                            Provision new Doctor or Admin accounts. Standard users cannot register these roles publicly.
                        </p>
                        <form onSubmit={handleRegisterStaff}>
                            <div className="form-row">
                                <div className="form-group">
                                    <label>Username</label>
                                    <input type="text" value={regUsername} onChange={e => setRegUsername(e.target.value)} placeholder="e.g. dr_smith" />
                                </div>
                                <div className="form-group">
                                    <label>Role</label>
                                    <select value={regRole} onChange={e => setRegRole(e.target.value)}>
                                        <option value="DOCTOR">Doctor</option>
                                        <option value="ADMIN">System Admin</option>
                                    </select>
                                </div>
                            </div>
                            <div className="form-group">
                                <label>Password</label>
                                <input type="password" value={regPassword} onChange={e => setRegPassword(e.target.value)} placeholder="Strong password" />
                            </div>
                            <button type="submit" className="btn-primary" disabled={regLoading}>
                                {regLoading ? '⏳ Registering...' : '➕ Create Account'}
                            </button>
                            {regMsg && (
                                <div className={regMsg.includes('✅') ? 'success' : 'error'} style={{ marginTop: '0.75rem', fontSize: '0.85rem' }}>
                                    {regMsg}
                                </div>
                            )}
                        </form>
                    </div>

                    {/* Emergency Approvals */}
                    <div className="card" style={{ marginBottom: 0 }}>
                        <h3>🚨 Pending Emergency Requests
                            <button onClick={loadEmergencyRequests} disabled={emgLoading} style={{ marginLeft: '1rem', fontSize: '0.8rem', padding: '4px 10px', cursor: 'pointer', border: '1px solid #d1d5db', borderRadius: '4px', background: '#f9fafb' }}>
                                {emgLoading ? '⏳' : '↺ Refresh'}
                            </button>
                        </h3>
                        <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '1rem' }}>
                            Stakeholder panel. Approve life-critical access requests by submitting your cryptographic share.
                        </p>
                        <div style={{ background: '#e0e7ff', borderLeft: '4px solid #4f46e5', padding: '0.75rem', marginBottom: '1rem', borderRadius: '4px', fontSize: '0.8rem', color: '#3730a3' }}>
                            <strong>Simulation Note:</strong> In production, these approvals are safely distributed to patient-assigned trustees (e.g., family, primary physician). For this demonstration, they are aggregated here in the Admin panel.
                        </div>
                        {emgMsg && (
                            <div className={emgMsg.includes('✅') ? 'success' : emgMsg.includes('⏳') ? '' : 'error'} style={{ marginBottom: '1rem', fontSize: '0.85rem' }}>
                                {emgMsg}
                            </div>
                        )}
                        {emgRequests.length === 0 ? (
                            <p className="empty-state" style={{ padding: '1rem', fontSize: '0.85rem' }}>No pending emergency requests.</p>
                        ) : (
                            <div className="records-list" style={{ maxHeight: '250px', overflowY: 'auto', paddingRight: '5px' }}>
                                {emgRequests.filter(r => r.status === 'PENDING').length === 0 && (
                                    <p className="empty-state" style={{ padding: '1rem', fontSize: '0.85rem' }}>All requests have been finalized.</p>
                                )}
                                {emgRequests.filter(r => r.status === 'PENDING').map(req => (
                                    <div key={req.requestId} className="record-item" style={{ flexDirection: 'column', alignItems: 'flex-start', padding: '1rem', background: '#fef2f2', border: '1px solid #fecaca' }}>
                                        <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                            <strong>ID: {req.requestId}</strong>
                                            <span style={{ fontSize: '0.75rem', color: '#dc2626', fontWeight: 600 }}>{req.collectedShares?.length || 0}/{req.threshold} Approvals</span>
                                        </div>
                                        <div style={{ fontSize: '0.8rem', color: '#4b5563', marginBottom: '0.75rem' }}>
                                            <div><strong>By:</strong> {req.requesterId} <strong>For:</strong> {req.patientId}</div>
                                            <div><strong>Reason:</strong> {req.reason}</div>
                                            <div><strong>Record:</strong> {req.recordId}</div>
                                        </div>
                                        <div style={{ display: 'flex', gap: '0.5rem', width: '100%' }}>
                                            {/* Simulate stakeholders 1, 2, 3 submitting their shares */}
                                            {[1, 2, 3].map(i => (
                                                <button key={i}
                                                    onClick={() => handleApproveEmergency(req.requestId, i)}
                                                    className="btn-primary"
                                                    style={{ flex: 1, padding: '0.4rem', fontSize: '0.75rem', background: '#dc2626' }}>
                                                    Submit Share {i}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

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
