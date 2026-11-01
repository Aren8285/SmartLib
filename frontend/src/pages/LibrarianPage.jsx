import React, { useState, useEffect, useCallback } from 'react';
import {
  getLoansByStatus, getFines, getBooks, getWaitlistForBook,
  issueLoan, returnLoan, lostLoan,
  devSetDueDate, devSetPickupDeadline, devRunJobs,
} from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    year: 'numeric', month: 'short', day: 'numeric'
  });
}

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

const DEV_TOOLS_ENABLED = import.meta.env.VITE_DEV_TOOLS === 'true';

export default function LibrarianPage() {
  const { currentUser } = useUser();
  const [ready, setReady] = useState([]);
  const [issued, setIssued] = useState([]);
  const [fines, setFines] = useState([]);
  const [books, setBooks] = useState([]);
  const [waitlistBookId, setWaitlistBookId] = useState('');
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  // Dev tools state
  const [devOpen, setDevOpen] = useState(false);
  const [devLoanId, setDevLoanId] = useState('');
  const [devDays, setDevDays] = useState('-2');
  const [devMinutes, setDevMinutes] = useState('-1');

  const fetchAll = useCallback(() => {
    if (!currentUser) return;
    setLoading(true);
    Promise.all([
      getLoansByStatus('READY_FOR_PICKUP'),
      getLoansByStatus('ISSUED'),
      getLoansByStatus('OVERDUE'),
      getFines({ status: 'PENDING' }),
      getBooks(),
    ])
      .then(([r, iss, ovd, f, b]) => {
        setReady(r);
        setIssued([...iss, ...ovd]);
        setFines(f);
        setBooks(b);
      })
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [currentUser]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const fetchQueue = useCallback((bookId) => {
    setWaitlistBookId(bookId);
    if (!bookId) { setQueue([]); return; }
    getWaitlistForBook(bookId).then(setQueue).catch((e) => setToast({ message: e.message, type: 'error' }));
  }, []);

  const handleIssue = async (loanId) => {
    try {
      const result = await issueLoan(loanId, currentUser.id);
      setToast({ message: result.message || 'Book issued.', type: 'success' });
      fetchAll();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  const handleReturn = async (loanId) => {
    try {
      const result = await returnLoan(loanId, currentUser.id);
      setToast({ message: result.message || 'Book returned.', type: 'success' });
      fetchAll();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  const handleLost = async (loanId) => {
    if (!window.confirm('Mark this copy as LOST? A ₹500 fine will be created and the copy is not returned to the shelf.')) return;
    try {
      const result = await lostLoan(loanId, currentUser.id);
      setToast({ message: result.message || 'Marked as lost.', type: 'success' });
      fetchAll();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  const runDev = async (fn, successMsg) => {
    try {
      const result = await fn();
      setToast({ message: typeof result === 'object' && result.message ? result.message : successMsg, type: 'success' });
      fetchAll();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  if (!currentUser || currentUser.role !== 'LIBRARIAN') {
    return <div className="page"><p>This page is for librarians only.</p></div>;
  }

  return (
    <div className="page">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />
      <h1>Librarian Dashboard</h1>

      <section className="section">
        <h2>Ready for pickup ({ready.length})</h2>
        {loading ? <p className="loading">Loading…</p> : ready.length === 0 ? (
          <p>No copies waiting for pickup.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Book</th><th>Student</th><th>Pickup deadline</th><th>Action</th></tr>
            </thead>
            <tbody>
              {ready.map((loan) => (
                <tr key={loan.id}>
                  <td>{loan.bookTitle}</td>
                  <td>{loan.studentName}</td>
                  <td>{formatDateTime(loan.pickupDeadline)}</td>
                  <td>
                    <button className="btn btn--primary" onClick={() => handleIssue(loan.id)}>
                      Issue
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="section">
        <h2>Currently issued / overdue ({issued.length})</h2>
        {loading ? <p className="loading">Loading…</p> : issued.length === 0 ? (
          <p>No books currently issued.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Book</th><th>Student</th><th>Status</th><th>Due</th><th>Late fine</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {issued.map((loan) => (
                <tr key={loan.id}>
                  <td>{loan.bookTitle}</td>
                  <td>{loan.studentName}</td>
                  <td><span className={`badge badge--${loan.status === 'OVERDUE' ? 'red' : 'green'}`}>{loan.status}</span></td>
                  <td>{formatDate(loan.dueDate)}</td>
                  <td>{loan.status === 'OVERDUE' ? <span className="text-red">₹{loan.accruedFine}</span> : '—'}</td>
                  <td>
                    <button className="btn btn--success" onClick={() => handleReturn(loan.id)}>
                      Mark returned
                    </button>{' '}
                    <button className="btn btn--danger" onClick={() => handleLost(loan.id)}>
                      Mark lost
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="section">
        <h2>Pending fines ({fines.length})</h2>
        {loading ? <p className="loading">Loading…</p> : fines.length === 0 ? (
          <p>No pending fines.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Student</th><th>Reason</th><th>Amount</th><th>Issued</th></tr>
            </thead>
            <tbody>
              {fines.map((fine) => (
                <tr key={fine.id}>
                  <td>{fine.studentName}</td>
                  <td>{fine.reason.replace('_', ' ')}</td>
                  <td>₹{fine.amount}</td>
                  <td>{formatDate(fine.issueDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="section">
        <h2>Waitlists</h2>
        <div className="filter-bar">
          <select value={waitlistBookId} onChange={(e) => fetchQueue(e.target.value)}>
            <option value="">Pick a book…</option>
            {books.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title} (queue: {b.waitlistSize})
              </option>
            ))}
          </select>
        </div>
        {waitlistBookId && (
          queue.length === 0 ? (
            <p>Nobody waiting for this book.</p>
          ) : (
            <table className="table">
              <thead>
                <tr><th>#</th><th>Student</th><th>Joined</th></tr>
              </thead>
              <tbody>
                {queue.map((entry) => (
                  <tr key={entry.entryId}>
                    <td>{entry.position}</td>
                    <td>{entry.studentName}</td>
                    <td>{formatDateTime(entry.joinedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        )}
      </section>

      {DEV_TOOLS_ENABLED && (
        <section className="section">
          <h2>
            <button className="link-button" onClick={() => setDevOpen(!devOpen)}>
              {devOpen ? '▾' : '▸'} Dev tools
            </button>
          </h2>
          {devOpen && (
            <div className="dev-panel">
              <div className="dev-panel__row">
                <select value={devLoanId} onChange={(e) => setDevLoanId(e.target.value)}>
                  <option value="">Pick a loan…</option>
                  {[...ready, ...issued].map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.bookTitle} — {l.studentName} ({l.status})
                    </option>
                  ))}
                </select>
              </div>
              <div className="dev-panel__row">
                <input
                  type="number" style={{ width: 80 }} value={devDays}
                  onChange={(e) => setDevDays(e.target.value)}
                />
                <span>days from now</span>
                <button
                  className="btn btn--primary"
                  disabled={!devLoanId}
                  onClick={() => runDev(() => devSetDueDate(devLoanId, Number(devDays)), 'Due date shifted.')}
                >
                  Set due date
                </button>
                <input
                  type="number" style={{ width: 80 }} value={devMinutes}
                  onChange={(e) => setDevMinutes(e.target.value)}
                />
                <span>min from now</span>
                <button
                  className="btn btn--primary"
                  disabled={!devLoanId}
                  onClick={() => runDev(() => devSetPickupDeadline(devLoanId, Number(devMinutes)), 'Pickup deadline shifted.')}
                >
                  Set pickup deadline
                </button>
              </div>
              <div className="dev-panel__row">
                <button className="btn" onClick={() => runDev(devRunJobs, 'Jobs executed.')}>
                  Run jobs now (expiry + overdue)
                </button>
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
