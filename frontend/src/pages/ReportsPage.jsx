import React, { useCallback, useEffect, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis,
} from 'recharts';
import { BarChart3, Download, RefreshCw, Wallet } from '../components/icons';
import {
  downloadReportCsv, getCirculation, getFinesReport, getInventoryReport, getLeaderboard,
  getOverdueReport, getPopularBooks, getWaitlistReport,
} from '../api';
import { useToast } from '../context/ToastContext';
import { formatDate, money } from '../utils/format';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatCard, Table,
} from '../components/ui';

const COLORS = ['#365663', '#b47a38', '#4f7862', '#9a4a3f', '#75536b', '#3f7373'];
const DAY = 86_400_000;

function isoDate(date) {
  return date.toISOString().slice(0, 10);
}

function defaultRange() {
  const to = new Date();
  const from = new Date(to.getTime() - 29 * DAY);
  return { from: isoDate(from), to: isoDate(to) };
}

function ChartEmpty({ label }) {
  return <EmptyState title="No data in this period" description={label} />;
}

export default function ReportsPage() {
  const toast = useToast();
  const [range, setRange] = useState(defaultRange);
  const [applied, setApplied] = useState(defaultRange);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    const params = { from: applied.from, to: applied.to };
    Promise.all([
      getOverdueReport(),
      getPopularBooks({ ...params, limit: 8 }),
      getCirculation(params),
      getFinesReport(params),
      getWaitlistReport(params),
      getInventoryReport(),
      getLeaderboard(10),
    ])
      .then(([overdue, popular, circulation, fines, waitlist, inventory, leaderboard]) => {
        setData({ overdue, popular, circulation, fines, waitlist, inventory, leaderboard });
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [applied]);

  useEffect(() => { load(); }, [load]);

  const apply = (event) => {
    event.preventDefault();
    if (!range.from || !range.to) return;
    if (range.from > range.to) {
      toast.error('The start date must be before the end date.');
      return;
    }
    setApplied({ ...range });
  };

  const download = async (type) => {
    setDownloading(type);
    try {
      const filename = await downloadReportCsv(type, { from: applied.from, to: applied.to });
      toast.success(`Downloaded ${filename}.`);
    } catch (e) {
      toast.error(e.message);
    } finally {
      setDownloading('');
    }
  };

  if (loading) {
    return (
      <div className="page">
        <PageHeader eyebrow="Librarian" title="Reports" subtitle="Loading the last 30 days of circulation, fines and inventory…" />
        <div className="stats-grid">{Array.from({ length: 4 }).map((_, i) => <div className="stat-card" key={i}><Skeleton width="55%" height={10} /><Skeleton width="35%" height={26} style={{ marginTop: 12 }} /></div>)}</div>
        <div className="reports-grid"><div className="card"><Skeleton height={250} /></div><div className="card"><Skeleton height={250} /></div></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <PageHeader eyebrow="Librarian" title="Reports" />
        <Alert tone="error" title="Could not load reports" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>
      </div>
    );
  }

  const { overdue = [], popular = [], circulation = {}, fines = {}, waitlist = {}, inventory = {}, leaderboard = [] } = data || {};
  const circulationPoints = circulation.points || [];
  const fineReasons = fines.byReason || [];
  const copyStatuses = Object.entries(inventory.copiesByStatus || {});
  const statusData = copyStatuses.map(([name, value]) => ({ name, value }));

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Reports"
        subtitle="Circulation, demand, fines and inventory for the selected period."
        actions={<Button variant="secondary" icon={RefreshCw} onClick={load}>Refresh</Button>}
      />

      <form className="report-filters" onSubmit={apply}>
        <div className="field">
          <label className="field__label" htmlFor="report-from">From</label>
          <input id="report-from" type="date" className="input" value={range.from} max={range.to} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="report-to">To</label>
          <input id="report-to" type="date" className="input" value={range.to} min={range.from} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        </div>
        <Button type="submit" variant="primary">Apply range</Button>
        <span className="cell-detail">Reporting window: {formatDate(applied.from)} – {formatDate(applied.to)}</span>
      </form>

      <div className="stats-grid">
        <StatCard label="Loans issued" value={circulation.totalIssued ?? 0} icon={BarChart3} hint="In selected period" />
        <StatCard label="Loans returned" value={circulation.totalReturned ?? 0} icon={BarChart3} tone="success" hint="In selected period" />
        <StatCard label="Fines imposed" value={money(fines.totalImposed)} icon={Wallet} tone="warning" hint={`${money(fines.totalOutstanding)} outstanding`} />
        <StatCard label="Overdue now" value={overdue.length} tone={overdue.length ? 'danger' : 'success'} hint="Current loans past due" />
      </div>

      <div className="reports-grid reports-grid--wide">
        <Card
          title="Circulation"
          subtitle="Loans issued and returned per day"
          actions={<Badge tone="info">{circulationPoints.length} days</Badge>}
        >
          {circulationPoints.length === 0 ? <ChartEmpty label="No loans were issued or returned in this range." /> : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={circulationPoints} margin={{ top: 8, right: 12, left: -18, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tickFormatter={(d) => d?.slice(5)} minTickGap={22} />
                  <YAxis allowDecimals={false} />
                  <ChartTooltip />
                  <Legend />
                  <Line type="monotone" dataKey="issued" name="Issued" stroke="#365663" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="returned" name="Returned" stroke="#b47a38" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card
          title="Popular books"
          subtitle="Most borrowed in the period"
          actions={<Button size="sm" variant="outline" icon={Download} loading={downloading === 'popular'} onClick={() => download('popular')}>CSV</Button>}
        >
          {popular.length === 0 ? <ChartEmpty label="No borrow history in this range." /> : (
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={popular} layout="vertical" margin={{ top: 4, right: 16, left: 4, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" allowDecimals={false} />
                  <YAxis type="category" dataKey="title" width={120} tick={{ fontSize: 10 }} />
                  <ChartTooltip />
                  <Bar dataKey="borrowCount" name="Borrows" fill="#b47a38" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>

      <div className="reports-grid">
        <Card
          title="Fines summary"
          subtitle="Imposed, collected and outstanding by reason"
          actions={<Button size="sm" variant="outline" icon={Download} loading={downloading === 'fines'} onClick={() => download('fines')}>CSV</Button>}
        >
          {fineReasons.length === 0 ? <ChartEmpty label="No fines were imposed in this range." /> : (
            <>
              <div className="chart-box chart-box--sm">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={fineReasons} dataKey="imposed" nameKey="reason" innerRadius={45} outerRadius={78} paddingAngle={3}>
                      {fineReasons.map((entry, index) => <Cell key={entry.reason} fill={COLORS[index % COLORS.length]} />)}
                    </Pie>
                    <ChartTooltip formatter={(value) => money(value)} />
                    <Legend formatter={(v) => v.replace(/_/g, ' ').toLowerCase()} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="inline-list">
                {fineReasons.map((row) => (
                  <div className="inline-row" key={row.reason}>
                    <span>{row.reason.replace(/_/g, ' ').toLowerCase()} · {row.count}</span>
                    <span><strong>{money(row.imposed)}</strong> <span className="text-muted">/ {money(row.outstanding)} open</span></span>
                  </div>
                ))}
              </div>
            </>
          )}
        </Card>

        <Card title="Inventory" subtitle="Copies by status and titles with no free copies" actions={<Button size="sm" variant="outline" icon={Download} loading={downloading === 'inventory'} onClick={() => download('inventory')}>CSV</Button>}>
          {statusData.length === 0 ? <ChartEmpty label="No copies have been recorded." /> : (
            <div className="chart-box chart-box--sm">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={statusData} dataKey="value" nameKey="name" innerRadius={45} outerRadius={78} paddingAngle={3}>
                    {statusData.map((entry, index) => <Cell key={entry.name} fill={COLORS[index % COLORS.length]} />)}
                  </Pie>
                  <ChartTooltip />
                  <Legend formatter={(v) => v.toLowerCase()} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
          <p className="cell-detail">Total copies: <strong>{inventory.totalCopies ?? 0}</strong></p>
        </Card>
      </div>

      <Card
        title="Overdue loans"
        subtitle="Current overdue loans, oldest first"
        actions={<Button size="sm" variant="outline" icon={Download} loading={downloading === 'overdue'} onClick={() => download('overdue')}>CSV</Button>}
      >
        {overdue.length === 0 ? <EmptyState title="Nothing overdue" description="Every issued copy is within its due date." /> : (
          <Table>
            <thead><tr><th>Student</th><th>Book</th><th>Due</th><th>Days late</th><th>Accrued fine</th></tr></thead>
            <tbody>
              {overdue.map((row) => (
                <tr key={row.loanId}>
                  <td><strong>{row.studentName}</strong></td>
                  <td>{row.bookTitle}</td>
                  <td>{formatDate(row.dueDate)}</td>
                  <td><span className="text-danger">{row.daysLate}</span></td>
                  <td>{money(row.accruedFine)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <div className="reports-grid">
        <Card title="Waitlist pressure" subtitle="Longest queues and average wait to pickup">
          {(waitlist.books || []).length === 0 ? <EmptyState title="No active queues" description="No students are waiting for a book right now." /> : (
            <>
              <div className="inline-row"><span>Average wait (fulfilled entries)</span><strong>{waitlist.overallAverageWaitHours != null ? `${waitlist.overallAverageWaitHours.toFixed(1)} h` : 'No fulfilled entries'}</strong></div>
              <Table>
                <thead><tr><th>Book</th><th>Queue</th><th>Average wait</th></tr></thead>
                <tbody>
                  {(waitlist.books || []).map((row) => (
                    <tr key={row.bookId}>
                      <td><strong>{row.title}</strong></td>
                      <td>{row.queueLength}</td>
                      <td>{row.averageWaitHours != null ? `${row.averageWaitHours.toFixed(1)} h` : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </>
          )}
        </Card>

        <Card title="Leaderboard" subtitle="Students by points">
          {leaderboard.length === 0 ? <EmptyState title="No points yet" /> : (
            <Table>
              <thead><tr><th>Rank</th><th>Student</th><th>Points</th></tr></thead>
              <tbody>
                {leaderboard.map((row) => (
                  <tr key={row.studentId || row.rank}><td>{row.rank}</td><td><strong>{row.name}</strong></td><td>{row.points}</td></tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>

      <Card title="Books with no available copies" subtitle="Every copy is issued, reserved or unavailable">
        {(inventory.booksWithZeroAvailable || []).length === 0 ? <EmptyState title="Every title has a free copy" /> : (
          <Table>
            <thead><tr><th>Title</th><th>Author</th><th>Subject</th><th>Copies</th><th>Available</th></tr></thead>
            <tbody>
              {(inventory.booksWithZeroAvailable || []).map((book) => (
                <tr key={book.bookId}>
                  <td><strong>{book.title}</strong></td>
                  <td>{book.author}</td>
                  <td>{book.subject}</td>
                  <td>{book.totalCopies}</td>
                  <td className="text-danger">0</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}
