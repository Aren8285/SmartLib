import React, { useState, useEffect, useCallback } from 'react';
import { getBooks, getSubjects, requestLoan, getFines } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

export default function BooksPage() {
  const { currentUser } = useUser();
  const [books, setBooks] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [query, setQuery] = useState('');
  const [subject, setSubject] = useState('');
  const [availableOnly, setAvailableOnly] = useState(false);
  const [pendingFines, setPendingFines] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  const isStudent = currentUser?.role === 'STUDENT';

  const fetchBooks = useCallback(() => {
    setLoading(true);
    getBooks({ query, subject, availableOnly })
      .then(setBooks)
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [query, subject, availableOnly]);

  useEffect(() => { fetchBooks(); }, [fetchBooks]);

  useEffect(() => {
    getSubjects().then(setSubjects).catch(console.error);
  }, []);

  // Banner: pending fines block new requests for students.
  useEffect(() => {
    if (!isStudent || !currentUser) { setPendingFines(false); return; }
    getFines({ studentId: currentUser.id })
      .then((fines) => setPendingFines(fines.some((f) => f.status === 'PENDING')))
      .catch(console.error);
  }, [isStudent, currentUser]);

  const handleRequest = async (bookId) => {
    if (!currentUser) {
      setToast({ message: 'Please select a user from the navbar first.', type: 'error' });
      return;
    }
    try {
      const result = await requestLoan(currentUser.id, bookId);
      setToast({
        message: result.message || 'Request placed!',
        type: result.status === 'WAITLISTED' ? 'info' : 'success',
      });
      fetchBooks();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  return (
    <div className="page">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />
      <h1>Book Catalogue</h1>

      {isStudent && pendingFines && (
        <div className="banner banner--warning">
          ⚠️ You have pending fines. Pay them on the <a href="/my-fines">My Fines</a> page
          before requesting new books.
        </div>
      )}

      <div className="filter-bar">
        <input
          type="text"
          className="filter-bar__search"
          placeholder="Search by title or author…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select value={subject} onChange={(e) => setSubject(e.target.value)}>
          <option value="">All subjects</option>
          {subjects.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <label className="filter-bar__check">
          <input
            type="checkbox"
            checked={availableOnly}
            onChange={(e) => setAvailableOnly(e.target.checked)}
          />
          Available only
        </label>
      </div>

      {loading ? (
        <p className="loading">Loading…</p>
      ) : books.length === 0 ? (
        <p>No books found.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Author</th>
              <th>Subject</th>
              <th>Available</th>
              <th>Waitlist</th>
              {isStudent && <th>Action</th>}
            </tr>
          </thead>
          <tbody>
            {books.map((book) => {
              const canRequest = book.availableCopies > 0 && book.waitlistSize === 0;
              return (
                <tr key={book.id}>
                  <td>{book.title}</td>
                  <td>{book.author}</td>
                  <td>{book.subject}</td>
                  <td>
                    <span className={book.availableCopies > 0 ? 'badge badge--green' : 'badge badge--red'}>
                      {book.availableCopies}
                    </span>
                  </td>
                  <td>{book.waitlistSize}</td>
                  {isStudent && (
                    <td>
                      <button
                        className="btn btn--primary"
                        onClick={() => handleRequest(book.id)}
                      >
                        {canRequest ? 'Request' : 'Join waitlist'}
                      </button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
