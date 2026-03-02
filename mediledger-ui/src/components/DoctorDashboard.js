import React, { useState } from 'react';
import api from '../services/api';
import './Dashboard.css';

function DoctorDashboard({ user }) {
    const [patientId, setPatientId] = useState('');
    const [records, setRecords] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchDone, setSearchDone] = useState(false);
    const [message, setMessage] = useState('');

    // Emergency Access state
    const [emgPatientId, setEmgPatientId] = useState('');
    const [emgRecordId, setEmgRecordId] = useState('');
    const [emgReason, setEmgReason] = useState('');
    const [emgRequestId, setEmgRequestId] = useState('');
    const [emgShareIndex, setEmgShareIndex] = useState('1');
    const [emgApproveId, setEmgApproveId] = useState('');
    const [emgDownloadId, setEmgDownloadId] = useState('');
    const [emgStatus, setEmgStatus] = useState(null);
    const [emgMsg, setEmgMsg] = useState('');
    const [emgLoading, setEmgLoading] = useState(false);

    const doctorId = user?.username || localStorage.getItem('username');

    const handleSearch = async () => {
        if (!patientId.trim()) { setMessage('Please enter a Patient ID.'); return; }
        setLoading(true);
        setMessage('');
        setRecords([]);
        try {
            const data = await api.getPatientRecords(patientId.trim());
            const list = typeof data === 'string' ? JSON.parse(data) : (Array.isArray(data) ? data : []);
            setRecords(list);
            setSearchDone(true);
            if (list.length === 0) setMessage(`No records found for patient: ${patientId}. They may not exist or access is not granted.`);
        } catch (err) {
            setMessage('Search failed: ' + err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async (recordId, recordType) => {
        setMessage(`⏳ Downloading ${recordType || 'record'}…`);
        try {
            const result = await api.downloadFile(recordId);
            const url = window.URL.createObjectURL(result.blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = result.filename || `${recordType || 'medical_record'}_${recordId}.dat`;
            a.click();
            window.URL.revokeObjectURL(url);
            setMessage(`✅ Downloaded ${result.filename || recordType} successfully.`);
        } catch (err) {
            setMessage(`❌ Download failed: ${err.message}`);
        }
    };

    // Emergency handlers
    const handleOpenEmergency = async (e) => {
        e.preventDefault();
        if (!emgPatientId.trim() || !emgRecordId.trim() || !emgReason.trim()) {
            setEmgMsg('❌ All fields required.');
            return;
        }
        setEmgLoading(true);
        setEmgMsg('');
        try {
            const result = await api.openEmergencyRequest(emgPatientId.trim(), emgRecordId.trim(), emgReason.trim());
            if (result.requestId) {
                setEmgRequestId(result.requestId);
                setEmgApproveId(result.requestId);
                setEmgDownloadId(result.requestId);
                setEmgMsg(`✅ Emergency request opened. Request ID: ${result.requestId}\n\nNow get ${result.threshold || 3} approvers to submit shares using that Request ID.`);
            } else {
                setEmgMsg(`❌ ${result.error || result.message || 'Failed to open request'}`);
            }
        } catch (err) {
            setEmgMsg(`❌ ${err.message}`);
        } finally {
            setEmgLoading(false);
        }
    };

    const handleApprove = async (e) => {
        e.preventDefault();
        if (!emgApproveId.trim()) { setEmgMsg('❌ Request ID required.'); return; }
        setEmgLoading(true);
        setEmgMsg('');
        try {
            const result = await api.approveEmergencyRequest(emgApproveId.trim(), parseInt(emgShareIndex));
            const granted = result.granted === true || result.granted === 'true';
            setEmgStatus(result);
            if (granted) {
                setEmgMsg(`🔓 Threshold met! Emergency access GRANTED. Approvals: ${result.approvalCount}/${result.threshold}. You can now Emergency Download.`);
            } else {
                setEmgMsg(`✅ Share submitted. Approvals so far: ${result.approvalCount || '?'}/${result.threshold || 3}. Need more approvals.`);
            }
        } catch (err) {
            setEmgMsg(`❌ ${err.message}`);
        } finally {
            setEmgLoading(false);
        }
    };

    const handleEmergencyDownload = async () => {
        if (!emgDownloadId.trim()) { setEmgMsg('❌ Request ID required for emergency download.'); return; }
        setEmgLoading(true);
        setEmgMsg('⏳ Reconstructing key and downloading…');
        try {
            const result = await api.emergencyDownload(emgDownloadId.trim());
            const url = window.URL.createObjectURL(result.blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = result.filename || `emergency_record_${emgDownloadId.substring(0, 8)}.dat`;
            a.click();
            window.URL.revokeObjectURL(url);
            setEmgMsg(`✅ Emergency download complete: ${result.filename || 'reconstructed file'}.`);
        } catch (err) {
            setEmgMsg(`❌ Emergency download failed: ${err.message}`);
        } finally {
            setEmgLoading(false);
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
                    <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '0.75rem' }}>
                        Only records where the patient has granted you (<strong>{doctorId}</strong>) explicit consent will be downloadable.
                    </p>
                    <div className="search-box">
                        <input
                            type="text"
                            placeholder="Enter Patient ID (e.g. patient_demo)"
                            value={patientId}
                            onChange={(e) => {
                                setPatientId(e.target.value);
                                if (e.target.value.trim() === '') {
                                    setRecords([]);
                                    setSearchDone(false);
                                    setMessage('');
                                }
                            }}
                            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                        />
                        <button onClick={handleSearch} className="btn-primary" disabled={loading}>
                            {loading ? '⏳ Searching…' : 'Search'}
                        </button>
                    </div>
                    {message && (
                        <div className={message.startsWith('✅') ? 'success' : message.startsWith('❌') ? 'error' : ''}
                            style={{ marginTop: '0.75rem' }}>
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
                        <p className="empty-state">No records found — either no uploads or patient has not granted you consent.</p>
                    ) : (
                        <div className="records-list">
                            {records.map((record, idx) => (
                                <div key={idx} className="record-item">
                                    <div className="record-info">
                                        <strong>{record.recordType || 'Medical Record'}</strong>
                                        <span className="record-meta">
                                            {record.department} • {record.patientId} • {record.timestamp ? new Date(record.timestamp).toLocaleDateString() : 'Recent'}
                                        </span>
                                        <span className="record-meta" style={{ fontSize: '0.7rem', color: '#9ca3af' }}>
                                            IPFS: {record.ipfsCid ? record.ipfsCid.substring(0, 20) + '…' : 'N/A'} | ID: {record.recordId}
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

                {/* Emergency Access Section */}
                <div className="card">
                    <h3>🚨 Emergency Access (Shamir Secret Sharing)</h3>
                    <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '1rem' }}>
                        Use in life-critical situations when normal consent-based access is unavailable.
                        Requires <strong>3 out of 5</strong> authorized stakeholders to submit their shares.
                        All actions are permanently recorded on the blockchain audit trail.
                    </p>

                    {/* Step 1: Open Request */}
                    <div className="emergency-step" style={{ marginBottom: '1rem', padding: '1rem', border: '1px solid #e5e7eb', borderRadius: '8px', background: '#f9fafb' }}>
                        <h4 style={{ fontWeight: 600, marginTop: 0, marginBottom: '0.75rem' }}>
                            Step 1: Open Emergency Request
                        </h4>
                        <form onSubmit={handleOpenEmergency} style={{ marginTop: '0.75rem' }}>
                            <div className="form-row">
                                <div className="form-group">
                                    <label>Patient ID:</label>
                                    <input type="text" placeholder="e.g. patient_demo" value={emgPatientId} onChange={e => setEmgPatientId(e.target.value)} />
                                </div>
                                <div className="form-group">
                                    <label>Record ID:</label>
                                    <input type="text" placeholder="Record ID to access" value={emgRecordId} onChange={e => setEmgRecordId(e.target.value)} />
                                </div>
                            </div>
                            <div className="form-group">
                                <label>Reason (documented on blockchain):</label>
                                <input type="text" placeholder="e.g. Patient unconscious, critical care required" value={emgReason} onChange={e => setEmgReason(e.target.value)} />
                            </div>
                            <button type="submit" className="btn-primary" disabled={emgLoading}>
                                {emgLoading ? '⏳ Opening…' : '🚨 Open Emergency Request'}
                            </button>
                        </form>
                    </div>

                    {/* Step 2: Submit Approvals */}
                    <div className="emergency-step" style={{ marginBottom: '1rem', padding: '1rem', border: '1px solid #e5e7eb', borderRadius: '8px', background: '#f9fafb' }}>
                        <h4 style={{ fontWeight: 600, marginTop: 0, marginBottom: '0.75rem' }}>
                            Step 2: Submit Approvals (3 times with share index 1, 2, 3)
                        </h4>
                        <form onSubmit={handleApprove} style={{ marginTop: '0.75rem' }}>
                            <div className="form-row">
                                <div className="form-group">
                                    <label>Request ID:</label>
                                    <input type="text" placeholder="From Step 1" value={emgApproveId} onChange={e => setEmgApproveId(e.target.value)} />
                                </div>
                                <div className="form-group">
                                    <label>Share Index (1–5):</label>
                                    <select value={emgShareIndex} onChange={e => setEmgShareIndex(e.target.value)}>
                                        <option value="1">Share 1</option>
                                        <option value="2">Share 2</option>
                                        <option value="3">Share 3</option>
                                        <option value="4">Share 4</option>
                                        <option value="5">Share 5</option>
                                    </select>
                                </div>
                            </div>
                            <button type="submit" className="btn-primary" disabled={emgLoading}>
                                {emgLoading ? '⏳ Submitting…' : '✅ Submit Approval Share'}
                            </button>
                        </form>
                        {emgStatus && (
                            <div style={{ marginTop: '0.75rem', padding: '0.75rem', background: '#f0fdf4', borderRadius: '8px', fontSize: '0.85rem' }}>
                                Approvals: <strong>{emgStatus.approvalCount}/{emgStatus.threshold}</strong> |
                                Granted: <strong style={{ color: emgStatus.granted ? '#16a34a' : '#9ca3af' }}>{emgStatus.granted ? 'YES ✅' : 'Not yet'}</strong>
                            </div>
                        )}
                    </div>

                    {/* Step 3: Emergency Download */}
                    <div className="emergency-step" style={{ marginBottom: '1rem', padding: '1rem', border: '1px solid #e5e7eb', borderRadius: '8px', background: '#f9fafb' }}>
                        <h4 style={{ fontWeight: 600, marginTop: 0, marginBottom: '0.75rem' }}>
                            Step 3: Emergency Download (after threshold met)
                        </h4>
                        <div style={{ marginTop: '0.75rem' }}>
                            <div className="form-group">
                                <label>Request ID:</label>
                                <input type="text" placeholder="From Step 1" value={emgDownloadId} onChange={e => setEmgDownloadId(e.target.value)} />
                            </div>
                            <button onClick={handleEmergencyDownload} className="btn-primary" disabled={emgLoading}>
                                {emgLoading ? '⏳ Reconstructing key…' : '📥 Emergency Download'}
                            </button>
                        </div>
                    </div>

                    {emgMsg && (
                        <div className={emgMsg.includes('✅') || emgMsg.includes('🔓') ? 'success' : emgMsg.includes('⏳') ? '' : 'error'}
                            style={{ marginTop: '1rem', whiteSpace: 'pre-line' }}>
                            {emgMsg}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default DoctorDashboard;
