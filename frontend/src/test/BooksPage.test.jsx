import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import BooksPage from '../pages/BooksPage';

const getBooks = vi.fn();
const getSubjects = vi.fn();
const requestLoan = vi.fn();

vi.mock('../api', () => ({
  getBooks: (...args) => getBooks(...args),
  getSubjects: (...args) => getSubjects(...args),
  requestLoan: (...args) => requestLoan(...args),
}));

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}));

const envelope = (items, totalItems = items.length) => ({
  items,
  page: 0,
  size: 12,
  totalItems,
  totalPages: totalItems ? Math.ceil(totalItems / 12) : 1,
});

const BOOK = {
  id: 'b1',
  title: 'Clean Code',
  author: 'Robert C. Martin',
  subject: 'Computer Science',
  isbn: '9780132350884',
  availableCopies: 2,
  totalCopies: 3,
  waitlistSize: 1,
  shelfCode: 'A2',
  shelfSection: 'Computer Science',
};

describe('BooksPage', () => {
  beforeEach(() => {
    getBooks.mockReset();
    getSubjects.mockReset();
    requestLoan.mockReset();
    getSubjects.mockResolvedValue(['Computer Science']);
  });

  it('renders skeletons while the first page is loading', () => {
    getBooks.mockReturnValue(new Promise(() => {}));
    render(<MemoryRouter><BooksPage /></MemoryRouter>);
    expect(screen.getByLabelText('Loading books')).toBeInTheDocument();
  });

  it('renders an empty state when no books match', async () => {
    getBooks.mockResolvedValue(envelope([]));
    render(<MemoryRouter><BooksPage /></MemoryRouter>);
    expect(await screen.findByText('No books match those filters')).toBeInTheDocument();
  });

  it('renders an error alert with a retry action', async () => {
    getBooks.mockRejectedValue(new Error('Catalogue unavailable.'));
    render(<MemoryRouter><BooksPage /></MemoryRouter>);
    expect(await screen.findByText('Could not load the catalogue')).toBeInTheDocument();
    expect(screen.getByText('Catalogue unavailable.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('renders books, shelf and availability, and requests a copy', async () => {
    getBooks.mockResolvedValue(envelope([BOOK]));
    requestLoan.mockResolvedValue({ message: 'Copy reserved.' });
    render(<MemoryRouter><BooksPage /></MemoryRouter>);

    expect(await screen.findByText('Clean Code')).toBeInTheDocument();
    expect(screen.getByText('A2')).toBeInTheDocument();
    expect(screen.getByText('2 of 3 available')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Locate Clean Code on the map/i })).toHaveAttribute('href', '/map?shelf=A2');
  });

  it('switches between card and table views', async () => {
    getBooks.mockResolvedValue(envelope([BOOK]));
    const { container } = render(<MemoryRouter><BooksPage /></MemoryRouter>);
    await screen.findByText('Clean Code');

    expect(container.querySelector('.ui-table')).not.toBeInTheDocument();
    const tableButton = screen.getByRole('button', { name: /table/i });
    tableButton.click();
    await waitFor(() => expect(container.querySelector('.ui-table')).toBeInTheDocument());
  });
});
