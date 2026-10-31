import React, { useState, useEffect, useCallback } from 'react';
import { getLoansByStudent, cancelLoan } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    year: 'numeric', month: 'short', day: 'numeric'
  });
}

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
      await cancelLoan(loanId, currentUser.id);
      setToast({ message: 'Loan cancelled.', type: 'success' });
      fetchLoans();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  if (!currentUser || currentUser.role !== 'STUDENT') {
    return <div className="page"><p>This page is for students only.</p></div>;
  }

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
        <p>No loans found.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Book</th>
              <th>Status</th>
              <th>Requested</th>
              <th>Issued</th>
              <th>Due</th>
              <th>Returned</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {loans.map((loan) => (
              <tr key={loan.id}>
                <td>{loan.bookTitle}</td>
                <td><span className={`badge badge--${statusColor(loan.status)}`}>{loan.status}</span></td>
                <td>{formatDate(loan.requestDate)}</td>
                <td>{formatDate(loan.issueDate)}</td>
                <td>{formatDate(loan.dueDate)}</td>
                <td>{formatDate(loan.returnDate)}</td>
                <td>
                  {loan.status === 'REQUESTED' && (
                    <button
                      className="btn btn--danger"
                      onClick={() => handleCancel(loan.id)}
                    >
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function statusColor(status) {
  switch (status) {
    case 'REQUESTED': return 'yellow';
    case 'ISSUED': return 'blue';
    case 'RETURNED': return 'green';
    case 'CANCELLED': return 'gray';
    default: return 'gray';
  }
}

