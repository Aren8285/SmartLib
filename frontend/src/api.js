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
export const getUser = (id) => request(`/users/${id}`);

// Books
export function getBooks({ query = '', subject = '', availableOnly = false } = {}) {
  const params = new URLSearchParams();
  if (query) params.set('query', query);
  if (subject) params.set('subject', subject);
  if (availableOnly) params.set('availableOnly', 'true');
  const qs = params.toString();
  return request(`/books${qs ? `?${qs}` : ''}`);
}

export const getBook = (id) => request(`/books/${id}`);
export const getSubjects = () => request('/books/subjects');

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

export const lostLoan = (loanId, librarianId) =>
  request(`/loans/${loanId}/lost`, {
    method: 'POST',
    body: JSON.stringify({ librarianId }),
  });

export const cancelLoan = (loanId, studentId) =>
  request(`/loans/${loanId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ studentId }),
  });

// Fines
export const getFines = ({ studentId = '', status = '' } = {}) => {
  const params = new URLSearchParams();
  if (studentId) params.set('studentId', studentId);
  if (status) params.set('status', status);
  const qs = params.toString();
  return request(`/fines${qs ? `?${qs}` : ''}`);
};

export const payFine = (fineId, studentId) =>
  request(`/fines/${fineId}/pay`, {
    method: 'POST',
    body: JSON.stringify({ studentId }),
  });

// Waitlist
export const getWaitlistForBook = (bookId) => request(`/waitlist/book/${bookId}`);

// Dev tools (Spring profile "dev" only)
export const devSetDueDate = (loanId, daysFromNow) =>
  request(`/dev/loans/${loanId}/set-due-date?daysFromNow=${daysFromNow}`, { method: 'POST' });

export const devSetPickupDeadline = (loanId, minutesFromNow) =>
  request(`/dev/loans/${loanId}/set-pickup-deadline?minutesFromNow=${minutesFromNow}`, { method: 'POST' });

export const devRunJobs = () => request('/dev/run-jobs', { method: 'POST' });
