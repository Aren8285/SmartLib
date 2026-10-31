import React, { useState, useEffect, useCallback } from 'react';
import { getLoansByStatus, issueLoan, returnLoan } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    year: 'numeric', month: 'short', day: 'numeric'
  });
}

export default function LibrarianPage() {
  const { currentUser } = useUser();
  const [requested, setRequested] = useState([]);
  const [issued, setIssuedLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  const fetchAll = useCallback(() => {
    if (!currentUser) return;
    setLoading(true);
    Promise.all([
      getLoansByStatus('REQUESTED'),
      getLoansByStatus('ISSUED'),
    ])
      .then(([req, iss]) => {
        setRequested(req);
        setIssuedLoans(iss);
      })
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [currentUser]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const handleIssue = async (loanId) => {
    try {
      await issueLoan(loanId, currentUser.id);
      setToast({ message: 'Book issued successfully!', type: 'success' });
      fetchAll();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  const handleReturn = async (loanId) => {
    try {
      await returnLoan(loanId, currentUser.id);
      setToast({ message: 'Book marked as returned.', type: 'success' });
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
        <h2>Pending Requests ({requested.length})</h2>
        {loading ? <p className="loading">Loading…</p> : requested.length === 0 ? (
          <p>No pending requests.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Book</th>
                <th>Student</th>
                <th>Requested</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {requested.map((loan) => (
                <tr key={loan.id}>
                  <td>{loan.bookTitle}</td>
                  <td>{loan.studentName}</td>
                  <td>{formatDate(loan.requestDate)}</td>
                  <td>
                    <button
                      className="btn btn--primary"
                      onClick={() => handleIssue(loan.id)}
                    >
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
        <h2>Issued Books ({issued.length})</h2>
        {loading ? <p className="loading">Loading…</p> : issued.length === 0 ? (
          <p>No books currently issued.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Book</th>
                <th>Student</th>
                <th>Issued</th>
                <th>Due</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {issued.map((loan) => (
                <tr key={loan.id}>
                  <td>{loan.bookTitle}</td>
                  <td>{loan.studentName}</td>
                  <td>{formatDate(loan.issueDate)}</td>
                  <td>{formatDate(loan.dueDate)}</td>
                  <td>
                    <button
                      className="btn btn--success"
                      onClick={() => handleReturn(loan.id)}
                    >
                      Mark Returned
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

