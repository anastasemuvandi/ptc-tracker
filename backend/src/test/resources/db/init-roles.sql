-- Test container: runtime login roles (production: deploy/db/init).
CREATE ROLE ptc_app LOGIN PASSWORD 'app-test';
CREATE ROLE ptc_auth LOGIN PASSWORD 'auth-test';
