import React, { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  AlertCircle, AlertTriangle, BadgeCheck, Ban, BookOpen, CheckCircle2, CircleHelp,
  Clock3, Hourglass, Inbox, Info, ListOrdered, Loader2, Package, PackageCheck,
  PenLine, Wrench, X, XCircle, Wallet, ChevronLeft, ChevronRight,
} from '../icons';

/* ───────────────────────────── Button ───────────────────────────── */
export function Button({
  variant = 'primary', size = 'md', loading = false, icon: Icon,
  children, className = '', type = 'button', disabled, ...props
}) {
  return (
    <button
      type={type}
      className={`btn btn--${variant} btn--${size} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <Loader2 size={16} className="spin" aria-hidden="true" /> : Icon ? <Icon size={16} aria-hidden="true" /> : null}
      {children && <span>{children}</span>}
    </button>
  );
}

/* ───────────────────────────── Form fields ───────────────────────────── */
export const Input = React.forwardRef(function Input(
  { label, error, hint, id, className = '', ...props }, ref
) {
  const autoId = useId();
  const inputId = id || autoId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;
  return (
    <div className={`field ${className}`}>
      {label && <label className="field__label" htmlFor={inputId}>{label}</label>}
      <input
        ref={ref}
        id={inputId}
        className={`input ${error ? 'input--error' : ''}`}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy}
        {...props}
      />
      {error
        ? <span id={`${inputId}-error`} className="field__error" role="alert">{error}</span>
        : hint ? <span id={`${inputId}-hint`} className="field__hint">{hint}</span> : null}
    </div>
  );
});

export const Select = React.forwardRef(function Select(
  { label, error, hint, id, children, className = '', ...props }, ref
) {
  const autoId = useId();
  const selectId = id || autoId;
  return (
    <div className={`field ${className}`}>
      {label && <label className="field__label" htmlFor={selectId}>{label}</label>}
      <select
        ref={ref}
        id={selectId}
        className={`input select ${error ? 'input--error' : ''}`}
        aria-invalid={error ? 'true' : undefined}
        {...props}
      >
        {children}
      </select>
      {error
        ? <span className="field__error" role="alert">{error}</span>
        : hint ? <span className="field__hint">{hint}</span> : null}
    </div>
  );
});

export function Textarea({ label, error, hint, id, className = '', ...props }) {
  const autoId = useId();
  const textId = id || autoId;
  return (
    <div className={`field ${className}`}>
      {label && <label className="field__label" htmlFor={textId}>{label}</label>}
      <textarea
        id={textId}
        className={`input textarea ${error ? 'input--error' : ''}`}
        aria-invalid={error ? 'true' : undefined}
        {...props}
      />
      {error
        ? <span className="field__error" role="alert">{error}</span>
        : hint ? <span className="field__hint">{hint}</span> : null}
    </div>
  );
}

/* ───────────────────────────── Badges ───────────────────────────── */
export function Badge({ tone = 'neutral', icon: Icon, children, className = '' }) {
  return (
    <span className={`badge badge--${tone} ${className}`}>
      {Icon && <Icon size={12} aria-hidden="true" />}
      <span>{children}</span>
    </span>
  );
}

const STATUS_META = {
  READY_FOR_PICKUP: { label: 'Ready for pickup', tone: 'info', icon: Clock3 },
  ISSUED: { label: 'Issued', tone: 'success', icon: BookOpen },
  OVERDUE: { label: 'Overdue', tone: 'danger', icon: AlertTriangle },
  FINE_PENDING: { label: 'Fine pending', tone: 'warning', icon: Wallet },
  REFLECTION_PENDING: { label: 'Reflection pending', tone: 'accent', icon: PenLine },
  WAITLISTED: { label: 'Waitlisted', tone: 'neutral', icon: ListOrdered },
  CLOSED: { label: 'Closed', tone: 'neutral', icon: CheckCircle2 },
  RETURNED: { label: 'Returned', tone: 'success', icon: CheckCircle2 },
  EXPIRED: { label: 'Expired', tone: 'neutral', icon: XCircle },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', icon: Ban },
  LOST: { label: 'Lost', tone: 'danger', icon: CircleHelp },
  AVAILABLE: { label: 'Available', tone: 'success', icon: PackageCheck },
  RESERVED: { label: 'Reserved', tone: 'info', icon: Package },
  DAMAGED: { label: 'Damaged', tone: 'warning', icon: Wrench },
  PENDING: { label: 'Pending', tone: 'warning', icon: Hourglass },
  PAID: { label: 'Paid', tone: 'success', icon: BadgeCheck },
  APPROVED: { label: 'Approved', tone: 'success', icon: BadgeCheck },
  REJECTED: { label: 'Rejected', tone: 'danger', icon: XCircle },
  GOOD: { label: 'Good', tone: 'success', icon: CheckCircle2 },
  WORN: { label: 'Worn', tone: 'warning', icon: Wrench },
};

export function StatusBadge({ status, label }) {
  const meta = STATUS_META[status] || { label: (status || 'Unknown').replace(/_/g, ' '), tone: 'neutral' };
  return <Badge tone={meta.tone} icon={meta.icon}>{label || meta.label}</Badge>;
}

/* ───────────────────────────── Layout primitives ───────────────────────────── */
export function PageHeader({ eyebrow, title, subtitle, actions }) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}

export function Card({ title, subtitle, actions, children, className = '', as: Tag = 'section' }) {
  return (
    <Tag className={`card ${className}`}>
      {(title || actions) && (
        <div className="card__head">
          <div>
            {title && <h2 className="card__title">{title}</h2>}
            {subtitle && <p className="card__subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="card__actions">{actions}</div>}
        </div>
      )}
      {children}
    </Tag>
  );
}

export function StatCard({ label, value, hint, icon: Icon, tone = 'brand' }) {
  return (
    <div className={`stat-card stat-card--${tone}`}>
      <div className="stat-card__top">
        <span className="stat-card__label">{label}</span>
        {Icon && <span className="stat-card__icon"><Icon size={17} aria-hidden="true" /></span>}
      </div>
      <div className="stat-card__value">{value}</div>
      {hint && <div className="stat-card__hint">{hint}</div>}
    </div>
  );
}

export function Chip({ active, children, ...props }) {
  return (
    <button type="button" className={`chip ${active ? 'chip--on' : ''}`} aria-pressed={active} {...props}>
      {children}
    </button>
  );
}

/* ───────────────────────────── Table / Pagination ───────────────────────────── */
export function Table({ children, className = '' }) {
  return (
    <div className="table-wrap">
      <table className={`ui-table ${className}`}>{children}</table>
    </div>
  );
}

export function Pagination({ page = 0, totalPages = 1, totalItems, size = 10, onChange }) {
  if (!totalPages || totalPages <= 1) {
    return totalItems != null
      ? <div className="pagination pagination--single">Showing {totalItems} item{totalItems === 1 ? '' : 's'}</div>
      : null;
  }
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages - 1, page + 2);
  const numbers = [];
  for (let i = start; i <= end; i++) numbers.push(i);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="pagination__summary">
        {totalItems != null && `${Math.min(totalItems, page * size + 1)}–${Math.min(totalItems, (page + 1) * size)} of ${totalItems}`}
      </span>
      <div className="pagination__controls">
        <Button variant="ghost" size="sm" aria-label="Previous page" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          <ChevronLeft size={16} />
        </Button>
        {start > 0 && <span className="pagination__ellipsis">…</span>}
        {numbers.map((n) => (
          <button
            key={n}
            type="button"
            className={`pagination__page ${n === page ? 'pagination__page--on' : ''}`}
            aria-current={n === page ? 'page' : undefined}
            onClick={() => onChange(n)}
          >
            {n + 1}
          </button>
        ))}
        {end < totalPages - 1 && <span className="pagination__ellipsis">…</span>}
        <Button variant="ghost" size="sm" aria-label="Next page" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
          <ChevronRight size={16} />
        </Button>
      </div>
    </nav>
  );
}

/* ───────────────────────────── States ───────────────────────────── */
export function EmptyState({ icon: Icon = Inbox, title = 'Nothing here yet', description, action }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon"><Icon size={24} aria-hidden="true" /></span>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}

export function Skeleton({ width, height = 14, className = '', style }) {
  return <span className={`skeleton ${className}`} style={{ width, height, ...style }} aria-hidden="true" />;
}

export function SkeletonTable({ rows = 5, columns = 4 }) {
  return (
    <div className="skeleton-table" aria-label="Loading content" role="status">
      <div className="skeleton-table__row skeleton-table__row--head">
        {Array.from({ length: columns }).map((_, i) => <Skeleton key={i} height={11} width="70%" />)}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div className="skeleton-table__row" key={r}>
          {Array.from({ length: columns }).map((_, c) => <Skeleton key={c} height={13} width={c === 0 ? '85%' : '55%'} />)}
        </div>
      ))}
    </div>
  );
}

export function Spinner({ label = 'Loading…', size = 20 }) {
  return (
    <span className="spinner" role="status">
      <Loader2 size={size} className="spin" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  );
}

export function Alert({ tone = 'info', title, children, action }) {
  const Icon = tone === 'error' ? AlertCircle : tone === 'warning' ? AlertTriangle : Info;
  return (
    <div className={`alert alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={18} className="alert__icon" aria-hidden="true" />
      <div className="alert__body">
        {title && <strong>{title}</strong>}
        {children && <div>{children}</div>}
      </div>
      {action}
    </div>
  );
}

/* ───────────────────────────── Tabs ───────────────────────────── */
export function Tabs({ tabs, active, onChange, ariaLabel = 'Sections' }) {
  return (
    <div className="tabs" role="tablist" aria-label={ariaLabel}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={active === tab.id}
          className={`tab ${active === tab.id ? 'tab--on' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
          {tab.count != null && <span className="tab__count">{tab.count}</span>}
        </button>
      ))}
    </div>
  );
}

/* ───────────────────────────── Modal / Confirm ───────────────────────────── */
export function Modal({ open, onClose, title, description, children, footer, size = 'md' }) {
  const panelRef = useRef(null);
  const restoreFocus = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    restoreFocus.current = document.activeElement;
    const panel = panelRef.current;
    const focusables = () => panel
      ? Array.from(panel.querySelectorAll('a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'))
      : [];
    focusables()[0]?.focus();

    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
      if (event.key !== 'Tab') return;
      const nodes = focusables();
      if (nodes.length === 0) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      restoreFocus.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className={`modal modal--${size}`}
      >
        <div className="modal__head">
          <div>
            <h2 id="modal-title">{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button type="button" className="icon-btn" aria-label="Close dialog" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        <div className="modal__body">{children}</div>
        {footer && <div className="modal__foot">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

export function ConfirmDialog({
  open, onClose, onConfirm, title = 'Are you sure?', message,
  confirmLabel = 'Confirm', cancelLabel = 'Cancel', variant = 'danger', loading = false,
}) {
  return (
    <Modal
      open={open}
      onClose={loading ? () => {} : onClose}
      title={title}
      footer={(
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>{cancelLabel}</Button>
          <Button variant={variant} onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
        </>
      )}
    >
      <p className="modal__message">{message}</p>
    </Modal>
  );
}

/* ───────────────────────────── Tooltip ───────────────────────────── */
export function Tooltip({ text, children }) {
  return (
    <span className="tooltip" data-tip={text} tabIndex={0}>
      {children}
    </span>
  );
}
