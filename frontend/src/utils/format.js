const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
});
const dateFmt = new Intl.DateTimeFormat('en-IN', {
  month: 'short', day: 'numeric', year: 'numeric',
});

export function formatDateTime(iso) {
  return iso ? dateTimeFmt.format(new Date(iso)) : '—';
}

export function formatDate(iso) {
  return iso ? dateFmt.format(new Date(iso)) : '—';
}

/** "due in 3 days", "2 hours ago", "just now" - absolute time belongs in a title tooltip. */
export function relativeTime(iso, { now = Date.now() } = {}) {
  if (!iso) return '—';
  const diff = new Date(iso).getTime() - now;
  const abs = Math.abs(diff);
  const minute = 60_000;
  const hour = 3_600_000;
  const day = 86_400_000;
  let value;
  let unit;
  if (abs < minute) {
    return diff >= 0 ? 'in a moment' : 'just now';
  }
  if (abs < hour) {
    value = Math.round(abs / minute);
    unit = 'minute';
  } else if (abs < day) {
    value = Math.round(abs / hour);
    unit = 'hour';
  } else if (abs < 30 * day) {
    value = Math.round(abs / day);
    unit = 'day';
  } else if (abs < 365 * day) {
    value = Math.round(abs / (30 * day));
    unit = 'month';
  } else {
    value = Math.round(abs / (365 * day));
    unit = 'year';
  }
  const plural = value === 1 ? '' : 's';
  return diff >= 0 ? `in ${value} ${unit}${plural}` : `${value} ${unit}${plural} ago`;
}

export function money(amount) {
  return `₹${Number(amount || 0).toLocaleString('en-IN')}`;
}

export function dueLabel(iso) {
  if (!iso) return '—';
  return `Due ${relativeTime(iso)}`;
}

export function statusLabel(status) {
  return (status || '').replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function initials(title = '') {
  return title.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
}
