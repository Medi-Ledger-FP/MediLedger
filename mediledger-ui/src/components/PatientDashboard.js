import React, { useState, useEffect } from 'react';
import api from '../services/api';
import './Dashboard.css';

function PatientDashboard({ user }) {
    const [records, setRecords] = useState([]);
    const [file, setFile] = useState(null);
    const [recordType, setRecordType] = useState('Lab Report');
    const [department, setDepartment] = useState('General');
    const [uploading, setUploading] = useState(false);
    const [message, setMessage] = useState('');

    // Consent state
    const [consentDoctorId, setConsentDoctorId] = useState('');
    const [consentRecordId, setConsentRecordId] = useState('');
    const [consentPurpose, setConsentPurpose] = useState('Treatment');
    const [consentMsg, setConsentMsg] = useState('');
    const [consentLoading, setConsentLoading] = useState(false);
    const [authorizedDoctors, setAuthorizedDoctors] = useState([]);

    const patientId = user?.username || localStorage.getItem('username');

    useEffect(() => {
        loadRecords();
        loadAuthorizedDoctors();
    }, []);

    const loadRecords = async () => {
        try {
            const data = await api.getPatientRecords(patientId);
            setRecords(typeof data === 'string' ? JSON.parse(data) : (Array.isArray(data) ? data : []));
        } catch (err) {
            console.error('Failed to load records:', err);
            setRecords([]);
        }
    };

    const loadAuthorizedDoctors = async () => {
        try {
            const data = await api.listAuthorizedDoctors(patientId);
            setAuthorizedDoctors(typeof data === 'string' ? JSON.parse(data) : (Array.isArray(data) ? data : []));
        } catch (err) {
            console.error('Failed to load authorized doctors:', err);
        }
    };

    const handleUpload = async (e) => {
        e.preventDefault();
        if (!file) { setMessage('Please select a file'); return; }
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
            a.download = `medical_record_${recordId}.dat`;
            a.click();
        } catch (err) {
            alert('Download failed: ' + err.message);
        }
    };

    const handleGrantConsent = async (e) => {
        e.preventDefault();
        if (!consentDoctorId.trim() || !consentRecordId.trim()) {
            setConsentMsg('❌ Doctor username and Record ID are required.');
            return;
        }
        setConsentLoading(true);
        setConsentMsg('');
        try {
            const result = await api.grantConsent(patientId, consentDoctorId.trim(), consentRecordId.trim(), consentPurpose);
            if (result.success || result.grantId) {
                setConsentMsg(`✅ Access granted to Dr. ${consentDoctorId} for record ${consentRecordId.substring(0, 8)}… (Grant ID: ${result.message || result.grantId || 'done'})`);
                setConsentDoctorId('');
                setConsentRecordId('');
                loadAuthorizedDoctors();
            } else {
                setConsentMsg(`❌ ${result.message || result.error || 'Failed to grant access'}`);
            }
        } catch (err) {
            setConsentMsg(`❌ Error: ${err.message}`);
        } finally {
            setConsentLoading(false);
        }
    };

    const handleLogout = () => {
        localStorage.clear();
        window.location.href = '/';
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
                                accept=".pdf,.jpg,.png,.dcm,.txt"
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
                            {uploading ? '⏳ Uploading & Encrypting…' : '📤 Upload Encrypted File'}
                        </button>
                    </form>
                    {message && (
                        <div className={message.includes('✅') ? 'success' : 'error'}>{message}</div>
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
                                            {record.department} • {record.timestamp ? new Date(record.timestamp).toLocaleDateString() : 'Recent'}
                                        </span>
                                        <span className="record-meta" style={{ fontSize: '0.7rem', color: '#9ca3af' }}>
                                            ID: {record.recordId}
                                        </span>
                                    </div>
                                    <button onClick={() => handleDownload(record.recordId)} className="btn-secondary">
                                        📥 Download
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Consent Management */}
                <div className="card">
                    <h3>🔐 Grant Doctor Access</h3>
                    <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '1rem' }}>
                        Grant a specific doctor permission to download one of your records.
                        Without your explicit consent, doctors cannot access your files even if they have the correct role.
                    </p>
                    <form onSubmit={handleGrantConsent}>
                        <div className="form-row">
                            <div className="form-group">
                                <label>Doctor Username:</label>
                                <input
                                    type="text"
                                    placeholder="e.g. doctor_demo"
                                    value={consentDoctorId}
                                    onChange={(e) => setConsentDoctorId(e.target.value)}
                                />
                            </div>
                            <div className="form-group">
                                <label>Record ID (from list above):</label>
                                <input
                                    type="text"
                                    placeholder="Paste Record ID"
                                    value={consentRecordId}
                                    onChange={(e) => setConsentRecordId(e.target.value)}
                                    list="record-id-list"
                                />
                                <datalist id="record-id-list">
                                    {records.map((r) => <option key={r.recordId} value={r.recordId}>{r.recordType}</option>)}
                                </datalist>
                            </div>
                        </div>
                        <div className="form-group">
                            <label>Purpose:</label>
                            <input
                                type="text"
                                placeholder="e.g. Treatment, Second Opinion"
                                value={consentPurpose}
                                onChange={(e) => setConsentPurpose(e.target.value)}
                            />
                        </div>
                        <button type="submit" className="btn-primary" disabled={consentLoading}>
                            {consentLoading ? '⏳ Granting…' : '✅ Grant Access'}
                        </button>
                    </form>
                    {consentMsg && (
                        <div className={consentMsg.includes('✅') ? 'success' : 'error'}>{consentMsg}</div>
                    )}

                    {authorizedDoctors.length > 0 && (
                        <div style={{ marginTop: '1rem' }}>
                            <strong>Currently authorized doctors:</strong>
                            <ul style={{ marginTop: '0.5rem', paddingLeft: '1.5rem', color: '#374151' }}>
                                {authorizedDoctors.map((d, i) => <li key={i}>{d}</li>)}
                            </ul>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default PatientDashboard;
