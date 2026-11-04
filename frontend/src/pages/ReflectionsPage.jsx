import React, { useState, useEffect, useCallback } from 'react';
import { reflectionsByStatus, approveReflection, rejectReflection } from '../api';
import Toast from '../components/Toast';

const fmtDT = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export default function ReflectionsPage() {
  const [tab, setTab] = useState('PENDING');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [open, setOpen] = useState(null);
  const [note, setNote] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    reflectionsByStatus(tab)
      .then(setItems)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [tab]);

  useEffect(() => { setOpen(null); setNote(''); load(); }, [load]);

  const notify = (payload) => setToast(payload);

  const doApprove = async (id) => {
    setBusy(id);
    try {
      await approveReflection(id);
      notify({ message: 'Approved — +20 points awarded to the student.', type: 'success' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const doReject = async (id) => {
    if (!note.trim()) {
      setNoteError(true);
      notify({ message: 'A review note is required when rejecting.', type: 'error' });
      return;
    }
    setBusy(id);
    try {
      await rejectReflection(id, note.trim());
      notify({ message: 'Reflection rejected with your note.', type: 'success' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const [noteError, setNoteError] = useState(false);

  return (
    <div className="page">
      <h1>Reading reflections</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="tabs">
        {[['PENDING', 'Pending'], ['APPROVED', 'Approved'], ['REJECTED', 'Rejected']].map(([id, label]) => (
          <button key={id} className={`tab ${tab === id ? 'tab--on' : ''}`} onClick={() => setTab(id)}>{label}</button>
        ))}
      </div>

      {loading ? <div className="loading">Loading…</div> : items.length === 0
        ? <p className="empty-state">Nothing here yet.</p> : (
        <ul className="reflection-list">
          {items.map((r) => (
            <li key={r.id} className="reflection-card">
              <div className="reflection-card__head">
                <strong>{r.studentName}</strong> on <strong>{r.bookTitle}</strong>
                <span className="page-subtitle">{fmtDT(r.submittedAt)}</span>
              </div>
              <div className="reflection-card__preview">
                {open === r.id ? r.content : (r.content.length > 160 ? `${r.content.slice(0, 160)}…` : r.content)}
              </div>
              {r.status === 'PENDING' && (
                <>
                  <button className="link-button" onClick={() => setOpen(open === r.id ? null : r.id)}>
                    {open === r.id ? 'Show less' : 'Show full text'}
                  </button>
                  <div className="reflection-card__actions">
                    <button className="btn btn--success" disabled={busy === r.id} onClick={() => doApprove(r.id)}>
                      Approve (+20 pts)
                    </button>
                    {open === r.id && (
                      <div className="reject-box">
                        <textarea
                          rows={2}
                          placeholder="Required review note when rejecting…"
                          value={note}
                          className={noteError && !note.trim() ? 'input-error' : ''}
                          onChange={(e) => { setNote(e.target.value); setNoteError(false); }}
                        />
                        <button className="btn btn--danger" disabled={busy === r.id} onClick={() => doReject(r.id)}>
                          Reject
                        </button>
                      </div>
                    )}
                  </div>
                </>
              )}
              {r.status !== 'PENDING' && r.reviewNote && (
                <div className="cell-detail">Librarian note: “{r.reviewNote}”</div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
