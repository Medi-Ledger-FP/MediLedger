const API_BASE_URL = 'http://localhost:8080/api';

// Get auth token from localStorage
const getAuthToken = () => localStorage.getItem('token');

// API client with auth header
const api = {
    // Auth endpoints
    register: async (username, password, role) => {
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, role })
        });
        return response.json();
    },

    login: async (username, password) => {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        return response.json();
    },

    // File endpoints
    uploadFile: async (file, patientId, recordType, department) => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('patientId', patientId);
        formData.append('recordType', recordType);
        formData.append('department', department);

        const response = await fetch(`${API_BASE_URL}/files/upload`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${getAuthToken()}` },
            body: formData
        });
        return response.json();
    },

    downloadFile: async (recordId) => {
        const response = await fetch(`${API_BASE_URL}/files/download/${recordId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        if (!response.ok) {
            let errorMsg = 'Download failed';
            try {
                const errData = await response.json();
                errorMsg = errData.message || errData.error || errorMsg;
            } catch (e) {
                errorMsg = response.statusText;
            }
            throw new Error(errorMsg);
        }
        return response.blob();
    },

    // Record endpoints
    getPatientRecords: async (patientId) => {
        const response = await fetch(`${API_BASE_URL}/records/patient/${patientId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    // Consent endpoints
    grantConsent: async (patientId, doctorId, recordId, purpose) => {
        const response = await fetch(`${API_BASE_URL}/consent/grant`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${getAuthToken()}`
            },
            body: JSON.stringify({ patientId, doctorId, recordId, expiresAt: null, purpose: purpose || 'Treatment' })
        });
        return response.json();
    },

    revokeConsent: async (grantId) => {
        const response = await fetch(`${API_BASE_URL}/consent/revoke/${grantId}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    listAuthorizedDoctors: async (patientId) => {
        const response = await fetch(`${API_BASE_URL}/consent/patient/${patientId}/doctors`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    // Emergency Access endpoints
    openEmergencyRequest: async (patientId, recordId, reason) => {
        const response = await fetch(`${API_BASE_URL}/emergency/request`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${getAuthToken()}`
            },
            body: JSON.stringify({ patientId, recordId, reason })
        });
        return response.json();
    },

    approveEmergencyRequest: async (requestId, shareIndex) => {
        const response = await fetch(`${API_BASE_URL}/emergency/approve/${requestId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${getAuthToken()}`
            },
            body: JSON.stringify({ shareIndex })
        });
        return response.json();
    },

    getEmergencyStatus: async (requestId) => {
        const response = await fetch(`${API_BASE_URL}/emergency/status/${requestId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    emergencyDownload: async (requestId) => {
        const response = await fetch(`${API_BASE_URL}/emergency/download/${requestId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.error || 'Emergency download failed');
        }
        return response.blob();
    },

    listEmergencyRequestsByPatient: async (patientId) => {
        const response = await fetch(`${API_BASE_URL}/emergency/requests/patient/${patientId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    // Audit endpoints
    getAuditLog: async (patientId) => {
        const response = await fetch(`${API_BASE_URL}/audit/patient/${patientId}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    },

    getRecentAuditLogs: async (max = 100) => {
        const response = await fetch(`${API_BASE_URL}/audit/recent?max=${max}`, {
            headers: { 'Authorization': `Bearer ${getAuthToken()}` }
        });
        return response.json();
    }
};

export default api;
