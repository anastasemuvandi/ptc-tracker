import { describe, it, expect } from 'vitest';
import { liveQuorum, quorumTone, requiredVotes, voteOutlook } from '@/lib/quorum';
import type { Quorum } from '@/api/types';

const base: Quorum = { committeeSize: 5, required: 3, present: 0, met: false, exactlyAtQuorum: false, needed: 3, rule: '3/5' };

describe('quorum display', () => {
  it('distinguishes met, exactly-at-quorum and not met', () => {
    expect(quorumTone({ present: 2, required: 3 })).toBe('notMet');
    expect(quorumTone({ present: 3, required: 3 })).toBe('exact');
    expect(quorumTone({ present: 4, required: 3 })).toBe('met');
  });

  it('recomputes live from local attendance marks', () => {
    expect(liveQuorum(base, 2)).toMatchObject({ met: false, needed: 1, exactlyAtQuorum: false });
    expect(liveQuorum(base, 3)).toMatchObject({ met: true, needed: 0, exactlyAtQuorum: true });
    expect(liveQuorum({ ...base, committeeSize: 7, required: 5 }, 6)).toMatchObject({ met: true, exactlyAtQuorum: false });
  });
});

describe('voting threshold', () => {
  it('is a share of members present and eligible, not of committee size', () => {
    // 5 present, rule 3/5 → 3 votes required
    expect(requiredVotes(5, 0, '3/5')).toEqual({ present: 5, excluded: 0, eligible: 5, required: 3, rule: '3/5' });
    // 4 present → ceil(2.4) = 3
    expect(requiredVotes(4, 0, '3/5').required).toBe(3);
  });

  it('is recalculated when members are excluded for a declared interest', () => {
    const t = requiredVotes(5, 2, '3/5');
    expect(t.eligible).toBe(3);
    expect(t.required).toBe(2); // ceil(1.8)
  });

  it('reports whether adoption is still reachable', () => {
    const t = requiredVotes(5, 0, '3/5');
    expect(voteOutlook(t, 1, 3)).toEqual({ remaining: 2, adoptedAlready: false, stillPossible: true });
    expect(voteOutlook(t, 1, 4)).toMatchObject({ stillPossible: false });
    expect(voteOutlook(t, 3, 3)).toMatchObject({ adoptedAlready: true, remaining: 0 });
  });
});
