import React, { useCallback, useEffect, useState } from 'react';
import { BookOpen, Library, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import {
  addCopies, createBook, deleteBook, getBooks, getCopies, getShelves, getSubjects,
  updateBook, updateCopyStatus,
} from '../api';
import { useToast } from '../context/ToastContext';
import {
  Alert, Badge, Button, ConfirmDialog, EmptyState, Input, Modal, PageHeader,
  Pagination, Select, SkeletonTable, StatusBadge, Table,
} from '../components/ui';

const EMPTY = { title: '', author: '', subject: '', isbn: '', shelfId: '' };
const SIZE = 10;

export default function ManageBooksPage() {
  const toast = useToast();
  const [data, setData] = useState({ items: [], totalItems: 0, totalPages: 1 });
  const [subjects, setSubjects] = useState([]);
  const [shelves, setShelves] = useState([]);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const [copiesOf, setCopiesOf] = useState(null);
  const [copies, setCopies] = useState([]);
  const [copiesLoading, setCopiesLoading] = useState(false);
  const [copiesError, setCopiesError] = useState('');
  const [copiesForm, setCopiesForm] = useState({ count: 1, condition: 'GOOD' });
  const [copiesBusy, setCopiesBusy] = useState('');
  const [copyBusy, setCopyBusy] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getBooks({ query, page, size: SIZE, sort: 'title' })
      .then((envelope) => setData(envelope?.items ? envelope : { items: envelope || [], totalItems: 0, totalPages: 1 }))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [query, page]);

  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
  }, [load]);
  useEffect(() => {
    getSubjects().then(setSubjects).catch(() => {});
    getShelves().then(setShelves).catch(() => {});
  }, []);

  const openAdd = () => {
    setEditing(null);
    setForm(EMPTY);
    setFormError('');
    setFormOpen(true);
  };

  const openEdit = (book) => {
    setEditing(book);
    setForm({
      title: book.title,
      author: book.author,
      subject: book.subject,
      isbn: book.isbn || '',
      shelfId: book.shelfId || '',
    });
    setFormError('');
    setFormOpen(true);
  };

  const save = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!form.title.trim() || !form.author.trim() || !form.subject.trim()) {
      setFormError('Title, author and subject are all required.');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        title: form.title.trim(),
        author: form.author.trim(),
        subject: form.subject.trim(),
        isbn: form.isbn.trim() || null,
        shelfId: form.shelfId || null,
      };
      if (editing) await updateBook(editing.id, payload);
      else await createBook(payload);
      toast.success(editing ? 'Book updated.' : 'Book created. Add copies when you are ready.');
      setFormOpen(false);
      load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  };

  const doDelete = async () => {
    setDeleteBusy(true);
    try {
      await deleteBook(deleteTarget.id);
      toast.success(`“${deleteTarget.title}” deleted.`);
      setDeleteTarget(null);
      if (copiesOf === deleteTarget.id) setCopiesOf(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setDeleteBusy(false);
    }
  };

  const toggleCopies = async (book) => {
    if (copiesOf === book.id) { setCopiesOf(null); return; }
    setCopiesOf(book.id);
    setCopiesError('');
    setCopies([]);
    setCopiesLoading(true);
    try {
      setCopies(await getCopies(book.id));
    } catch (e) {
      setCopiesError(e.message);
    } finally {
      setCopiesLoading(false);
    }
  };

  const reloadCopies = async (bookId) => {
    try { setCopies(await getCopies(bookId)); } catch (e) { setCopiesError(e.message); }
  };

  const doAddCopies = async (bookId) => {
    setCopiesBusy(bookId);
    try {
      await addCopies(bookId, { count: Number(copiesForm.count), condition: copiesForm.condition });
      toast.success(`${copiesForm.count} copy/copies added${copiesForm.condition === 'GOOD' ? '' : ` (${copiesForm.condition})`}.`);
      setCopiesForm({ count: 1, condition: 'GOOD' });
      await reloadCopies(bookId);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setCopiesBusy('');
    }
  };

  const doCopyStatus = async (copy, status, bookId) => {
    setCopyBusy(copy.id);
    try {
      await updateCopyStatus(copy.id, { status });
      toast.success(`Copy marked ${status.toLowerCase()}.`);
      await reloadCopies(bookId);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setCopyBusy('');
    }
  };

  const books = data.items || [];

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Books & copies"
        subtitle="Create titles, place them on a shelf and manage every physical copy."
        actions={<Button icon={Plus} onClick={openAdd}>Add book</Button>}
      />

      <div className="toolbar">
        <div className="field toolbar__search" style={{ margin: 0 }}>
          <label className="sr-only" htmlFor="manage-search">Search books</label>
          <div style={{ position: 'relative' }}>
            <Search size={15} aria-hidden="true" style={{ position: 'absolute', left: 11, top: '50%', transform: 'translateY(-50%)', color: 'var(--muted)' }} />
            <input id="manage-search" className="input" style={{ paddingLeft: 33 }} placeholder="Search the catalogue…" value={query} onChange={(e) => { setPage(0); setQuery(e.target.value); }} />
          </div>
        </div>
        <Badge tone="neutral">{data.totalItems || 0} titles</Badge>
        <Badge tone="info"><Library size={12} /> {shelves.length} shelves</Badge>
      </div>

      {error && <Alert tone="error" title="Could not load the catalogue" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {loading ? <SkeletonTable rows={7} columns={5} /> : books.length === 0 ? (
        <EmptyState icon={BookOpen} title={query ? 'No books match that search' : 'The catalogue is empty'} description={query ? 'Try another title, author or subject.' : 'Add the first title, then add copies to make it borrowable.'} action={<Button onClick={openAdd}>Add a book</Button>} />
      ) : (
        <>
          <Table>
            <thead><tr><th>Title</th><th>Author</th><th>Subject</th><th>Shelf</th><th>Copies</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {books.map((book) => (
                <React.Fragment key={book.id}>
                  <tr>
                    <td><strong>{book.title}</strong><div className="cell-detail mono">{book.isbn || 'no ISBN'}</div></td>
                    <td>{book.author}</td>
                    <td><Badge tone="neutral">{book.subject}</Badge></td>
                    <td>{book.shelfCode ? <span className="shelf-tag">{book.shelfCode}</span> : <span className="cell-detail">Unassigned</span>}</td>
                    <td>{book.availableCopies} available / {book.totalCopies ?? book.availableCopies} total{book.waitlistSize > 0 ? ` · ${book.waitlistSize} queued` : ''}</td>
                    <td>
                      <div className="row-actions">
                        <Button size="sm" variant="secondary" onClick={() => toggleCopies(book)}>{copiesOf === book.id ? 'Hide copies' : 'Copies'}</Button>
                        <Button size="sm" variant="ghost" icon={Pencil} onClick={() => openEdit(book)}>Edit</Button>
                        <Button size="sm" variant="ghost" icon={Trash2} onClick={() => setDeleteTarget(book)}>Delete</Button>
                      </div>
                    </td>
                  </tr>
                  {copiesOf === book.id && (
                    <tr>
                      <td colSpan={6} style={{ background: 'var(--surface-2)' }}>
                        {copiesError && <Alert tone="error">{copiesError}</Alert>}
                        {copiesLoading ? <SkeletonTable rows={2} columns={4} /> : (
                          <div className="copies-panel">
                            <Table>
                              <thead><tr><th>Copy</th><th>Status</th><th>Condition</th><th>Holder</th><th>Set status</th></tr></thead>
                              <tbody>
                                {copies.map((copy) => (
                                  <tr key={copy.id}>
                                    <td className="mono">{copy.id.slice(-6).toUpperCase()}</td>
                                    <td><StatusBadge status={copy.status} /></td>
                                    <td>{copy.condition || '—'}</td>
                                    <td>{copy.holderName || '—'}</td>
                                    <td>
                                      {(copy.status === 'AVAILABLE' || copy.status === 'DAMAGED') ? (
                                        <Select
                                          aria-label={`Set status for copy ${copy.id.slice(-6)}`}
                                          value={copy.status}
                                          disabled={copyBusy === copy.id}
                                          onChange={(e) => doCopyStatus(copy, e.target.value, book.id)}
                                          style={{ margin: 0, minWidth: 140 }}
                                        >
                                          {copy.status === 'AVAILABLE' && (
                                            <>
                                              <option value="AVAILABLE">Available</option>
                                              <option value="DAMAGED">Damaged</option>
                                              <option value="LOST">Lost</option>
                                            </>
                                          )}
                                          {copy.status === 'DAMAGED' && (
                                            <>
                                              <option value="DAMAGED">Damaged</option>
                                              <option value="AVAILABLE">Available (repaired)</option>
                                            </>
                                          )}
                                        </Select>
                                      ) : <span className="cell-detail">held by a student — locked</span>}
                                    </td>
                                  </tr>
                                ))}
                                {copies.length === 0 && <tr><td colSpan={5}>No copies yet. Add copies below to make this title borrowable.</td></tr>}
                              </tbody>
                            </Table>
                            <div className="copies-add">
                              <Input aria-label="Number of copies" type="number" min="1" max="20" value={copiesForm.count} onChange={(e) => setCopiesForm({ ...copiesForm, count: e.target.value })} style={{ width: 90, margin: 0 }} />
                              <Select aria-label="Copy condition" value={copiesForm.condition} onChange={(e) => setCopiesForm({ ...copiesForm, condition: e.target.value })} style={{ width: 130, margin: 0 }}>
                                <option value="GOOD">Good</option>
                                <option value="WORN">Worn</option>
                                <option value="DAMAGED">Damaged</option>
                              </Select>
                              <Button size="sm" loading={copiesBusy === book.id} onClick={() => doAddCopies(book.id)}>Add copies</Button>
                              <span className="cell-detail">Waiting students are promoted automatically when a copy is added.</span>
                            </div>
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </Table>
          <Pagination page={data.page || 0} totalPages={data.totalPages || 1} totalItems={data.totalItems} size={data.size || SIZE} onChange={setPage} />
        </>
      )}

      <Modal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? 'Edit book' : 'Add a book'}
        description={editing ? 'Changing the shelf also moves all of its copies.' : 'Copies are added after the title exists.'}
        size="lg"
        footer={(
          <>
            <Button variant="ghost" onClick={() => setFormOpen(false)} disabled={saving}>Cancel</Button>
            <Button onClick={save} loading={saving}>{editing ? 'Save changes' : 'Create book'}</Button>
          </>
        )}
      >
        <form onSubmit={save} noValidate>
          {formError && <Alert tone="error">{formError}</Alert>}
          <Input label="Title" value={form.title} maxLength={200} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          <div className="field-row">
            <Input label="Author" value={form.author} maxLength={100} onChange={(e) => setForm({ ...form, author: e.target.value })} />
            <Input label="Subject" list="manage-subject-list" value={form.subject} maxLength={60} onChange={(e) => setForm({ ...form, subject: e.target.value })} />
            <datalist id="manage-subject-list">{subjects.map((s) => <option key={s} value={s} />)}</datalist>
          </div>
          <div className="field-row">
            <Input label="ISBN (optional)" value={form.isbn} maxLength={20} onChange={(e) => setForm({ ...form, isbn: e.target.value })} />
            <Select label="Shelf" value={form.shelfId} onChange={(e) => setForm({ ...form, shelfId: e.target.value })}>
              <option value="">Unassigned</option>
              {shelves.map((shelf) => (
                <option key={shelf.id} value={shelf.id}>
                  {shelf.code} — {shelf.section} ({shelf.bookCount}/{shelf.capacity})
                </option>
              ))}
            </Select>
          </div>
          <button type="submit" hidden />
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={doDelete}
        loading={deleteBusy}
        title={`Delete “${deleteTarget?.title || ''}”?`}
        message="The title and its remaining copy records are removed. Books with active loans, held copies or waiting students cannot be deleted. This cannot be undone."
        confirmLabel="Delete book"
      />
    </div>
  );
}
