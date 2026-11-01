import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getLoansByStudent, cancelLoan } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';
import Countdown from '../components/Countdown';

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

function statusColor(status) {
  switch (status) {
    case 'READY_FOR_PICKUP': return 'blue';
    case 'ISSUED': return 'green';
    case 'OVERDUE': return 'red';
    case 'FINE_PENDING': return 'orange';
    case 'WAITLISTED': return 'gray';
    default: return 'gray';
  }
}

const ACTIVE = ['READY_FOR_PICKUP', 'ISSUED', 'OVERDUE', 'FINE_PENDING'];
const HISTORY = ['RETURNED', 'CLOSED', 'EXPIRED', 'CANCELLED'];

export default function MyLoansPage() {
  const { currentUser } = useUser();
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  const fetchLoans = useCallback(() => {
    if (!currentUser) return;
    setLoading(true);
    getLoansByStudent(currentUser.id)
      .then(setLoans)
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [currentUser]);

  useEffect(() => { fetchLoans(); }, [fetchLoans]);

  const handleCancel = async (loanId) => {
    try {
      const result = await cancelLoan(loanId, currentUser.id);
      setToast({ message: result.message || 'Request cancelled.', type: 'success' });
      fetchLoans();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  if (!currentUser || currentUser.role !== 'STUDENT') {
    return <div className="page"><p>This page is for students only.</p></div>;
  }

  const active = loans.filter((l) => ACTIVE.includes(l.status));
  const waitlisted = loans.filter((l) => l.status === 'WAITLISTED');
  const history = loans.filter((l) => HISTORY.includes(l.status));

  const loanDetail = (loan) => {
    switch (loan.status) {
      case 'READY_FOR_PICKUP':
        return <>Pick up by {formatDateTime(loan.pickupDeadline)} (<Countdown deadline={loan.pickupDeadline} />)</>;
      case 'ISSUED':
        return <>Due {formatDate(loan.dueDate)}</>;
      case 'OVERDUE':
        return <>Was due {formatDate(loan.dueDate)} — <strong className="text-red">fine ₹{loan.accruedFine}</strong> and counting</>;
      case 'FINE_PENDING':
        return <>Late fee ₹{loan.fine?.amount} pending — <Link to="/my-fines">pay it</Link></>;
      case 'WAITLISTED':
        return <>Queue position #{loan.queuePosition}</>;
      default:
        return null;
    }
  };

  const renderRow = (loan, canCancel) => (
    <tr key={loan.id}>
      <td>{loan.bookTitle}</td>
      <td><span className={`badge badge--${statusColor(loan.status)}`}>{loan.status}</span></td>
      <td className="cell-detail">{loanDetail(loan)}</td>
      <td>
        {canCancel && (
          <button className="btn btn--danger" onClick={() => handleCancel(loan.id)}>
            Cancel
          </button>
        )}
      </td>
    </tr>
  );

  return (
    <div className="page">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />
      <h1>My Loans</h1>
      {loading ? (
        <p className="loading">Loading…</p>
      ) : loans.length === 0 ? (
        <p>No loans yet. Browse the <Link to="/books">catalogue</Link> to request a book.</p>
      ) : (
        <>
          <section className="section">
            <h2>Active ({active.length})</h2>
            {active.length === 0 ? <p>No active loans.</p> : (
              <table className="table">
                <thead><tr><th>Book</th><th>Status</th><th>Details</th><th></th></tr></thead>
                <tbody>{active.map((l) => renderRow(l, l.status === 'READY_FOR_PICKUP'))}</tbody>
              </table>
            )}
          </section>

          <section className="section">
            <h2>Waitlisted ({waitlisted.length})</h2>
            {waitlisted.length === 0 ? <p>Not waiting on any book.</p> : (
              <table className="table">
                <thead><tr><th>Book</th><th>Status</th><th>Details</th><th></th></tr></thead>
                <tbody>{waitlisted.map((l) => renderRow(l, true))}</tbody>
              </table>
            )}
          </section>

          <section className="section">
            <h2>History ({history.length})</h2>
            {history.length === 0 ? <p>No past loans.</p> : (
              <table className="table">
                <thead><tr><th>Book</th><th>Status</th><th>Details</th><th></th></tr></thead>
                <tbody>{history.map((l) => renderRow(l, false))}</tbody>
              </table>
            )}
          </section>
        </>
      )}
    </div>
  );
}
