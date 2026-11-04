import React, { createContext, useContext, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(true);
  const navigate = useNavigate();

  // Restore the session from the stored token via /api/auth/me.
  useEffect(() => {
    if (!api.getToken()) {
      setBooting(false);
      return;
    }
    api.me()
      .then(setUser)
      .catch(() => api.setToken(null))
      .finally(() => setBooting(false));
  }, []);

  // Any API 401 clears the session and lands on /login with a message.
  useEffect(() => {
    return api.onUnauthorized((event) => {
      setUser(null);
      navigate('/login', {
        state: { message: (event.detail || 'Session expired. Please log in again.') },
      });
    });
  }, [navigate]);

  const login = async (email, password) => {
    const res = await api.login({ email, password });
    api.setToken(res.token);
    setUser(res.user);
    return res.user;
  };

  const register = async (body) => {
    const res = await api.register(body);
    api.setToken(res.token);
    setUser(res.user);
    return res.user;
  };

  const logout = () => {
    api.setToken(null);
    setUser(null);
    navigate('/login');
  };

  const refreshUser = () => api.me().then(setUser).catch(() => {});

  return (
    <AuthContext.Provider value={{ user, booting, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
