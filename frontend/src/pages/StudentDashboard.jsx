import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { studentDashboard, getRecommendations, requestLoan } from '../api';
import Toast from '../components/Toast';
import Countdown from '../components/Countdown';

function fmt(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function StudentDashboard() {
  const [dash, setDash] = useState(null);
  const [recs, setRecs] = useState([]);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(() => {
    studentDashboard().then(setDash).catch((e) => setError(e.message));
    getRecommendations().then(setRecs).catch((e) => setError(e.message));
  }, []);

  useEffect(() => { load(); }, [load]);

  const request = async (bookId, action) => {
    setBusy(bookId);
    try {
      const res = await requestLoan(bookId);
      setToast({ message: res.message || 'Requested.', type: 'success' });
      load();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  if (error) return <div className="banner banner--warning">{error}</div>;
  if (!dash) return <div className="loading">Loading…</div>;

  const nextDue = dash.nextDueDate;

  return (
    <div className="page">
      <h1>My dashboard</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="cards">
        <div className="card"><div className="card__num">{dash.points}</div><div className="card__label">points</div></div>
        <div className="card"><div className="card__num">{dash.activeLoans}</div><div className="card__label">active loans</div></div>
        <div className="card"><div className="card__num">{dash.waitlisted}</div><div className="card__label">in queue</div></div>
        <div className="card">
          <div className={`card__num ${dash.pendingFinesAmount > 0 ? 'text-red' : ''}`}>₹{dash.pendingFinesAmount || 0}</div>
          <div className="card__label">fines due</div>
        </div>
      </div>

      <section className="section">
        <h2>Action needed</h2>
        {dash.actionsNeeded.length === 0 && <p className="empty-state">Nothing here yet — all caught up!</p>}
        <ul className="action-list">
          {dash.actionsNeeded.map((a) => (
            <li key={a.loanId} className="action-item">
              <span className="badge badge--blue">{a.status.replace(/_/g, ' ')}</span>
              <strong>{a.bookTitle}</strong>
              <span>{a.action}</span>
              {a.pickupDeadline && <span>Deadline: <Countdown deadline={a.pickupDeadline} /> ({fmt(a.pickupDeadline)})</span>}
            </li>
          ))}
        </ul>
      </section>

      {nextDue && (
        <section className="section">
          <h2>Next due date</h2>
          <p>{fmt(nextDue)} — make sure to return on time to keep your points.</p>
        </section>
      )}

      <section className="section">
        <h2>Recommended for you</h2>
        {recs.length === 0 && <p className="empty-state">Nothing here yet — borrow a book to unlock recommendations.</p>}
        <div className="rec-grid">
          {recs.map((r) => (
            <div key={r.book.id} className="rec-card">
              <div className="rec-card__title">{r.book.title}</div>
              <div className="rec-card__author">{r.book.author}</div>
              <div className="rec-card__reason">{r.reason}</div>
              <div className="rec-card__meta">
                {r.book.availableCopies > 0 ? `${r.book.availableCopies} available` : 'No copies free'}
                {r.book.waitlistSize > 0 && ` · ${r.book.waitlistSize} waiting`}
              </div>
              <button
                className="btn btn--primary"
                disabled={busy === r.book.id}
                onClick={() => request(r.book.id)}
              >
                {busy === r.book.id ? 'Requesting…' : r.book.availableCopies > 0 ? 'Request' : 'Join waitlist'}
              </button>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
