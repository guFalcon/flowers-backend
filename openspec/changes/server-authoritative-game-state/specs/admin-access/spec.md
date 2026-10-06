## Purpose

Protects the admin endpoints of the backend with a shared admin token, and describes how the
frontend admin mode obtains that token and sends it along.

## ADDED Requirements

### Requirement: Admin endpoints require the admin token
The backend SHALL read the admin token from the environment variable `FLOWERS_ADMIN_TOKEN`. A
request to an admin endpoint (every path under `/api/admin/`) SHALL be executed only if it carries
the header `X-Admin-Token` whose value equals the configured token exactly. Otherwise the backend
SHALL respond with `403`, SHALL NOT perform the action and SHALL NOT publish any event. If no token
is configured (variable unset or blank), every admin request SHALL be answered with `403`.

#### Scenario: Correct token
- **WHEN** `FLOWERS_ADMIN_TOKEN` is `s3cret` and a client posts to `/api/admin/restart` with header `X-Admin-Token: s3cret`
- **THEN** the action is performed and the response is `200`

#### Scenario: Missing or wrong token
- **WHEN** `FLOWERS_ADMIN_TOKEN` is `s3cret` and a client posts to `/api/admin/restart` without the header or with `X-Admin-Token: true`
- **THEN** the response is `403`, the flowers stay the same and no `levelRestarted` event is published

#### Scenario: No token configured
- **WHEN** `FLOWERS_ADMIN_TOKEN` is not set and a client posts to `/api/admin/restart` with any `X-Admin-Token`
- **THEN** the response is `403`

### Requirement: Browsers may send the admin token cross-origin
The backend's CORS configuration SHALL allow the request header `X-Admin-Token` for the allowed
frontend origins, so a browser preflight for an admin request from the frontend succeeds.

#### Scenario: Preflight from the frontend
- **WHEN** a browser sends `OPTIONS /api/admin/restart` with `Origin: https://flowers.htl.dev` and `Access-Control-Request-Headers: x-admin-token`
- **THEN** the response allows that header for that origin

### Requirement: Frontend admin mode sends the token
The frontend SHALL show the admin panel when the page URL has a non-empty query parameter `admin`,
and SHALL treat its value as the admin token. Admin requests SHALL carry that value in the header
`X-Admin-Token`. If an admin request is answered with `403`, the panel SHALL show that the admin
token was rejected and SHALL NOT reload the level.

#### Scenario: Admin restarts with the right token
- **WHEN** the page is opened with `?admin=<correct token>` and the admin clicks "Restart Level"
- **THEN** the restart request carries `X-Admin-Token: <correct token>` and the level is reloaded

#### Scenario: Wrong token
- **WHEN** the page is opened with `?admin=true` (not the token) and the admin clicks "Restart Level"
- **THEN** the panel shows that the admin token was rejected and the level stays as it is
