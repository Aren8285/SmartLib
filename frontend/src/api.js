const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || `Request failed: ${res.status}`);
  }
  return data;
}

// Users
export const getUsers = () => request('/users');

// Books
export const getBooks = (query = '') =>
  request(`/books${query ? `?query=${encodeURIComponent(query)}` : ''}`);

// Loans
export const requestLoan = (studentId, bookId) =>
  request('/loans/request', {
    method: 'POST',
    body: JSON.stringify({ studentId, bookId }),
  });

export const getLoansByStudent = (studentId) =>
  request(`/loans?studentId=${studentId}`);

export const getLoansByStatus = (status) =>
  request(`/loans?status=${status}`);

export const issueLoan = (loanId, librarianId) =>
  request(`/loans/${loanId}/issue`, {
    method: 'POST',
    body: JSON.stringify({ librarianId }),
  });

export const returnLoan = (loanId, librarianId) =>
  request(`/loans/${loanId}/return`, {
    method: 'POST',
    body: JSON.stringify({ librarianId }),
  });

export const cancelLoan = (loanId, studentId) =>
  request(`/loans/${loanId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ studentId }),
  });

