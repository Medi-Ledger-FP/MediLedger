import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './Login.css';

function Login({ setUser }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isRegister, setIsRegister] = useState(false);
    const [role, setRole] = useState('PATIENT');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        try {
            // Both register and login return a token — handle identically
            const result = isRegister
                ? await api.register(username, password, role)
                : await api.login(username, password);

            if (result.token) {
                // Always trust the role from the server response
                const serverRole = result.role || role;
                localStorage.setItem('token', result.token);
                localStorage.setItem('username', result.username || username);
                localStorage.setItem('role', serverRole);
                setUser({ username: result.username || username, role: serverRole });

                // Route based on server-confirmed role
                const dest = serverRole === 'DOCTOR' ? '/doctor'
                    : serverRole === 'ADMIN' ? '/admin'
                        : '/patient';
                navigate(dest);
            } else {
                setError(result.error || result.message || 'Authentication failed');
            }
        } catch (err) {
            setError('Connection error: ' + err.message);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>🏥 MediLedger</h1>
                <p className="subtitle">Blockchain-Powered Healthcare Records</p>

                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Username"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        required
                    />
                    <input
                        type="password"
                        placeholder="Password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />

                    {isRegister && (
                        <select value={role} onChange={(e) => setRole(e.target.value)}>
                            <option value="PATIENT">Patient</option>
                            <option value="DOCTOR">Doctor</option>
                            <option value="ADMIN">Admin</option>
                        </select>
                    )}

                    {error && <div className="error">{error}</div>}

                    <button type="submit" className="btn-primary">
                        {isRegister ? 'Register' : 'Login'}
                    </button>
                </form>

                <button
                    className="btn-link"
                    onClick={() => setIsRegister(!isRegister)}
                >
                    {isRegister ? 'Already have an account? Login' : 'Need an account? Register'}
                </button>
            </div>
        </div>
    );
}

export default Login;
