package rw.gov.ptc.session.dto;

import rw.gov.ptc.config.dto.ConfigSnapshot;

/**
 * Quorum against the committee's own size, using the rule stored on the
 * session when it was convened.
 *
 * @param exactlyAtQuorum met with no margin: one departure breaks quorum
 */
public record Quorum(int committeeSize, int required, int present, boolean met, boolean exactlyAtQuorum,
                     int needed, String rule) {

    public static Quorum of(ConfigSnapshot.Fraction rule, int committeeSize, int present) {
        int required = rule.ceilOf(committeeSize);
        return new Quorum(committeeSize, required, present, present >= required, present == required,
                Math.max(0, required - present), rule.toString());
    }
}
