import React, { useState, useCallback } from 'react';
import { getBooks, getWaitlistForBook } from '../api';
import Toast from '../components/Toast';

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export default function WaitlistsPage() {
  const [books, setBooks] = useState([]);
  const [selected, setSelected] = useState('');
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  React.useEffect(() => {
    getBooks({ availableOnly: false }).then(setBooks).catch((e) => setError(e.message));
  }, []);

  const pick = useCallback(async (bookId) => {
    setSelected(bookId);
    if (!bookId) { setQueue([]); return; }
    setLoading(true);
    setError('');
    try {
      setQueue(await getWaitlistForBook(bookId));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <div className="page">
      <h1>Waitlists</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="filter-bar">
        <select value={selected} onChange={(e) => pick(e.target.value)}>
          <option value="">Select a book…</option>
          {books.map((b) => (
            <option key={b.id} value={b.id}>
              {b.title} — {b.waitlistSize > 0 ? `${b.waitlistSize} waiting` : 'no queue'}
            </option>
          ))}
        </select>
      </div>

      {loading ? <div className="loading">Loading…</div> : selected && (
        queue.length === 0
          ? <p className="empty-state">Nobody is on the waitlist for this book.</p>
          : <table className="table">
              <thead><tr><th>Position</th><th>Student</th><th>Status</th><th>Joined</th><th></th></tr></thead>
              <tbody>
                {queue.map((w, i) => (
                  <tr key={w.id}>
                    <td>#{i + 1}</td>
                    <td>{w.studentName}</td>
                    <td>
                      <span className={`badge ${w.status === 'READY_FOR_PICKUP' ? 'badge--blue' : w.status === 'EXPIRED' ? 'badge--gray' : 'badge--yellow'}`}>
                        {w.status.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td>{fmtDate(w.joinedAt)}</td>
                    <td>
                      {w.status === 'READY_FOR_PICKUP' && w.pickupDeadline && (
                        <span className="page-subtitle">deadline {fmtDate(w.pickupDeadline)}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
      )}
      {!selected && <p className="empty-state">Choosing a book shows who is waiting and why.</p>}
    </div>
  );
}
