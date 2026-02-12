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
            let result;
            if (isRegister) {
                result = await api.register(username, password, role);
                if (result.token) {
                    alert('Registration successful! Please login.');
                    setIsRegister(false);
                    return;
                }
            } else {
                result = await api.login(username, password);
            }

            if (result.token) {
                localStorage.setItem('token', result.token);
                localStorage.setItem('username', username);
                localStorage.setItem('role', result.role || role);
                setUser({ username, role: result.role || role });

                // Navigate based on role
                if (result.role === 'PATIENT' || role === 'PATIENT') {
                    navigate('/patient');
                } else if (result.role === 'DOCTOR' || role === 'DOCTOR') {
                    navigate('/doctor');
                } else {
                    navigate('/admin');
                }
            } else {
                setError(result.message || 'Authentication failed');
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
