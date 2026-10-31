import React, { useState, useEffect, useCallback } from 'react';
import { getBooks, requestLoan } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

export default function BooksPage() {
  const { currentUser } = useUser();
  const [books, setBooks] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  const fetchBooks = useCallback(() => {
    setLoading(true);
    getBooks(query)
      .then(setBooks)
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [query]);

  useEffect(() => {
    fetchBooks();
  }, [fetchBooks]);

  const handleRequest = async (bookId) => {
    if (!currentUser) {
      setToast({ message: 'Please select a user from the navbar first.', type: 'error' });
      return;
    }
    try {
      await requestLoan(currentUser.id, bookId);
      setToast({ message: 'Loan requested successfully!', type: 'success' });
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
      <div className="search-bar">
        <input
          type="text"
          placeholder="Search by title, author or subject…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
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
              {currentUser?.role === 'STUDENT' && <th>Action</th>}
            </tr>
          </thead>
          <tbody>
            {books.map((book) => (
              <tr key={book.id}>
                <td>{book.title}</td>
                <td>{book.author}</td>
                <td>{book.subject}</td>
                <td>
                  <span className={book.availableCopies > 0 ? 'badge badge--green' : 'badge badge--red'}>
                    {book.availableCopies}
                  </span>
                </td>
                {currentUser?.role === 'STUDENT' && (
                  <td>
                    {book.availableCopies > 0 ? (
                      <button
                        className="btn btn--primary"
                        onClick={() => handleRequest(book.id)}
                      >
                        Request
                      </button>
                    ) : (
                      <button className="btn" disabled>
                        Unavailable
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

