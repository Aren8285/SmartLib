import React, { useEffect, useState } from 'react';
import { formatDateTime } from '../utils/format';

const HOUR = 3_600_000;

function parts(msLeft) {
  const abs = Math.max(0, msLeft);
  const days = Math.floor(abs / 86_400_000);
  const hours = Math.floor((abs % 86_400_000) / HOUR);
  const minutes = Math.floor((abs % HOUR) / 60_000);
  const seconds = Math.floor((abs % 60_000) / 1000);
  if (days > 0) return `${days}d ${hours}h ${minutes}m`;
  return `${hours}h ${String(minutes).padStart(2, '0')}m ${String(seconds).padStart(2, '0')}s`;
}

/**
 * Live countdown to an ISO deadline. Turns red under 6 hours and shows
 * "Expired" once passed. The absolute timestamp is available on hover for
 * users who need an exact time.
 */
export default function Countdown({ deadline, className = '' }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  if (!deadline) return null;
  const msLeft = new Date(deadline).getTime() - now;
  const expired = msLeft <= 0;
  const urgent = !expired && msLeft <= 6 * HOUR;
  const label = expired ? 'Expired' : parts(msLeft);

  return (
    <span
      className={`countdown ${urgent ? 'countdown--urgent' : ''} ${expired ? 'countdown--expired' : ''} ${className}`}
      title={formatDateTime(deadline)}
      role="timer"
      aria-label={`${expired ? 'Expired' : 'Time remaining'}: ${label}`}
    >
      {label}
    </span>
  );
}
