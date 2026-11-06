import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../components/ui';

describe('StatusBadge', () => {
  it.each([
    ['READY_FOR_PICKUP', 'Ready for pickup', 'badge--info'],
    ['ISSUED', 'Issued', 'badge--success'],
    ['OVERDUE', 'Overdue', 'badge--danger'],
    ['FINE_PENDING', 'Fine pending', 'badge--warning'],
    ['REFLECTION_PENDING', 'Reflection pending', 'badge--accent'],
    ['WAITLISTED', 'Waitlisted', 'badge--neutral'],
    ['CLOSED', 'Closed', 'badge--neutral'],
    ['AVAILABLE', 'Available', 'badge--success'],
    ['DAMAGED', 'Damaged', 'badge--warning'],
    ['PENDING', 'Pending', 'badge--warning'],
    ['APPROVED', 'Approved', 'badge--success'],
  ])('maps %s to a readable, tone-coded badge', (status, label, tone) => {
    const { container } = render(<StatusBadge status={status} />);
    const badge = container.querySelector('.badge');
    expect(badge).toHaveClass(tone);
    expect(screen.getByText(label)).toBeInTheDocument();
    // Status is never conveyed by colour alone: a text label (and icon) is always present.
    expect(badge?.textContent).toContain(label);
  });

  it('falls back gracefully for an unknown status', () => {
    render(<StatusBadge status="SOMETHING_NEW" />);
    expect(screen.getByText('SOMETHING NEW')).toBeInTheDocument();
  });
});
