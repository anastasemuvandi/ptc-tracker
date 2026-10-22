-- A committee's name is unique within its institution, dissolved committees
-- included, ignoring case and surrounding spaces. Existing duplicates must be
-- renamed by the institution first: names are not changed here.
DO $$
DECLARE
    duplicates text;
BEGIN
    SELECT string_agg(format('"%s" in %s', d.name, i.abbreviation), '; ')
      INTO duplicates
      FROM (SELECT institution_id, min(btrim(name)) AS name
              FROM committee
             GROUP BY institution_id, lower(btrim(name))
            HAVING count(*) > 1) d
      JOIN institution i ON i.id = d.institution_id;
    IF duplicates IS NOT NULL THEN
        RAISE EXCEPTION 'Committees share a name; rename them before upgrading: %', duplicates;
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_committee_name ON committee (institution_id, lower(btrim(name)));
