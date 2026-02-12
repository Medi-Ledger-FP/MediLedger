import React from 'react';
import { useNavigate } from 'react-router-dom';
import './Dashboard.css';

function AdminDashboard() {
    const navigate = useNavigate();

    const stats = {
        totalUsers: 156,
        totalRecords: 1243,
        activeConsents: 89,
        auditEntries: 5621
    };

    const handleLogout = () => {
        localStorage.clear();
        navigate('/');
    };

    return (
        <div className="dashboard">
            <nav className="navbar">
                <h2>⚙️ Admin Dashboard</h2>
                <div>
                    <span className="username">Administrator</span>
                    <button onClick={handleLogout} className="btn-logout">Logout</button>
                </div>
            </nav>

            <div className="dashboard-content">
                {/* Statistics */}
                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="stat-number">{stats.totalUsers}</div>
                        <div className="stat-label">Total Users</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">{stats.totalRecords}</div>
                        <div className="stat-label">Medical Records</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">{stats.activeConsents}</div>
                        <div className="stat-label">Active Consents</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">{stats.auditEntries}</div>
                        <div className="stat-label">Audit Entries</div>
                    </div>
                </div>

                {/* Recent Activity */}
                <div className="card">
                    <h3>📊 Recent System Activity</h3>
                    <div className="activity-list">
                        <div className="activity-item">
                            <span className="activity-type success">✅ RECORD_UPLOAD</span>
                            <span>patient123 uploaded Lab Report</span>
                            <span className="activity-time">2 minutes ago</span>
                        </div>
                        <div className="activity-item">
                            <span className="activity-type info">🔍 RECORD_ACCESS</span>
                            <span>dr_smith accessed patient456's X-Ray</span>
                            <span className="activity-time">15 minutes ago</span>
                        </div>
                        <div className="activity-item">
                            <span className="activity-type warning">⚠️ ACCESS_REQUEST</span>
                            <span>dr_jones requested access to patient789</span>
                            <span className="activity-time">1 hour ago</span>
                        </div>
                    </div>
                </div>

                {/* System Health */}
                <div className="card">
                    <h3>💚 System Health</h3>
                    <div className="health-grid">
                        <div className="health-item">
                            <span className="health-indicator green"></span>
                            <strong>Blockchain Network:</strong> Online
                        </div>
                        <div className="health-item">
                            <span className="health-indicator green"></span>
                            <strong>IPFS Storage:</strong> Operational
                        </div>
                        <div className="health-item">
                            <span className="health-indicator green"></span>
                            <strong>Encryption Service:</strong> Active
                        </div>
                        <div className="health-item">
                            <span className="health-indicator yellow"></span>
                            <strong>Database:</strong> Syncing
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default AdminDashboard;
