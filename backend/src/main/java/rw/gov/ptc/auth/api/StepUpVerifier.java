package rw.gov.ptc.auth.api;

/**
 * Guard for legally significant acts (vote, conflict-of-interest declaration,
 * recommendation issue): the user must have re-entered TOTP (or password if
 * MFA is not enabled) within the configured window, in this login session.
 */
public interface StepUpVerifier {

    /**
     * @return when the qualifying step-up happened
     * @throws rw.gov.ptc.common.ApiException 401 step_up_required otherwise
     */
    java.time.Instant requireRecent(AuthPrincipal principal, String act);
}
