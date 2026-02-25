import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './Dashboard.css';

function DoctorDashboard({ user }) {
    const [patientId, setPatientId] = useState('');
    const [records, setRecords] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchDone, setSearchDone] = useState(false);
    const [message, setMessage] = useState('');
    const navigate = useNavigate();

    const doctorId = user?.username || localStorage.getItem('username');

    const handleSearch = async () => {
        if (!patientId.trim()) {
            setMessage('Please enter a Patient ID.');
            return;
        }
        setLoading(true);
        setMessage('');
        setRecords([]);
        try {
            const data = await api.getPatientRecords(patientId.trim());
            const list = typeof data === 'string' ? JSON.parse(data) : (Array.isArray(data) ? data : []);
            setRecords(list);
            setSearchDone(true);
            if (list.length === 0) setMessage(`No accessible records found for patient: ${patientId}`);
        } catch (err) {
            setMessage('Search failed: ' + err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async (recordId, recordType) => {
        setMessage(`⏳ Downloading ${recordType || 'record'}…`);
        try {
            const blob = await api.downloadFile(recordId);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${recordType || 'medical_record'}_${recordId}.dat`;
            a.click();
            window.URL.revokeObjectURL(url);
            setMessage(`✅ Downloaded ${recordType || 'record'} successfully.`);
        } catch (err) {
            setMessage('❌ Download failed: ' + err.message);
        }
    };

    const handleLogout = () => {
        localStorage.clear();
        window.location.href = '/';
    };

    return (
        <div className="dashboard">
            <nav className="navbar">
                <h2>👨‍⚕️ Doctor Dashboard</h2>
                <div>
                    <span className="username">Dr. {doctorId}</span>
                    <button onClick={handleLogout} className="btn-logout">Logout</button>
                </div>
            </nav>

            <div className="dashboard-content">
                {/* Search */}
                <div className="card">
                    <h3>🔍 Search Patient Records</h3>
                    <div className="search-box">
                        <input
                            type="text"
                            placeholder="Enter Patient ID (e.g. patient123)"
                            value={patientId}
                            onChange={(e) => setPatientId(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                        />
                        <button onClick={handleSearch} className="btn-primary" disabled={loading}>
                            {loading ? '⏳ Searching…' : 'Search'}
                        </button>
                    </div>
                    {message && (
                        <div className={message.startsWith('✅') ? 'success' : message.startsWith('❌') ? 'error' : ''} style={{ marginTop: '0.75rem' }}>
                            {message}
                        </div>
                    )}
                </div>

                {/* Records List */}
                <div className="card">
                    <h3>📋 Accessible Patient Records
                        {records.length > 0 && <span style={{ fontSize: '0.85rem', fontWeight: 400, marginLeft: '0.5rem', color: '#6b7280' }}>({records.length} found)</span>}
                    </h3>
                    {!searchDone ? (
                        <p className="empty-state">Search for a patient to view their records.</p>
                    ) : records.length === 0 ? (
                        <p className="empty-state">No records found — either no uploads or access denied by patient policy.</p>
                    ) : (
                        <div className="records-list">
                            {records.map((record, idx) => (
                                <div key={idx} className="record-item">
                                    <div className="record-info">
                                        <strong>{record.recordType || 'Medical Record'}</strong>
                                        <span className="record-meta">
                                            {record.department} &bull; {record.patientId} &bull; {record.timestamp ? new Date(record.timestamp).toLocaleDateString() : 'Recent'}
                                        </span>
                                        <span className="record-meta" style={{ fontSize: '0.7rem', color: '#9ca3af' }}>
                                            IPFS: {record.ipfsCid ? record.ipfsCid.substring(0, 20) + '…' : 'N/A'}
                                        </span>
                                    </div>
                                    <button
                                        onClick={() => handleDownload(record.recordId, record.recordType)}
                                        className="btn-secondary"
                                    >
                                        📥 Download
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Info Card */}
                <div className="card">
                    <h3>🔐 Access Control</h3>
                    <p style={{ color: '#6b7280', fontSize: '0.9rem', margin: 0 }}>
                        Records are decrypted using CP-ABE role-based keys. You only see records where the patient has granted <strong>DOCTOR</strong> access in their upload policy.
                        All access attempts are immutably logged on the blockchain.
                    </p>
                </div>
            </div>
        </div>
    );
}

export default DoctorDashboard;
