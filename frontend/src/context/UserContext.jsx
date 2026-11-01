import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { getUsers, getUser } from '../api';

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
    if (user) {
      localStorage.setItem('smartlib_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('smartlib_user');
    }
  };

  // Re-fetch the current user so points / state stay fresh after actions.
  const refreshUser = useCallback(() => {
    if (!currentUser) return;
    getUser(currentUser.id)
      .then((fresh) => {
        setCurrentUser(fresh);
        localStorage.setItem('smartlib_user', JSON.stringify(fresh));
      })
      .catch(console.error);
  }, [currentUser]);

  return (
    <UserContext.Provider value={{ users, currentUser, selectUser, refreshUser, loading }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  return useContext(UserContext);
}
