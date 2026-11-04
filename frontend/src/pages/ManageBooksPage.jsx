import React, { useState, useEffect, useCallback } from 'react';
import {
  getBooks, getSubjects, createBook, updateBook, deleteBook, getCopies, addCopies, updateCopyStatus,
} from '../api';
import Toast from '../components/Toast';

const emptyForm = { title: '', author: '', subject: '', isbn: '' };

export default function ManageBooksPage() {
  const [books, setBooks] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formErr, setFormErr] = useState('');

  const [copiesOf, setCopiesOf] = useState(null); // bookId currently expanded
  const [copies, setCopies] = useState([]);
  const [copiesErr, setCopiesErr] = useState('');
  const [copiesForm, setCopiesForm] = useState({ count: 1, condition: 'GOOD' });
  const [copiesBusy, setCopiesBusy] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    getBooks({ query })
      .then(setBooks)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [query]);

  useEffect(() => {
    const t = setTimeout(load, 250);
    return () => clearTimeout(t);
  }, [load]);

  useEffect(() => { getSubjects().then(setSubjects).catch(() => {}); }, []);

  const notify = (payload) => setToast(payload);

  const openAdd = () => { setEditId(null); setForm(emptyForm); setFormErr(''); setShowForm(true); };
  const openEdit = (b) => {
    setEditId(b.id);
    setForm({ title: b.title, author: b.author, subject: b.subject, isbn: b.isbn || '' });
    setFormErr('');
    setShowForm(true);
  };

  const saveBook = async (e) => {
    e.preventDefault();
    if (!form.title.trim() || !form.author.trim() || !form.subject) {
      setFormErr('Title, author and subject are all required.');
      return;
    }
    setBusy('form');
    try {
      if (editId) {
        await updateBook(editId, form);
        notify({ message: 'Book updated.', type: 'success' });
      } else {
        await createBook(form);
        notify({ message: 'Book created — add copies next.', type: 'success' });
      }
      setShowForm(false);
      load();
    } catch (err) {
      setFormErr(err.message);
    } finally {
      setBusy('');
    }
  };

  const doDelete = async (b) => {
    if (!window.confirm(`Delete “${b.title}” and all its copy records? Existing loans keep their references.`)) return;
    setBusy(b.id);
    try {
      await deleteBook(b.id);
      notify({ message: 'Book deleted.', type: 'success' });
      if (copiesOf === b.id) { setCopiesOf(null); }
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const toggleCopies = async (bookId) => {
    if (copiesOf === bookId) { setCopiesOf(null); return; }
    setCopiesOf(bookId);
    setCopiesErr('');
    setCopies([]);
    try {
      setCopies(await getCopies(bookId));
    } catch (e) {
      setCopiesErr(e.message);
    }
  };

  const reloadCopies = async (bookId) => {
    try { setCopies(await getCopies(bookId)); } catch { /* ignore */ }
  };

  const doAddCopies = async (bookId) => {
    setCopiesBusy(bookId);
    try {
      const res = await addCopies(bookId, { count: Number(copiesForm.count), condition: copiesForm.condition });
      notify({ message: res.message || 'Copies added.', type: 'success' });
      setCopiesForm({ count: 1, condition: 'GOOD' });
      await reloadCopies(bookId);
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setCopiesBusy('');
    }
  };

  const doCopyStatus = async (copyId, status, bookId) => {
    setBusy(copyId);
    try {
      await updateCopyStatus(copyId, { status });
      notify({ message: `Copy set to ${status}.`, type: 'success' });
      await reloadCopies(bookId);
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="page">
      <h1>Manage books &amp; copies</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="filter-bar">
        <input
          className="search-bar__input"
          placeholder="Search books…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn btn--primary" onClick={() => (showForm ? setShowForm(false) : openAdd())}>
          {showForm ? 'Close form' : '＋ Add book'}
        </button>
      </div>

      {showForm && (
        <form className="panel book-form" onSubmit={saveBook}>
          <h2>{editId ? 'Edit book' : 'New book'}</h2>
          {formErr && <div className="banner banner--warning">{formErr}</div>}
          <div className="form-grid">
            <label className="field"><span>Title</span>
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </label>
            <label className="field"><span>Author</span>
              <input value={form.author} onChange={(e) => setForm({ ...form, author: e.target.value })} />
            </label>
            <label className="field"><span>Subject</span>
              <input
                list="subject-list"
                value={form.subject}
                placeholder="Pick or type a new subject"
                onChange={(e) => setForm({ ...form, subject: e.target.value })}
              />
              <datalist id="subject-list">
                {subjects.map((s) => <option key={s} value={s} />)}
              </datalist>
            </label>
            <label className="field"><span>ISBN (optional)</span>
              <input value={form.isbn} onChange={(e) => setForm({ ...form, isbn: e.target.value })} />
            </label>
          </div>
          <button className="btn btn--primary" disabled={busy === 'form'}>{editId ? 'Save changes' : 'Create book'}</button>
        </form>
      )}

      {loading ? <div className="loading">Loading…</div> : (
        <table className="table">
          <thead>
            <tr><th>Title</th><th>Author</th><th>Subject</th><th>Copies</th><th></th></tr>
          </thead>
          <tbody>
            {books.map((b) => (
              <React.Fragment key={b.id}>
                <tr>
                  <td>{b.title}</td>
                  <td>{b.author}</td>
                  <td>{b.subject}</td>
                  <td>{b.availableCopies} available / {b.totalCopies ?? b.availableCopies} total{b.waitlistSize > 0 ? ` · ${b.waitlistSize} queued` : ''}</td>
                  <td className="row-actions">
                    <button className="btn" onClick={() => toggleCopies(b.id)}>
                      {copiesOf === b.id ? 'Hide copies' : 'Copies'}
                    </button>
                    <button className="btn" onClick={() => openEdit(b)}>Edit</button>
                    <button className="btn btn--danger" disabled={busy === b.id} onClick={() => doDelete(b)}>Delete</button>
                  </td>
                </tr>
                {copiesOf === b.id && (
                  <tr className="detail-row">
                    <td colSpan={5}>
                      {copiesErr && <div className="banner banner--warning">{copiesErr}</div>}
                      <div className="copies-panel">
                        <table className="table table--compact">
                          <thead><tr><th>Copy</th><th>Status</th><th>Condition</th><th>Holder</th><th>Set status</th></tr></thead>
                          <tbody>
                            {copies.map((c) => (
                              <tr key={c.id}>
                                <td>{c.id.slice(-6).toUpperCase()}</td>
                                <td><span className={`badge ${c.status === 'AVAILABLE' ? 'badge--green' : c.status === 'DAMAGED' ? 'badge--yellow' : 'badge--blue'}`}>{c.status}</span></td>
                                <td>{c.condition}</td>
                                <td>{c.holderName ?? '—'}</td>
                                <td>
                                  {(c.status === 'AVAILABLE' || c.status === 'DAMAGED') ? (
                                    <select
                                      value={c.status}
                                      disabled={busy === c.id}
                                      onChange={(e) => doCopyStatus(c.id, e.target.value, b.id)}
                                    >
                                      {c.status === 'AVAILABLE' && <>
                                        <option value="AVAILABLE">AVAILABLE</option>
                                        <option value="DAMAGED">DAMAGED</option>
                                        <option value="LOST">LOST</option>
                                      </>}
                                      {c.status === 'DAMAGED' && <>
                                        <option value="DAMAGED">DAMAGED</option>
                                        <option value="AVAILABLE">AVAILABLE (repaired)</option>
                                      </>}
                                    </select>
                                  ) : <em className="page-subtitle">reserved/issued — locked</em>}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                        <div className="copies-add">
                          <input
                            type="number"
                            min="1"
                            value={copiesForm.count}
                            onChange={(e) => setCopiesForm({ ...copiesForm, count: e.target.value })}
                          />
                          <select value={copiesForm.condition} onChange={(e) => setCopiesForm({ ...copiesForm, condition: e.target.value })}>
                            <option value="GOOD">GOOD</option>
                            <option value="WORN">WORN</option>
                            <option value="DAMAGED">DAMAGED</option>
                          </select>
                          <button className="btn btn--primary" disabled={copiesBusy === b.id} onClick={() => doAddCopies(b.id)}>
                            {copiesBusy === b.id ? 'Adding…' : 'Add copies'}
                          </button>
                          <span className="page-subtitle">Librarians in the waitlist are auto-promoted when copies free up.</span>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
