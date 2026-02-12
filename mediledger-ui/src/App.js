import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/Login';
import PatientDashboard from './components/PatientDashboard';
import DoctorDashboard from './components/DoctorDashboard';
import AdminDashboard from './components/AdminDashboard';
import './App.css';

function App() {
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    const role = localStorage.getItem('role');
    return token ? { username, role } : null;
  });

  const ProtectedRoute = ({ children, allowedRoles }) => {
    if (!user) {
      return <Navigate to="/" replace />;
    }
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      return <Navigate to="/" replace />;
    }
    return children;
  };

  return (
    <Router>
      <div className="App">
        <Routes>
          <Route path="/" element={
            user ? <Navigate to={`/${user.role.toLowerCase()}`} replace /> : <Login setUser={setUser} />
          } />

          <Route path="/patient" element={
            <ProtectedRoute allowedRoles={['PATIENT']}>
              <PatientDashboard user={user} />
            </ProtectedRoute>
          } />

          <Route path="/doctor" element={
            <ProtectedRoute allowedRoles={['DOCTOR']}>
              <DoctorDashboard user={user} />
            </ProtectedRoute>
          } />

          <Route path="/admin" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminDashboard user={user} />
            </ProtectedRoute>
          } />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
