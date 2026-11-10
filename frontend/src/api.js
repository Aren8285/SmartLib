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
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));

  if (res.status === 401) {
    setToken(null);
    window.dispatchEvent(new CustomEvent('smartlib:unauthorized', {
      detail: data.message || 'Session expired - please log in again.',
    }));
    throw new Error(data.message || 'Not logged in.');
  }
  if (!res.ok) {
    const error = new Error(data.message || `Request failed: ${res.status}`);
    error.status = res.status;
    throw error;
  }
  return data;
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
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
export function getBooks({ query: q = '', subject = '', shelf = '', availableOnly = false, page = 0, size = 12, sort = 'title' } = {}) {
  return request(`/books${query({ query: q, subject, shelf, availableOnly: availableOnly || undefined, page, size, sort })}`);
}
export const getBook = (id) => request(`/books/${id}`);
export const getSubjects = () => request('/books/subjects');
export const createBook = (body) => request('/books', { method: 'POST', body: JSON.stringify(body) });
export const updateBook = (id, body) => request(`/books/${id}`, { method: 'PUT', body: JSON.stringify(body) });
export const deleteBook = (id) => request(`/books/${id}`, { method: 'DELETE' });
export const assignBookShelf = (id, shelfId) =>
  request(`/books/${id}/shelf`, { method: 'PATCH', body: JSON.stringify({ shelfId }) });
export const getCopies = (bookId) => request(`/books/${bookId}/copies`);
export const addCopies = (bookId, body) => request(`/books/${bookId}/copies`, { method: 'POST', body: JSON.stringify(body) });
export const updateCopyStatus = (copyId, body) =>
  request(`/copies/${copyId}/status`, { method: 'PATCH', body: JSON.stringify(body) });

// ── Shelves / map ──
export const getShelves = () => request('/shelves');
export const getShelfBooks = (code) => request(`/shelves/${encodeURIComponent(code)}/books`);
export const createShelf = (body) => request('/shelves', { method: 'POST', body: JSON.stringify(body) });
export const updateShelf = (id, body) => request(`/shelves/${id}`, { method: 'PUT', body: JSON.stringify(body) });
export const deleteShelf = (id) => request(`/shelves/${id}`, { method: 'DELETE' });

// ── Loans ──
export const requestLoan = (bookId) =>
  request('/loans/request', { method: 'POST', body: JSON.stringify({ bookId }) });
export const getMyLoans = ({ page = 0, size = 20, status = '' } = {}) =>
  request(`/loans${query({ studentId: 'me', status, page, size })}`);
export const getLoans = ({ studentId = '', status = '', q = '', page = 0, size = 20 } = {}) =>
  request(`/loans${query({ studentId, status, q, page, size })}`);
export const getLoan = (id) => request(`/loans/${id}`);
export const issueLoan = (loanId) => request(`/loans/${loanId}/issue`, { method: 'POST' });
export const returnLoan = (loanId, bookCondition) =>
  request(`/loans/${loanId}/return`, {
    method: 'POST',
    body: JSON.stringify(bookCondition ? { bookCondition } : {}),
  });
export const lostLoan = (loanId) => request(`/loans/${loanId}/lost`, { method: 'POST' });
export const cancelLoan = (loanId) => request(`/loans/${loanId}/cancel`, { method: 'POST' });
export const skipReflection = (loanId) => request(`/loans/${loanId}/skip-reflection`, { method: 'POST' });
export const renewLoan = (loanId) => request(`/loans/${loanId}/renew`, { method: 'POST' });

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
export const getFinesByStatus = (status) => request(status === 'ALL' ? '/fines' : `/fines?status=${status}`);
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

// ── Reports & leaderboard ──
export const getLeaderboard = (limit = 10) => request(`/leaderboard?limit=${limit}`);
export const getReport = (name, { from, to, limit } = {}) =>
  request(`/reports/${name}${query({ from, to, limit })}`);
export const getOverdueReport = () => request('/reports/overdue');
export const getPopularBooks = ({ from, to, limit = 10 } = {}) => getReport('popular-books', { from, to, limit });
export const getCirculation = ({ from, to } = {}) => getReport('circulation', { from, to });
export const getFinesReport = ({ from, to } = {}) => getReport('fines', { from, to });
export const getWaitlistReport = ({ from, to } = {}) => getReport('waitlist', { from, to });
export const getInventoryReport = () => getReport('inventory');

/** CSV can't be a normal <a download> because auth is a header; fetch a blob and click it. */
export async function downloadReportCsv(type, { from, to } = {}) {
  const token = getToken();
  const url = `${BASE_URL}/reports/export${query({ type, from, to })}`;
  const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || `Export failed: ${res.status}`);
  }
  const blob = await res.blob();
  const disposition = res.headers.get('Content-Disposition') || '';
  const match = disposition.match(/filename="([^"]+)"/);
  const filename = match?.[1] || `smartlib-${type}.csv`;
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
  return filename;
}

// ── Dev tools (dev profile + librarian) ──
export const devSetDueDate = (loanId, daysFromNow) =>
  request(`/dev/loans/${loanId}/set-due-date?daysFromNow=${daysFromNow}`, { method: 'POST' });
export const devSetPickupDeadline = (loanId, minutesFromNow) =>
  request(`/dev/loans/${loanId}/set-pickup-deadline?minutesFromNow=${minutesFromNow}`, { method: 'POST' });
export const devRunJobs = () => request('/dev/run-jobs', { method: 'POST' });
export const devInvariants = () => request('/dev/invariants');
export const devRepair = () => request('/dev/repair', { method: 'POST' });
