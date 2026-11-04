import React, { useState, useEffect, useCallback } from 'react';
import { getBooks, getSubjects, requestLoan } from '../api';
import Toast from '../components/Toast';

export default function BooksPage() {
  const [books, setBooks] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [filters, setFilters] = useState({ query: '', subject: '', availableOnly: false });
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(null);

  const fetchBooks = useCallback(() => {
    setLoading(true);
    getBooks(filters)
      .then(setBooks)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [filters]);

  useEffect(() => { getSubjects().then(setSubjects).catch(() => {}); }, []);
  useEffect(() => {
    const t = setTimeout(fetchBooks, 250); // debounce while typing
    return () => clearTimeout(t);
  }, [fetchBooks]);

  const request = async (book) => {
    setBusy(book.id);
    try {
      const res = await requestLoan(book.id);
      setToast({ message: res.message || 'Requested.', type: 'success' });
      fetchBooks();
      setExpanded(null);
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="page">
      <h1>Books</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="filter-bar">
        <input
          className="search-bar__input"
          placeholder="Search title, author, subject…"
          value={filters.query}
          onChange={(e) => setFilters({ ...filters, query: e.target.value })}
        />
        <select
          value={filters.subject}
          onChange={(e) => setFilters({ ...filters, subject: e.target.value })}
        >
          <option value="">All subjects</option>
          {subjects.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={filters.availableOnly}
            onChange={(e) => setFilters({ ...filters, availableOnly: e.target.checked })}
          />
          Available only
        </label>
      </div>

      {loading ? <div className="loading">Loading…</div> : (
        <>
          {books.length === 0 && <p className="empty-state">Nothing here yet — try a different search.</p>}
          <table className="table">
            <thead>
              <tr>
                <th>Title</th><th>Author</th><th>Subject</th><th>Available</th><th>Queue</th><th></th>
              </tr>
            </thead>
            <tbody>
              {books.map((b) => (
                <React.Fragment key={b.id}>
                  <tr>
                    <td>
                      <button className="link-button" onClick={() => setExpanded(expanded === b.id ? null : b.id)}>
                        {b.title}
                      </button>
                    </td>
                    <td>{b.author}</td>
                    <td>{b.subject}</td>
                    <td className={b.availableCopies === 0 ? 'text-red' : ''}>{b.availableCopies}</td>
                    <td>{b.waitlistSize > 0 ? `${b.waitlistSize} waiting` : '—'}</td>
                    <td>
                      <button
                        className="btn btn--primary"
                        disabled={busy === b.id}
                        onClick={() => request(b)}
                      >
                        {busy === b.id ? '…' : b.availableCopies > 0 ? 'Request' : 'Join waitlist'}
                      </button>
                    </td>
                  </tr>
                  {expanded === b.id && (
                    <tr className="detail-row">
                      <td colSpan={6}>
                        <div className="cell-detail">
                          ISBN: {b.isbn || '—'} · {b.availableCopies} of the copies are on the shelf.
                          {b.waitlistSize > 0 && ` ${b.waitlistSize} students waiting for it.`}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
