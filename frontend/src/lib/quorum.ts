import type { Quorum, Threshold } from '@/api/types';

export type QuorumTone = 'met' | 'exact' | 'notMet';

/**
 * Quorum indicator state. "exact" is a distinct warning: one more absence
 * (or one member stepping out for a conflict of interest) breaks quorum.
 */
export function quorumTone(q: Pick<Quorum, 'present' | 'required'>): QuorumTone {
  if (q.present < q.required) return 'notMet';
  if (q.present === q.required) return 'exact';
  return 'met';
}

/** Recomputes quorum locally from the attendance marks shown on screen. */
export function liveQuorum(base: Quorum, present: number): Quorum {
  return {
    ...base,
    present,
    met: present >= base.required,
    exactlyAtQuorum: present === base.required,
    needed: Math.max(0, base.required - present),
  };
}

export function parseRule(rule: string): { numerator: number; denominator: number } {
  const [n, d] = rule.split('/').map((x) => Number(x.trim()));
  return { numerator: n || 3, denominator: d || 5 };
}

/**
 * Votes required for adoption: the configured fraction of members PRESENT
 * and eligible (i.e. after excluding those who declared an interest).
 * Mirrors the server rule for display; the server's figure is authoritative.
 */
export function requiredVotes(present: number, excluded: number, rule: string): Threshold {
  const { numerator, denominator } = parseRule(rule);
  const eligible = Math.max(0, present - excluded);
  return { present, excluded, eligible, required: Math.ceil((eligible * numerator) / denominator), rule };
}

export function voteOutlook(t: Threshold, votesFor: number, votesCast: number): {
  remaining: number; adoptedAlready: boolean; stillPossible: boolean;
} {
  const outstanding = Math.max(0, t.eligible - votesCast);
  return {
    remaining: Math.max(0, t.required - votesFor),
    adoptedAlready: votesFor >= t.required,
    stillPossible: votesFor + outstanding >= t.required,
  };
}
