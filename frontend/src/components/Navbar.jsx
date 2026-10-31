import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useUser } from '../context/UserContext';

export default function Navbar() {
  const { users, currentUser, selectUser } = useUser();
  const navigate = useNavigate();

  const handleUserChange = (e) => {
    const user = users.find((u) => u.id === e.target.value) || null;
    selectUser(user);
    navigate('/books');
  };

  return (
    <nav className="navbar">
      <div className="navbar__brand">
        <Link to="/books">📚 SmartLib</Link>
      </div>
      <div className="navbar__links">
        <Link to="/books">Books</Link>
        {currentUser?.role === 'STUDENT' && (
          <Link to="/my-loans">My Loans</Link>
        )}
        {currentUser?.role === 'LIBRARIAN' && (
          <Link to="/librarian">Librarian Dashboard</Link>
        )}
      </div>
      <div className="navbar__user">
        <select
          value={currentUser?.id || ''}
          onChange={handleUserChange}
        >
          <option value="">— Select user —</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.name} ({u.role})
            </option>
          ))}
        </select>
      </div>
    </nav>
  );
}

