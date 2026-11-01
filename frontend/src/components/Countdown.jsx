import React, { useState, useEffect } from 'react';

function parts(msLeft) {
  const abs = Math.max(0, msLeft);
  const h = Math.floor(abs / 3600000);
  const m = Math.floor((abs % 3600000) / 60000);
  const s = Math.floor((abs % 60000) / 1000);
  return `${h}h ${m}m ${s}s`;
}

/** Live countdown to an ISO deadline; shows "expired" once passed. */
export default function Countdown({ deadline }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  if (!deadline) return null;
  const msLeft = new Date(deadline).getTime() - now;
  const expired = msLeft <= 0;

  return (
    <span className={`countdown ${expired ? 'countdown--expired' : ''}`}>
      {expired ? 'expired' : parts(msLeft)}
    </span>
  );
}
