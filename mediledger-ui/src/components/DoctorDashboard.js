import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Dashboard.css';

function DoctorDashboard({ user }) {
    const [patientId, setPatientId] = useState('');
    const [records, setRecords] = useState([]);
    const navigate = useNavigate();

    const doctorId = user?.username || localStorage.getItem('username');

    const handleSearch = () => {
        // Simulate fetching patient's accessible records
        setRecords([
            {
                recordId: 'rec123',
                patientId: patientId,
                recordType: 'Lab Report',
                department: 'Cardiology',
                timestamp: '2026-02-10'
            }
        ]);
    };

    const handleDownload = (recordId) => {
        alert(`Downloading record: ${recordId}\n\n(Feature ready - backend integration pending)`);
    };

    const handleLogout = () => {
        localStorage.clear();
        navigate('/');
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
                {/* Search Patients */}
                <div className="card">
                    <h3>🔍 Search Patient Records</h3>
                    <div className="search-box">
                        <input
                            type="text"
                            placeholder="Enter Patient ID"
                            value={patientId}
                            onChange={(e) => setPatientId(e.target.value)}
                        />
                        <button onClick={handleSearch} className="btn-primary">
                            Search
                        </button>
                    </div>
                </div>

                {/* Accessible Records */}
                <div className="card">
                    <h3>📋 Accessible Patient Records</h3>
                    {records.length === 0 ? (
                        <p className="empty-state">Search for a patient to view their records</p>
                    ) : (
                        <div className="records-list">
                            {records.map((record, idx) => (
                                <div key={idx} className="record-item">
                                    <div className="record-info">
                                        <strong>{record.patientId} - {record.recordType}</strong>
                                        <span className="record-meta">
                                            {record.department} • {record.timestamp}
                                        </span>
                                    </div>
                                    <button
                                        onClick={() => handleDownload(record.recordId)}
                                        className="btn-secondary"
                                    >
                                        📥 Download
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* My Access Grants */}
                <div className="card">
                    <h3>✅ My Access Grants</h3>
                    <p className="empty-state">Patients who have granted you access will appear here</p>
                </div>
            </div>
        </div>
    );
}

export default DoctorDashboard;
