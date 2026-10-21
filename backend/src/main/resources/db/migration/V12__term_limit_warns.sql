-- Past members may join the next committee: going beyond term.maxRenewals
-- consecutive renewals warns, and blocks only where an institution sets
-- term.limitBlocks to true.
INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason) VALUES
(NULL, 'term.limitBlocks', 'false', '2020-01-01', 'Platform default: exceeding the renewal limit only warns');
