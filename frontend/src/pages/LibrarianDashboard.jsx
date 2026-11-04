import React, { useState, useEffect } from 'react';
import { librarianDashboard } from '../api';

const fmtDT = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export default function LibrarianDashboard() {
  const [dash, setDash] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    librarianDashboard().then(setDash).catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="banner banner--warning">{error}</div>;
  if (!dash) return <div className="loading">Loading…</div>;

  const cards = [
    ['Ready for pickup', dash.readyForPickup, dash.readyForPickup > 0 ? 'blue' : 'gray'],
    ['Issued', dash.issued, 'green'],
    ['Overdue', dash.overdue, dash.overdue > 0 ? 'red' : 'gray'],
    ['Pending reflections', dash.pendingReflections, dash.pendingReflections > 0 ? 'yellow' : 'gray'],
    ['Pending fines', dash.pendingFines, dash.pendingFines > 0 ? 'orange' : 'gray'],
    ['Books', dash.totalBooks, 'gray'],
    ['Copies', dash.totalCopies, 'gray'],
  ];

  const lineFor = (a) => {
    switch (a.status) {
      case 'READY_FOR_PICKUP': return `Pickup deadline ${fmtDT(a.pickupDeadline)} (cancels if missed)`;
      case 'OVERDUE': return `Was due ${fmtDT(a.dueDate)} — overdue`;
      default: return `Due ${fmtDT(a.dueDate)}`;
    }
  };

  return (
    <div className="page">
      <h1>Librarian dashboard</h1>
      <div className="cards">
        {cards.map(([label, value, tone]) => (
          <div className="card" key={label}>
            <div className={`card__num card__num--${tone}`}>{value}</div>
            <div className="card__label">{label}</div>
          </div>
        ))}
      </div>

      <section className="section">
        <h2>Circulation overview</h2>
        <table className="table">
          <thead><tr><th>Copy status</th><th>Count</th></tr></thead>
          <tbody>
            {Object.entries(dash.copiesByStatus || {}).map(([status, count]) => (
              <tr key={status}>
                <td>{status}</td>
                <td>{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="section">
        <h2>Oldest loans needing action</h2>
        {(dash.oldestActionsNeeded || []).length === 0 && <p className="empty-state">All clear! ✨</p>}
        {(dash.oldestActionsNeeded || []).length > 0 && (
          <table className="table">
            <thead><tr><th>Book</th><th>Status</th><th>Why it matters</th></tr></thead>
            <tbody>
              {dash.oldestActionsNeeded.map((a) => (
                <tr key={a.loanId}>
                  <td>{a.bookTitle}</td>
                  <td><span className={`badge ${a.status === 'OVERDUE' || a.status === 'FINE_PENDING' ? 'badge--red' : 'badge--blue'}`}>{(a.status || '').replace(/_/g, ' ')}</span></td>
                  <td>{lineFor(a)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
