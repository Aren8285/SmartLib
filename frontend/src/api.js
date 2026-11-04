const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
const TOKEN_KEY = 'smartlib_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

/** Raised after any 401 so the AuthContext can clear the session and send the user to login. */
export function onUnauthorized(handler) {
  window.addEventListener('smartlib:unauthorized', handler);
  return () => window.removeEventListener('smartlib:unauthorized', handler);
}

async function request(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  let data = await res.json().catch(() => ({}));

  if (res.status === 401) {
    setToken(null);
    window.dispatchEvent(new CustomEvent('smartlib:unauthorized', {
      detail: data.message || 'Session expired - please log in again.',
    }));
    throw new Error(data.message || 'Not logged in.');
  }
  if (!res.ok) {
    throw new Error(data.message || `Request failed: ${res.status}`);
  }
  return data;
}

// ── Auth ──
export const register = (body) => request('/auth/register', { method: 'POST', body: JSON.stringify(body) });
export const login = (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) });
export const me = () => request('/auth/me');

// ── Users ──
export const updateMe = (body) => request('/users/me', { method: 'PUT', body: JSON.stringify(body) });
export const createLibrarian = (body) => request('/users/librarian', { method: 'POST', body: JSON.stringify(body) });
export const getAllUsers = () => request('/users');

// ── Books & copies ──
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
export const createBook = (body) => request('/books', { method: 'POST', body: JSON.stringify(body) });
export const updateBook = (id, body) => request(`/books/${id}`, { method: 'PUT', body: JSON.stringify(body) });
export const deleteBook = (id) => request(`/books/${id}`, { method: 'DELETE' });
export const getCopies = (bookId) => request(`/books/${bookId}/copies`);
export const addCopies = (bookId, body) => request(`/books/${bookId}/copies`, { method: 'POST', body: JSON.stringify(body) });
export const updateCopyStatus = (copyId, body) =>
  request(`/copies/${copyId}/status`, { method: 'PATCH', body: JSON.stringify(body) });

// ── Loans ──
export const requestLoan = (bookId) =>
  request('/loans/request', { method: 'POST', body: JSON.stringify({ bookId }) });
export const getMyLoans = () => request('/loans?studentId=me');
export const getLoansByStatus = (status) => request(`/loans?status=${status}`);
export const issueLoan = (loanId) => request(`/loans/${loanId}/issue`, { method: 'POST' });
export const returnLoan = (loanId, bookCondition) =>
  request(`/loans/${loanId}/return`, {
    method: 'POST',
    body: JSON.stringify(bookCondition ? { bookCondition } : {}),
  });
export const lostLoan = (loanId) => request(`/loans/${loanId}/lost`, { method: 'POST' });
export const cancelLoan = (loanId) => request(`/loans/${loanId}/cancel`, { method: 'POST' });
export const skipReflection = (loanId) => request(`/loans/${loanId}/skip-reflection`, { method: 'POST' });

// ── Reflections ──
export const submitReflection = (loanId, content) =>
  request('/reflections', { method: 'POST', body: JSON.stringify({ loanId, content }) });
export const myReflections = () => request('/reflections/mine');
export const reflectionsByStatus = (status) => request(`/reflections?status=${status}`);
export const approveReflection = (id) => request(`/reflections/${id}/approve`, { method: 'POST' });
export const rejectReflection = (id, reviewNote) =>
  request(`/reflections/${id}/reject`, { method: 'POST', body: JSON.stringify({ reviewNote }) });

// ── Fines ──
export const getMyFines = () => request('/fines?studentId=me');
export const getFinesByStatus = (status) => request(`/fines?status=${status}`);
export const payFine = (fineId) => request(`/fines/${fineId}/pay`, { method: 'POST' });

// ── Waitlist ──
export const getWaitlistForBook = (bookId) => request(`/waitlist/book/${bookId}`);

// ── Notifications ──
export const getNotifications = () => request('/notifications');
export const unreadCount = () => request('/notifications/unread-count');
export const markNotificationRead = (id) => request(`/notifications/${id}/read`, { method: 'POST' });
export const markAllNotificationsRead = () => request('/notifications/read-all', { method: 'POST' });

// ── Recommendations & dashboards ──
export const getRecommendations = () => request('/recommendations');
export const studentDashboard = () => request('/dashboard/student');
export const librarianDashboard = () => request('/dashboard/librarian');

// ── Dev tools (dev profile + librarian) ──
export const devSetDueDate = (loanId, daysFromNow) =>
  request(`/dev/loans/${loanId}/set-due-date?daysFromNow=${daysFromNow}`, { method: 'POST' });
export const devSetPickupDeadline = (loanId, minutesFromNow) =>
  request(`/dev/loans/${loanId}/set-pickup-deadline?minutesFromNow=${minutesFromNow}`, { method: 'POST' });
export const devRunJobs = () => request('/dev/run-jobs', { method: 'POST' });
