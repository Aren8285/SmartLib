import React, { useCallback, useEffect, useState } from 'react';
import { Crown, Medal, Star, Trophy } from '../components/icons';
import { getLeaderboard } from '../api';
import { useAuth } from '../context/AuthContext';
import { Alert, Button, EmptyState, PageHeader, SkeletonTable, Table } from '../components/ui';

export default function LeaderboardPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    // Ask for a wider list so the current student's rank can be highlighted even outside the top 10.
    getLeaderboard(100).then((data) => setRows(data || [])).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const top = rows.slice(0, 10);
  const myIndex = rows.findIndex((r) => r.studentId === user?.id || r.name === user?.name);
  const me = myIndex >= 0 ? { ...rows[myIndex], rank: myIndex + 1 } : null;
  const inTop = me && me.rank <= 10;

  const iconFor = (rank) => {
    if (rank === 1) return <Crown size={15} aria-hidden="true" />;
    if (rank <= 3) return <Medal size={15} aria-hidden="true" />;
    return <span className="mono">{rank}</span>;
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Reading community"
        title="Leaderboard"
        subtitle="Top readers this term by points earned from on-time returns and approved reflections."
      />

      {me && !inTop && (
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', borderColor: 'var(--brand)' }}>
          <Trophy size={22} aria-hidden="true" />
          <div style={{ flex: 1 }}>
            <strong>Your current rank: #{me.rank}</strong>
            <div className="cell-detail">Keep returning on time and submitting reflections to climb.</div>
          </div>
          <span className="badge badge--accent"><Star size={12} /> {me.points} points</span>
        </div>
      )}

      {error && <Alert tone="error" title="Could not load the leaderboard" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {loading ? <SkeletonTable rows={8} columns={3} /> : top.length === 0 ? (
        <EmptyState icon={Trophy} title="No rankings yet" description="Points are awarded when loans are returned and reflections approved." />
      ) : (
        <Table>
          <thead><tr><th>Rank</th><th>Student</th><th>Points</th></tr></thead>
          <tbody>
            {top.map((row, index) => {
              const isMe = row.studentId === user?.id || row.name === user?.name;
              return (
                <tr key={`${row.name}-${index}`} style={isMe ? { background: 'var(--brand-soft)' } : undefined}>
                  <td><span className="row" style={{ gap: 6 }}>{iconFor(index + 1)}</span></td>
                  <td>
                    <strong>{row.name}</strong>
                    {isMe && <span className="badge badge--brand" style={{ marginLeft: 8 }}>You</span>}
                  </td>
                  <td><span className="row" style={{ gap: 5 }}><Star size={14} style={{ color: 'var(--accent)' }} aria-hidden="true" /> <strong>{row.points}</strong></span></td>
                </tr>
              );
            })}
          </tbody>
        </Table>
      )}

      <p className="cell-detail">Only names and points are shown. Emails and account details stay private.</p>
    </div>
  );
}
