import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import Countdown from '../components/Countdown';

const NOW = new Date('2026-11-06T12:00:00Z').getTime();

describe('Countdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders days and hours for a distant deadline without the urgent style', () => {
    render(<Countdown deadline={new Date(NOW + 3 * 86_400_000).toISOString()} />);
    const timer = screen.getByRole('timer');
    expect(timer).toHaveTextContent('3d');
    expect(timer).not.toHaveClass('countdown--urgent');
  });

  it('turns red under six hours', () => {
    render(<Countdown deadline={new Date(NOW + 5 * 3_600_000).toISOString()} />);
    expect(screen.getByRole('timer')).toHaveClass('countdown--urgent');
  });

  it('shows expired once the deadline passes', () => {
    render(<Countdown deadline={new Date(NOW - 1000).toISOString()} />);
    expect(screen.getByRole('timer')).toHaveTextContent('Expired');
    expect(screen.getByRole('timer')).toHaveClass('countdown--expired');
  });
});
