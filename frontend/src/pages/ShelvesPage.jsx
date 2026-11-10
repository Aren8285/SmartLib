import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Pencil, Plus, Trash2 } from '../components/icons';
import { createShelf, deleteShelf, getShelves, updateShelf } from '../api';
import { useToast } from '../context/ToastContext';
import {
  Alert, Badge, Button, Card, ConfirmDialog, EmptyState, Input, Modal, PageHeader,
  SkeletonTable, Table,
} from '../components/ui';

const EMPTY = { code: '', section: '', row: 1, column: 1, capacity: 12, label: '' };

export default function ShelvesPage() {
  const toast = useToast();
  const [shelves, setShelves] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getShelves().then(setShelves).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const openAdd = () => {
    setEditing(null);
    setForm({ ...EMPTY, row: Math.max(1, ...shelves.map((s) => s.row)), column: 1 });
    setFormError('');
    setModalOpen(true);
  };

  const openEdit = (shelf) => {
    setEditing(shelf);
    setForm({
      code: shelf.code,
      section: shelf.section,
      row: shelf.row,
      column: shelf.column,
      capacity: shelf.capacity,
      label: shelf.label || '',
    });
    setFormError('');
    setModalOpen(true);
  };

  const save = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!form.code.trim() || !form.section.trim()) {
      setFormError('Code and section are required.');
      return;
    }
    if (Number(form.row) < 1 || Number(form.column) < 1 || Number(form.capacity) < 1) {
      setFormError('Row, column and capacity must be positive numbers.');
      return;
    }
    setSaving(true);
    const payload = {
      ...form,
      code: form.code.trim(),
      section: form.section.trim(),
      row: Number(form.row),
      column: Number(form.column),
      capacity: Number(form.capacity),
    };
    try {
      if (editing) await updateShelf(editing.id, payload);
      else await createShelf(payload);
      toast.success(editing ? 'Shelf updated.' : 'Shelf created.');
      setModalOpen(false);
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
      await deleteShelf(deleting.id);
      toast.success(`Shelf ${deleting.code} deleted.`);
      setDeleting(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setDeleteBusy(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Shelves"
        subtitle="The floor is a grid of shelves. Capacity is measured in books, and a non-empty shelf cannot be deleted."
        actions={<Button icon={Plus} onClick={openAdd}>Add shelf</Button>}
      />

      {error && <Alert tone="error" title="Could not load shelves" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {loading ? <SkeletonTable rows={6} columns={5} /> : shelves.length === 0 ? (
        <EmptyState title="No shelves yet" description="Add shelves in a grid so books can be located on the map." action={<Button onClick={openAdd}>Add the first shelf</Button>} />
      ) : (
        <Card title="Floor layout" subtitle={`${shelves.length} shelves across ${new Set(shelves.map((s) => s.section)).size} sections`}>
          <Table>
            <thead><tr><th>Code</th><th>Section</th><th>Position</th><th>Books</th><th>Label</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {shelves.map((shelf) => {
                const full = shelf.bookCount >= shelf.capacity;
                return (
                  <tr key={shelf.id}>
                    <td><strong>{shelf.code}</strong></td>
                    <td>{shelf.section}</td>
                    <td className="mono">row {shelf.row} · col {shelf.column}</td>
                    <td>
                      <Badge tone={full ? 'danger' : 'neutral'}>{shelf.bookCount}/{shelf.capacity}</Badge>
                      {full && <span className="cell-detail"> full</span>}
                    </td>
                    <td>{shelf.label || '—'}</td>
                    <td>
                      <div className="row-actions">
                        <Link to={`/map?shelf=${shelf.code}`} className="btn btn--ghost btn--sm"><MapPin size={13} /> Map</Link>
                        <Button size="sm" variant="secondary" icon={Pencil} onClick={() => openEdit(shelf)}>Edit</Button>
                        <Button size="sm" variant="ghost" icon={Trash2} onClick={() => setDeleting(shelf)}>Delete</Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </Card>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? `Edit shelf ${editing.code}` : 'Add a shelf'}
        description="Codes are unique. Two shelves cannot share a grid position."
        footer={(
          <>
            <Button variant="ghost" onClick={() => setModalOpen(false)} disabled={saving}>Cancel</Button>
            <Button onClick={save} loading={saving}>{editing ? 'Save changes' : 'Create shelf'}</Button>
          </>
        )}
      >
        <form onSubmit={save} noValidate>
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="field-row">
            <Input label="Code" value={form.code} maxLength={10} placeholder="A3" onChange={(e) => setForm({ ...form, code: e.target.value })} />
            <Input label="Section" value={form.section} maxLength={60} placeholder="Computer Science" onChange={(e) => setForm({ ...form, section: e.target.value })} />
          </div>
          <div className="field-row">
            <Input label="Row" type="number" min="1" value={form.row} onChange={(e) => setForm({ ...form, row: e.target.value })} />
            <Input label="Column" type="number" min="1" value={form.column} onChange={(e) => setForm({ ...form, column: e.target.value })} />
          </div>
          <div className="field-row">
            <Input label="Capacity (books)" type="number" min="1" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
            <Input label="Label (optional)" value={form.label} maxLength={80} onChange={(e) => setForm({ ...form, label: e.target.value })} />
          </div>
          <button type="submit" hidden />
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={doDelete}
        loading={deleteBusy}
        title={`Delete shelf ${deleting?.code || ''}?`}
        message={deleting ? `${deleting.bookCount} book(s) are currently on this shelf. A non-empty shelf cannot be deleted; move the books first.` : ''}
        confirmLabel="Delete shelf"
      />
    </div>
  );
}
