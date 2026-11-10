import React, { useCallback, useEffect, useState } from 'react';
import { ListOrdered } from '../components/icons';
import { getBooks, getWaitlistForBook } from '../api';
import { formatDateTime } from '../utils/format';
import {
  Alert, Button, EmptyState, PageHeader, Select, SkeletonTable, StatusBadge, Table,
} from '../components/ui';

export default function WaitlistsPage() {
  const [books, setBooks] = useState([]);
  const [selected, setSelected] = useState('');
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getBooks({ size: 100, sort: 'title' })
      .then((envelope) => setBooks(envelope?.items || envelope || []))
      .catch((e) => setError(e.message));
  }, []);

  const pick = useCallback(async (bookId) => {
    setSelected(bookId);
    setQueue([]);
    setError('');
    if (!bookId) return;
    setLoading(true);
    try {
      setQueue(await getWaitlistForBook(bookId));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const chosen = books.find((b) => b.id === selected);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Waitlists"
        subtitle="Inspect FIFO queues. Copies are offered to the oldest waiting request first."
      />

      {error && <Alert tone="error" title="Could not load the waitlist">{error}</Alert>}

      <div className="toolbar">
        <Select label="Book" aria-label="Select a book" value={selected} onChange={(e) => pick(e.target.value)} style={{ minWidth: 300 }}>
          <option value="">Select a book…</option>
          {books.map((book) => (
            <option key={book.id} value={book.id}>
              {book.title} — {book.waitlistSize > 0 ? `${book.waitlistSize} waiting` : 'no queue'}
            </option>
          ))}
        </Select>
        {chosen && <span className="badge badge--info">{chosen.availableCopies} available · {chosen.totalCopies} copies</span>}
      </div>

      {loading ? <SkeletonTable rows={4} columns={4} /> : !selected ? (
        <EmptyState icon={ListOrdered} title="Choose a book" description="The ordered queue and each student's join time will appear here." />
      ) : queue.length === 0 ? (
        <EmptyState icon={ListOrdered} title="No one is waiting" description={`“${chosen?.title || 'This book'}” has no waiting students.`} />
      ) : (
        <Table>
          <thead><tr><th>Position</th><th>Student</th><th>Status</th><th>Joined</th></tr></thead>
          <tbody>
            {queue.map((entry, index) => (
              <tr key={entry.entryId || entry.id || index}>
                <td><strong>#{entry.position || index + 1}</strong></td>
                <td>{entry.studentName}</td>
                <td><StatusBadge status={entry.status} /></td>
                <td>{formatDateTime(entry.joinedAt)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Alert tone="info" title="How promotion works">
        When a copy is returned or added, the oldest WAITING entry is atomically promoted to READY_FOR_PICKUP.
        If the student does not collect it within 48 hours, it moves to the next person.
      </Alert>
    </div>
  );
}
