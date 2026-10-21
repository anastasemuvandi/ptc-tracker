#!/usr/bin/env python3
"""End-to-end smoke test against a running deployment (docker compose).

Walks the core committee workflow through nginx exactly as a browser would:
platform admin -> institution -> invited users (with mandatory MFA) ->
committee constitution -> contract and type C file -> session, attendance,
quorum -> conflict-of-interest declarations -> votes with dissent -> issue ->
CBM response -> minutes generation, acknowledgement, finalisation and .docx.

Requires mail disabled (invitation links are read from the backend log).
Usage: deploy/scripts/smoke-test.py [base_url]   (default http://localhost:8088)
"""
import base64, hashlib, hmac, http.cookiejar, io, json, re, struct, subprocess, sys, time, urllib.error
import urllib.request, uuid, zipfile
from datetime import date, datetime, timedelta, timezone

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8088"
DEPLOY = __file__.rsplit("/scripts/", 1)[0]
RUN = uuid.uuid4().hex[:6]
PASSWORD = "Smoke-Test-Passw0rd!"
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def totp(secret, step):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    h = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return "%06d" % ((struct.unpack(">I", h[o:o + 4])[0] & 0x7FFFFFFF) % 1000000)


class Client:
    def __init__(self, name):
        self.name = name
        self.token = None
        self.secret = None
        self.password = PASSWORD
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.last_step = 0

    def call(self, method, path, body=None, expect=(200, 201, 204), raw=False, key=None):
        headers = {"Content-Type": "application/json", "X-PTC-Client": "web"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if method != "GET" and path.startswith("/api/") and not path.startswith("/api/auth/"):
            headers["Idempotency-Key"] = key or str(uuid.uuid4())
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(req) as r:
                status, payload = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, payload = e.code, e.read()
        if status not in expect:
            raise AssertionError(f"{self.name} {method} {path} -> {status}: {payload[:400]!r}")
        if raw:
            return status, payload
        return json.loads(payload) if payload else None

    def upload(self, path, name, content_type, content):
        """multipart/form-data with one part named "file"."""
        boundary = uuid.uuid4().hex
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
                f"Content-Type: {content_type}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(BASE + path, data=body, method="POST", headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}", "X-PTC-Client": "web",
            "Authorization": "Bearer " + self.token, "Idempotency-Key": str(uuid.uuid4())})
        try:
            with self.opener.open(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def next_code(self):
        """A TOTP code for a step not used before (the server refuses replays)."""
        while True:
            now = int(time.time()) // 30
            for step in (now, now + 1):
                if step > self.last_step:
                    self.last_step = step
                    return totp(self.secret, step)
            time.sleep(2)

    def step_up(self):
        body = {"code": self.next_code()} if self.secret else {"password": self.password}
        self.call("POST", "/api/auth/step-up", body)


def invite_link(email):
    logs = subprocess.run(["docker", "compose", "logs", "--no-log-prefix", "backend"], cwd=DEPLOY,
                          capture_output=True, text=True).stdout
    idx = logs.rfind("to=" + email)
    assert idx >= 0, "no invitation mail logged for " + email
    return re.search(r"accept-invite\?token=([A-Za-z0-9_-]+)", logs[idx:]).group(1)


def onboard(name, email):
    c = Client(name)
    c.call("POST", "/api/auth/invitations/accept", {"token": invite_link(email), "password": PASSWORD})
    res = c.call("POST", "/api/auth/login", {"email": email, "password": PASSWORD})
    if res["status"] == "MFA_ENROLMENT_REQUIRED":
        enrol = c.call("POST", "/api/auth/mfa/enrol/start", {"challenge": res["challenge"]})
        c.secret = enrol["secret"]
        res = c.call("POST", "/api/auth/mfa/enrol/confirm", {"challenge": res["challenge"], "code": c.next_code()})
        assert len(res["recoveryCodes"]) == 10
    c.token = res["accessToken"]
    assert any(ck.name == "ptc_rt" for ck in c.jar), "refresh cookie missing"
    return c


def check(label, cond):
    print(("  ok  " if cond else "  FAIL") + "  " + label)
    if not cond:
        raise SystemExit(1)


print(f"Smoke test against {BASE} (run {RUN})")

# Platform administrator: bootstrap invitation from the log.
pa = onboard("platform-admin", "platform.admin@example.rw")
inst = pa.call("POST", "/api/platform/institutions", {
    "name": f"Smoke Ministry {RUN}", "abbreviation": f"SM{RUN}".upper(), "institutionType": "MINISTRY",
    "supervisingAuthority": "MINECOFIN", "adminEmail": f"admin.{RUN}@example.rw", "adminFullName": "Inst Admin"})
check("platform admin created institution", "id" in inst)
metrics = pa.call("GET", "/api/platform/metrics")
check("platform metrics are counts only", all("title" not in m for m in metrics))

admin = onboard("inst-admin", f"admin.{RUN}@example.rw")
people = {}
for key, roles, po in [("cbm", ["CBM"], False), ("chair", [], False), ("vice", [], False),
                       ("sec", [], True), ("m1", [], False), ("m2", [], False)]:
    email = f"{key}.{RUN}@example.rw"
    uid = admin.call("POST", "/api/users", {"email": email, "fullName": f"{key.upper()} Person", "roles": roles,
                                            "procurementOfficer": po, "locale": "en"})["id"]
    people[key] = (uid, email)

cbm = onboard("cbm", people["cbm"][1])
check("CBM had mandatory MFA enrolment", cbm.secret is not None)
committee = cbm.call("POST", "/api/committees", {"name": "PTC A", "code": "PTCA", "size": 5})
cid = committee["id"]
today = date.today().isoformat()
r = cbm.call("POST", f"/api/committees/{cid}/members", {"userId": people["cbm"][0], "role": "MEMBER",
                                                         "termStart": today}, expect=(422,))
check("CBM barred from committee membership", r["code"] == "composition.cbm_cannot_be_member")
for key, role in [("chair", "CHAIRPERSON"), ("vice", "VICE_CHAIRPERSON"), ("sec", "SECRETARY"),
                  ("m1", "MEMBER"), ("m2", "MEMBER")]:
    cbm.call("POST", f"/api/committees/{cid}/members", {"userId": people[key][0], "role": role, "termStart": today})
comp = cbm.call("GET", f"/api/committees/{cid}/composition")
check("composition compliant after appointments", comp["compliant"])

members = {k: onboard(k, people[k][1]) for k in ["chair", "vice", "sec", "m1", "m2"]}
check("officers enrolled MFA, members did not", members["chair"].secret and not members["m1"].secret)
sec, chair = members["sec"], members["chair"]

contract = sec.call("POST", "/api/contracts", {
    "contractRef": f"CT-{RUN}", "title": "Rehabilitation works", "procurementType": "WORKS", "currency": "RWF",
    "originalValue": "500000000.00", "originalCompletionDate": (date.today() + timedelta(days=200)).isoformat()})
f = sec.call("POST", "/api/files", {"committeeId": cid, "itemType": "C", "contractId": contract["id"],
                                    "title": "Addendum 1 — additional drainage", "dateReceived": today})
check("file numbered from per-institution counter", re.fullmatch(r"F-\d{4}/\d{4}", f["fileNumber"]) is not None)
amend = sec.call("POST", f"/api/files/{f['id']}/amendment", {
    "requestedValue": "60000000.00", "description": "Additional drainage works", "affectsSubstance": False,
    "substanceJustification": "Same works, same site, quantities only", "deliverablesChange": False})
assessment = sec.call("GET", f"/api/files/{f['id']}/amendment")["assessment"]
check("amendment assessed at 12% (entity route)",
      str(assessment["resultingPct"]).startswith("12") and assessment["route"] == "ENTITY")

when = (datetime.now(timezone.utc) + timedelta(days=7)).isoformat()
r = cbm.call("POST", "/api/sessions", {"committeeId": cid, "kind": "ORDINARY", "scheduledAt": when,
                                       "location": "Board room"}, expect=(403,))
check("CBM cannot schedule sessions", True)
s = sec.call("POST", "/api/sessions", {"committeeId": cid, "kind": "ORDINARY", "scheduledAt": when,
                                       "location": "Board room"})
sid = s["id"]
r = sec.call("POST", f"/api/sessions/{sid}/agenda", {"itemType": "A", "fileId": f["id"]}, expect=(422,))
check("agenda item type must match the file type", r["code"].startswith("agenda."))
item = sec.call("POST", f"/api/sessions/{sid}/agenda", {"itemType": "C", "fileId": f["id"],
                                                         "activities": ["AMENDMENT_REVIEW", "RECOMMENDATION"]})
sec.call("POST", f"/api/sessions/{sid}/status", {"status": "IN_PROGRESS"})
view = sec.call("GET", f"/api/sessions/{sid}")
by_user = {m["userId"]: m["membershipId"] for m in view["members"]}
for i, k in enumerate(["chair", "vice", "sec"]):
    q = sec.call("PUT", f"/api/sessions/{sid}/attendance/{by_user[people[k][0]]}",
                 {"status": "PRESENT", "clientRecordedAt": datetime.now(timezone.utc).isoformat()})["quorum"]
check("exactly at quorum flagged at 3 of 5", q["met"] and q["exactlyAtQuorum"])
for k in ["m1", "m2"]:
    q = sec.call("PUT", f"/api/sessions/{sid}/attendance/{by_user[people[k][0]]}",
                 {"status": "PRESENT", "clientRecordedAt": datetime.now(timezone.utc).isoformat()})["quorum"]
check("quorum met with 5 present", q["present"] == 5 and not q["exactlyAtQuorum"])

iid = item["id"]
r = members["m1"].call("POST", f"/api/agenda-items/{iid}/declarations", {"hasInterest": False}, expect=(401,))
check("declaration without step-up refused", r["code"] == "auth.step_up_required")
for k, c in members.items():
    c.step_up()
    interest = k == "m2"
    c.call("POST", f"/api/agenda-items/{iid}/declarations",
           {"hasInterest": interest, "interestKind": "INDIRECT" if interest else None,
            "details": "Sibling works for the contractor" if interest else None})
d = sec.call("POST", f"/api/agenda-items/{iid}/decision", {
    "recommendationCode": "APPROVE_AMENDMENT", "recommendationText": "Approve addendum 1 within entity limits",
    "contractValue": "60000000.00", "currency": "RWF"})
d = sec.call("POST", f"/api/decisions/{d['id']}/open-voting")
check("threshold recalculated after interest exclusion (4 eligible -> 3 required)",
      d["eligibleVoters"] == 4 and d["requiredVotes"] == 3)
r = members["m2"].call("POST", f"/api/decisions/{d['id']}/votes", {"choice": "FOR"}, expect=(403, 422))
check("member who declared an interest cannot vote", True)
r = members["m1"].call("POST", f"/api/decisions/{d['id']}/votes", {"choice": "AGAINST"}, expect=(400, 422))
check("dissent requires a reason", True)
for k in ["chair", "vice", "sec"]:
    members[k].step_up()
    members[k].call("POST", f"/api/decisions/{d['id']}/votes", {"choice": "FOR"})
members["m1"].call("POST", f"/api/decisions/{d['id']}/votes",
                   {"choice": "AGAINST", "dissentReason": "Drainage should be retendered as a separate lot"})
d = sec.call("POST", f"/api/decisions/{d['id']}/close-voting")
check("decision adopted 3-1", d["status"] == "ADOPTED" and d["votesFor"] == 3 and d["votesAgainst"] == 1)
chair.step_up()
d = chair.call("POST", f"/api/decisions/{d['id']}/issue")
check("recommendation issued by chair with step-up", d["status"] == "ISSUED")
sec.call("POST", f"/api/decisions/{d['id']}/submit-to-cbm")
pending = cbm.call("GET", "/api/cbm/pending")
check("CBM sees the pending recommendation", any(p.get("id") == d["id"] or p.get("decisionId") == d["id"] for p in pending))
cbm.call("POST", f"/api/decisions/{d['id']}/cbm-response", {"response": "APPROVED", "reasons": None})
timeline = sec.call("GET", f"/api/contracts/{contract['id']}/timeline")
check("approved amendment counted in contract timeline", str(timeline["cumulativeApproved"]).startswith("60000000"))

r = sec.call("POST", f"/api/sessions/{sid}/status", {"status": "HELD"}, expect=(422,))
check("session cannot close with unrecorded activities", r["code"] == "session.activities_unrecorded")
acts = {a["activityCode"]: a for a in sec.call("GET", f"/api/sessions/{sid}")["agenda"][0]["activities"]}
check("adopted recommendation completes its activity", acts["RECOMMENDATION"]["status"] == "COMPLETED")
sec.call("POST", f"/api/sessions/{sid}/activities/{acts['AMENDMENT_REVIEW']['id']}", {"status": "COMPLETED"})
sec.call("POST", f"/api/sessions/{sid}/status", {"status": "HELD"})
m = sec.call("POST", f"/api/sessions/{sid}/minutes/generate")
mid = (m.get("minutes") or m)["id"]
sec.call("POST", f"/api/minutes/{mid}/submit-for-review")
r = sec.call("POST", f"/api/minutes/{mid}/finalise", expect=(422,))
check("finalisation blocked until all present members acknowledge", True)
for c in members.values():
    c.call("POST", f"/api/minutes/{mid}/acknowledge", {"comment": None})
sec.call("POST", f"/api/minutes/{mid}/finalise")
status, docx = sec.call("GET", f"/api/minutes/{mid}/docx", raw=True)
text = zipfile.ZipFile(io.BytesIO(docx)).read("word/document.xml").decode()
plain = re.sub(r"<[^>]+>", "", text)
check(".docx downloaded and references the file number", f["fileNumber"] in plain)
check(".docx carries the dissent reason", "retendered as a separate lot" in plain)
check(".docx contains no UUIDs", UUID_RE.search(plain) is None)

# Requests from outside the committee: onboarded by the administrator, documents through nginx.
admin.call("POST", "/api/users", {"email": f"req.{RUN}@example.rw", "fullName": "Site Engineer", "locale": "en",
                                  "roles": ["REQUESTER"], "requesterCategory": "LONG_TERM"})
requester = onboard("requester", f"req.{RUN}@example.rw")
rq = requester.call("POST", "/api/requests", {"itemType": "H", "title": "Advice on site handover delay",
                                              "description": "The site was handed over six weeks late; advise on the way forward."})
big = b"%PDF-1.4\n" + b"0" * (8 * 1024 * 1024)
status, doc = requester.upload(f"/api/requests/{rq['id']}/documents", "handover-report.pdf", "application/pdf", big)
check("8 MB supporting document accepted through nginx", status == 200)
requester.call("POST", f"/api/requests/{rq['id']}/submit", {"declarationConfirmed": True})
check("officers see the submitted request", any(x["id"] == rq["id"] for x in sec.call("GET", "/api/requests/inbox")))
status, content = sec.call("GET", f"/api/requests/{rq['id']}/documents/{doc['id']}", raw=True)
check("officer downloads the document intact", content == big)
sec.call("POST", f"/api/requests/{rq['id']}/accept", {"committeeId": cid})
mine = requester.call("GET", f"/api/requests/{rq['id']}")
check("requester follows the accepted request as a file", mine["status"] == "ACCEPTED" and mine["file"]["fileNumber"].startswith("F-"))
status, payload = requester.call("GET", "/api/files", expect=(200, 403), raw=True)
check("requester sees no committee files", status == 403 or json.loads(payload) == [])

# Tenant isolation through the full stack: a second institution sees nothing.
inst2 = pa.call("POST", "/api/platform/institutions", {
    "name": f"Other Agency {RUN}", "abbreviation": f"OA{RUN}".upper(), "institutionType": "AGENCY",
    "adminEmail": f"admin2.{RUN}@example.rw", "adminFullName": "Other Admin"})
other = onboard("other-admin", f"admin2.{RUN}@example.rw")
other.call("GET", f"/api/files/{f['id']}", expect=(403, 404))
check("other institution cannot read the file", True)
check("other institution's register is empty", other.call("GET", "/api/files") == [])
other.call("GET", "/api/files/not-a-uuid", expect=(400,))
check("malformed UUID rejected with 400", True)

# Refresh rotation through nginx; reuse of a consumed cookie revokes the family.
old = [ck for ck in sec.jar if ck.name == "ptc_rt"][0].value
sec.call("POST", "/api/auth/refresh")
replay = Client("replay")
replay.jar.set_cookie(http.cookiejar.Cookie(0, "ptc_rt", old, None, False, "localhost.local", False, False,
                                            "/api/auth", True, False, None, False, None, None, {}))
replay.call("POST", "/api/auth/refresh", expect=(401,))
sec.call("POST", "/api/auth/refresh", expect=(401,))
check("refresh-token reuse revoked the whole session family", True)
print("All smoke checks passed.")
