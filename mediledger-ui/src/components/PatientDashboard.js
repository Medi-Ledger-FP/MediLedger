import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './Dashboard.css';

function PatientDashboard({ user }) {
    const [records, setRecords] = useState([]);
    const [file, setFile] = useState(null);
    const [recordType, setRecordType] = useState('Lab Report');
    const [department, setDepartment] = useState('General');
    const [uploading, setUploading] = useState(false);
    const [message, setMessage] = useState('');
    const navigate = useNavigate();

    const patientId = user?.username || localStorage.getItem('username');

    useEffect(() => {
        loadRecords();
    }, []);

    const loadRecords = async () => {
        try {
            const data = await api.getPatientRecords(patientId);
            // Parse if string, otherwise use as is
            setRecords(typeof data === 'string' ? JSON.parse(data) : (Array.isArray(data) ? data : []));
        } catch (err) {
            console.error('Failed to load records:', err);
            setRecords([]);
        }
    };

    const handleUpload = async (e) => {
        e.preventDefault();
        if (!file) {
            setMessage('Please select a file');
            return;
        }

        setUploading(true);
        setMessage('');

        try {
            const result = await api.uploadFile(file, patientId, recordType, department);
            setMessage(`✅ File uploaded successfully! Record ID: ${result.recordId}`);
            setFile(null);
            loadRecords();
        } catch (err) {
            setMessage(`❌ Upload failed: ${err.message}`);
        } finally {
            setUploading(false);
        }
    };

    const handleDownload = async (recordId) => {
        try {
            const blob = await api.downloadFile(recordId);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `medical_record_${recordId}.pdf`;
            a.click();
        } catch (err) {
            alert('Download failed: ' + err.message);
        }
    };

    const handleLogout = () => {
        localStorage.clear();
        navigate('/');
    };

    return (
        <div className="dashboard">
            <nav className="navbar">
                <h2>🏥 Patient Dashboard</h2>
                <div>
                    <span className="username">{patientId}</span>
                    <button onClick={handleLogout} className="btn-logout">Logout</button>
                </div>
            </nav>

            <div className="dashboard-content">
                {/* Upload Section */}
                <div className="card">
                    <h3>📤 Upload Medical Record</h3>
                    <form onSubmit={handleUpload}>
                        <div className="form-group">
                            <label>Select File:</label>
                            <input
                                type="file"
                                onChange={(e) => setFile(e.target.files[0])}
                                accept=".pdf,.jpg,.png,.dcm"
                            />
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label>Record Type:</label>
                                <select value={recordType} onChange={(e) => setRecordType(e.target.value)}>
                                    <option>Lab Report</option>
                                    <option>X-Ray</option>
                                    <option>MRI Scan</option>
                                    <option>Prescription</option>
                                    <option>Diagnosis</option>
                                </select>
                            </div>

                            <div className="form-group">
                                <label>Department:</label>
                                <select value={department} onChange={(e) => setDepartment(e.target.value)}>
                                    <option>General</option>
                                    <option>Cardiology</option>
                                    <option>Neurology</option>
                                    <option>Radiology</option>
                                    <option>Orthopedics</option>
                                </select>
                            </div>
                        </div>

                        <button type="submit" className="btn-primary" disabled={uploading}>
                            {uploading ? '⏳ Uploading...' : '📤 Upload Encrypted File'}
                        </button>
                    </form>

                    {message && (
                        <div className={message.includes('✅') ? 'success' : 'error'}>
                            {message}
                        </div>
                    )}
                </div>

                {/* Records List */}
                <div className="card">
                    <h3>📁 My Medical Records</h3>
                    {records.length === 0 ? (
                        <p className="empty-state">No records yet. Upload your first medical file!</p>
                    ) : (
                        <div className="records-list">
                            {records.map((record, idx) => (
                                <div key={idx} className="record-item">
                                    <div className="record-info">
                                        <strong>{record.recordType || 'Medical Record'}</strong>
                                        <span className="record-meta">
                                            {record.department} • {record.timestamp || 'Recent'}
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
            </div>
        </div>
    );
}

export default PatientDashboard;
