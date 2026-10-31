import React, { createContext, useContext, useState, useEffect } from 'react';
import { getUsers } from '../api';

const UserContext = createContext(null);

export function UserProvider({ children }) {
  const [users, setUsers] = useState([]);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('smartlib_user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getUsers()
      .then(setUsers)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const selectUser = (user) => {
    setCurrentUser(user);
    localStorage.setItem('smartlib_user', JSON.stringify(user));
  };

  return (
    <UserContext.Provider value={{ users, currentUser, selectUser, loading }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  return useContext(UserContext);
}

