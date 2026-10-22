-- Article 5 of the 2020 order: a committee is appointed for three years,
-- renewable only once, and more than half of its members may not serve two
-- consecutive terms. Both limits now block (the Secretary, a permanent
-- member, is outside them). An institution may still set either to false.
INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason, legal_reference) VALUES
(NULL, 'term.limitBlocks',            'true', '2026-10-22', 'A member''s term is renewable only once', 'Article 5, 2020 order'),
(NULL, 'term.consecutiveShareBlocks', 'true', '2020-01-01', 'More than half of the members may not serve two consecutive terms', 'Article 5, 2020 order');
