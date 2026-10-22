## Introduction

PTC Tracker helps a Public Tender Committee follow its own work: which files are assigned, what stage each has reached, what is overdue, and what the committee decided and why. It runs in any browser and can be installed on a phone or computer like an app. It is available in English, Kinyarwanda and French.

**Umucyo remains the official record.** PTC Tracker is internal working material. It never holds bid documents, bidder submissions, prices or evaluation scores, and you cannot publish a tender, open bids or award a contract here. Tenders, contracts and bidders are always referred to by their Umucyo reference.

This guide is for everyone who uses the system: administrators, the Chief Budget Manager (CBM), committee officers (Chairperson, Vice-Chairperson, Secretary), committee members and internal auditors. Read *Getting started* and *Roles at a glance* first, then the section for your role.

The screenshots show a fictional institution, the Rwanda Demo Agency; all names, references and figures in them are invented.

## Getting started

You join by invitation only; there is no self-registration. Your institution administrator sends the invitation to your work email.

### First sign-in

1. Open the link in the invitation email. It works once and expires after 72 hours; ask your administrator to resend it if it has expired.
2. Choose a password: at least 12 characters, using three of upper case, lower case, digits and symbols. It must not contain your email name.
3. Sign in with your email and the new password.

![The sign-in page, with the User guide link and language choice](/guide/01-login.png)

### Two-step verification

Two-step verification is **mandatory** for administrators, the CBM, Chairperson, Vice-Chairperson and Secretary. Your institution may also require it for members. If it applies to you, the first sign-in shows a set-up screen:

1. Install an authenticator app on your phone (Google Authenticator, Microsoft Authenticator or Aegis).
2. Scan the QR code on screen, or type the secret printed under it.
3. Enter the 6-digit code the app shows.
4. Save the 10 **recovery codes** shown next: download or print them and keep them somewhere safe. They are shown only once. Each code can replace an app code once if you lose your phone.

![Two-step verification set-up: scan the QR code, or type the key, then enter the 6-digit code](/guide/02-mfa-setup.png)

After that, each sign-in asks for your password and then the current code from the app. If you lose both phone and recovery codes, your institution administrator can reset two-step verification so you can set it up again.

### Confirming your identity for important acts

Casting a vote, declaring a conflict of interest and issuing a recommendation carry personal legal responsibility. For these, the system asks for a fresh code from your app (or your password, if you have no two-step verification) even when you are already signed in. A confirmation stays valid for 5 minutes, so several votes in a row need only one code.

### Installing the app

- **Android (Chrome):** accept the *Install* prompt, or use the browser menu → *Install app*.
- **iPhone / iPad (Safari, iOS 16.4 or later):** tap *Share* → *Add to Home Screen*. Notifications on iPhone only work once the app is added to the Home Screen.
- **Computer:** use the install icon in the address bar of Chrome or Edge.

The install prompt appears once; if you dismiss it, it does not return. You stay signed in for up to 14 days of inactivity, including after closing and reopening the installed app.

### Language, password and notifications

Open **Settings** to change your language (English, Kinyarwanda, French), change your password, and turn on phone notifications. Email remains the guaranteed channel for every reminder; phone notifications are an extra.

### Working offline

When your connection drops, an offline banner appears. Agendas, member lists and file summaries you have already opened stay readable. Only two things can be recorded offline and are sent automatically when you reconnect: **attendance marks** and **absence apologies**. Votes, declarations, evaluation outcomes and recommendations need a live connection: the system blocks them offline rather than pretend they were recorded. If someone else changed the same attendance mark meanwhile, you are asked which value to keep (**Sync conflicts**).

A banner tells you when a new version of the app is available; choose *Reload* to update.

## Roles at a glance

What you see in the menu depends on your role. You may hold different roles on different committees, for example Chairperson of one and Member of another.

| Role | Main tasks | Two-step verification |
| --- | --- | --- |
| Platform administrator | Creates institutions and invites each one's first administrator; sees operational counts only, never file content | Mandatory |
| Institution administrator | Invites users and sets their roles; institution settings, minutes template, rules and thresholds, holidays | Mandatory |
| Chief Budget Manager (CBM) | Creates committees, appoints and replaces members, reassigns files and assigns their procurement officer, responds to recommendations. Never a committee member, never votes | Mandatory |
| Chairperson / Vice-Chairperson | Register files, run sessions, decide tabling requests, open voting, issue recommendations | Mandatory |
| Secretary (procurement officer) | Registers files, schedules sessions, keeps attendance, records evaluation progress, drafts minutes | Mandatory |
| Member | Declares interests, votes, records dissent, acknowledges minutes, asks for files to be tabled | Set by institution |
| Read-only (internal audit) | Reads everything in the institution, including the audit log; cannot change anything | Optional |
| Requester | An employee outside the committee who submits requests (contract amendment, extension, request for a recommendation) with supporting documents and follows their progress; sees nothing else | Optional |

## Administrators

### Platform administrator

Under **Platform**, create an institution by giving its name, abbreviation, type, supervising authority and the email of its first institution administrator, who then receives an invitation. The same page lists each institution's counts (committees, active users, open and overdue files, sessions held in the last 30 days); file content is never shown here. You can also suspend an institution and set platform-wide default rules.

If an institution administrator loses both their authenticator and their recovery codes, and no other administrator of that institution can help, open **Administrators** on that institution's row and choose **Reset two-step verification**. Confirm the person's identity outside the system first (for example by phone call to a known number); the system asks for a fresh code from your own app. Their sessions end and they set up two-step verification again at their next sign-in. Only institution administrators can be reset from here, and each reset is recorded in that institution's audit log.

### Institution administrator

- **Users:** invite people by email, giving their name, job title and language. Tick *procurement officer* where it applies, and record any office they hold (head of institution, head of finance, internal auditor, legal advisor, logistics officer, lowest job category) so committee appointments can be checked against the exclusion rules. Assign the institution roles *CBM*, *Institution administrator* or *Read-only*. Committee roles are given by the CBM, not here. From the same list you can resend an invitation, disable an account, or **reset two-step verification** for someone who lost their phone.
- **Requesters (onboarding and offboarding):** an employee who needs to bring a request to the committee is invited like any user, with the role *Requester*. Choose **short term**, with the date access ends, or **long term**. The invitation email lets them set a password; their username is their email. Short-term access ends automatically after its last day; to offboard anyone earlier, disable the account. Use *Manage* to change the category or extend the end date.
- **Institution:** name, letterhead text and the font family and size used in minutes. Upload your own **minutes template** (.docx, under 5 MB): its headers, footers and styles are kept and the minutes are written into its body.
- **Rules & thresholds:** every legal threshold, period and rule is a setting, not fixed in the software: committee sizes, quorum and voting fractions, notice periods, cure period, amendment ceilings, term length and so on. Each change needs an **effective date**, a reason and, ideally, the legal reference (for example the Ministerial Order article). Changes cannot be backdated, and the full history is kept. Past decisions are always shown with the rules that applied when they were taken.
- **Holidays:** the national public holiday calendar is shared by all institutions. Islamic holidays are entered as provisional and should be confirmed when government announces them. If your institution observes a different calendar, switch on *holiday override* in Institution settings and add or remove days here. Working-day deadlines (cure period, notice period, target dates) use this calendar.

![Rules and thresholds: each rule shows its value, whether it is the platform default or your institution's, and its effective date](/guide/11-rules.png)

## Chief Budget Manager

The CBM constitutes committees and decides on their recommendations, but is never a committee member: the system refuses to appoint the CBM to any committee, and the CBM never votes or counts toward quorum.

### Creating a committee

Under **Committees**, create a committee by giving its **name** (e.g. "PTC A") and the **date its period starts**. The name must differ from that of every other committee of your institution, including dissolved ones. The start may be today, in the past or in the future; the period lasts 3 years and its end date is set automatically (the form shows it). Members you appoint before a scheduled start begin their term on that date. The short code used in session numbers (e.g. "PTCA 2026/014") is derived from the name.

A committee is appointed for a **period** of three years (configurable). The committee keeps its name across periods: when it is appointed again, choose *New period* on the committee page, and the period history is kept under *Periods*. Replacing all members also starts a new period. A badge warns when the period is about to end or has ended.

Members' terms end with the period they are appointed in. When a new period starts, members' seats end, except the Secretary's: the procurement officer is a permanent member and continues automatically. Anyone from the past committee may be appointed again, within the limits of Article 5 of the 2020 order:

- **A term is renewable only once.** Appointing someone to a third consecutive term is refused.
- **More than half of the members may not serve two consecutive terms.** At a renewal, at most half of the seats may go to members continuing from the previous term: 2 of 5, 3 of 7, 1 of 3. The rest must go to new people. The Secretary, a permanent member, is not counted.

The *Composition* tab and the appointment form show how many members continue, the most allowed and how many seats must go to new members. An appointment that would break either limit is refused with the reason.

### Appointing members

Open the committee and choose *Appoint member*, picking the person, their role (Chairperson, Vice-Chairperson, Secretary or Member) and the term start; the term end defaults to the configured term length. As you choose, the composition check runs live and explains any problem. The system **blocks** an appointment when:

- the person holds an excluded office (for example the head of the institution, head of finance or internal auditor);
- procurement officers would form a majority of the committee;
- the role is already filled (one Chairperson, one Vice-Chairperson, one Secretary);
- the committee is already at its size, or the person is already a member;
- the person would exceed the allowed number of consecutive terms.

It **warns** without blocking when the committee is below its size, has no procurement officer, has a Secretary who is not a procurement officer, or would have more than half its members serving consecutive terms. You are alerted before any member's term expires.

![A committee page: members with their role, term and consecutive term; tabs for composition, tabling requests and replacements](/guide/10-committee.png)

### Replacing members

Use *Replace members* to replace one member or the whole committee (all members except the Secretary). A written reason is mandatory, because replacement requires sound reasons. When **all** members are replaced, the system creates a tracked task to notify the regulator within the configured period (7 days by default). Mark it done under **Tasks** with the reference of your letter.

### Assigning files

You can register files for a committee, move a file to another committee (with a reason, which is logged) and assign a procurement officer to each file for the financial year. You can also refer any other procurement matter to a committee as a *referred matter* (type H), with the referral reference and a stated reason. You cannot schedule a committee's sessions.

### Responding to recommendations

**Recommendations** lists every recommendation issued to you and awaiting your decision. Open one to read the analysis, votes and any dissent, then record your response:

- **Approved:** the file is closed.
- **Returned for correction:** written reasons are required; the file goes back to the committee.
- **Other:** written reasons are required.

Your response is a separate, logged act. It never changes the committee's own decision record.

## Requesters

Under **My requests**, choose *New request*: the type (contract amendment, contract extension, or request for a recommendation), a title, a description of what is requested and why, and the contract reference as in Umucyo for amendments and extensions. *Save draft*, then attach the **supporting documents** (PDF, Word, Excel or images, up to 10 MB each). Attach internal documents only, such as memos, justifications and site reports: never bid documents, bidders' submissions or prices, which stay in Umucyo. Tick the declaration and *Submit to the committee*.

The Chairperson, Vice-Chairperson or Secretary either accepts the request, which registers it as a file, or returns it with a reason. If it is returned, correct it and submit it again. Once accepted, the request shows the file number, its progress (received, scheduled, under review, recommendation issued, submitted to CBM, closed) and the session it is on. You receive a notification at each decision. You can withdraw a request until it is accepted.

When you sign in, your home page shows your requests: how many need your action (drafts and returned requests), are with the committee, are in progress or are closed, and for each one a progress bar from submission to closure with the session it is on.

## Committee officers: Chairperson, Vice-Chairperson, Secretary

### Requests from outside the committee

**Requests** lists what requesters have submitted, newest first. Open one to read it and download its documents. *Accept as a file* registers it in your committee: choose the committee, and for amendments and extensions the contract (the one matching the requester's reference is selected for you; register it under **Contracts** first if it is missing). The file can then go on a session agenda like any other. *Return to the requester* sends it back with your reason. A file that came from a request shows the request and its documents on the file page.

Only the three officers can create or change sessions. Ordinary members can read the schedule and ask for a file to be tabled.

### Files register

**Files register** lists your committee's files with their number (e.g. F-2026/0042), type, tender or contract reference, procurement officer, stage, date received and target date. Filter by type, officer, stage, procurement type, contract, or *overdue only*.

![The files register with its filters; overdue files are flagged in red](/guide/04-files.png)

To register a file choose *New file*: committee, item type (A–H), title, tender reference, procurement type and method, procurement officer and date received. Contract amendments (C), extensions (D) and framework evaluations (G) must point to a contract, so register it first under **Contracts** with its Umucyo reference, value and currency, completion date and performance security. Referred matters (H) need the referral reference and reason. The target date is filled in from the configured working days for the type; you can change it.

A file moves through stages: received → scheduled → under review → recommendation issued → submitted to CBM → closed, or returned for correction. Adding it to a session agenda moves it to *scheduled*; issuing a recommendation and the CBM's response move it on automatically.

![A file's path: received, scheduled, under review, recommendation issued, submitted to CBM; the CBM approves (closed) or returns it to the committee](/guide/00-file-flow.png)

When a member asks for a file to be tabled, the Chairperson accepts or declines the request from the committee page.

### Scheduling a session

Under **Sessions** choose *New session*: ordinary or extraordinary, date and time, and place. Session numbers (e.g. PTCA 2026/014) are assigned automatically. If the date is inside the notice period (3 working days for ordinary, 24 hours for extraordinary sessions, by default), you are warned and must record why short notice was given.

The same page lets you choose the session's **agenda** straight away, with a toggle between **Normal activity** (the committee's open files) and **Submitted request** (requests waiting for the committee; the tab shows how many). Tick each item and its activities; for a request that concerns a contract, choose the contract. Scheduling then creates the session, accepts the chosen requests as files of the committee and puts everything on the agenda in one step; if anything is wrong, nothing is saved and you are told what to fix. You can still add items later from the session page.

### Building the agenda

*Add agenda item* has two tabs. **Normal activity** puts one of the committee's files on the agenda, as described below. **Submitted request** lists requests waiting for the committee (the tab shows how many): choose one, the contract for an amendment or extension, and the activities, then *Accept and add to agenda*; the request becomes a file of this committee and is tabled in one step. The *Schedule a session* page reminds you when requests are waiting.

Each agenda item must be an open file of the committee, and its type must match the file's type. Free-text items are not possible: other matters come in only as a type H referred matter from the CBM. The one exception is type I, the committee's own business (adopting previous minutes, work planning, member induction), which never carries a recommendation. If the file you need is not listed, use *Register a new file of this type* in the dialog.

When you choose the file, also tick the **activities** the committee will perform on it in this session, for example *Preliminary / eligibility examination* and *Technical evaluation* for a type A file, or *Review of the amendment request* and *Recommendation (vote)* for a type C file. Activities already completed are greyed out; the rest are ticked for you. The list of activities per file type is set by your institution under Settings.

During the session, mark each activity **Completed** or **Not completed**; not completed needs a reason. For type A, the activities are the evaluation stages: completing one here completes the stage in the evaluation (its checks still apply), and completing a stage in the evaluation marks the activity done. The *Recommendation* activity is marked done when the vote adopts the recommendation. The agenda shows how many activities are done, and the session cannot be closed until every activity is recorded.

Activities not completed are offered at the top of the committee's next session under *Carried over from previous sessions*. Tick them and choose *Add to agenda*; the file is added if it is not already there. The minutes list each item's activities and whether they were completed.

### Attendance and quorum

On the session page, mark each member: **present**, **absent with permission**, **on leave** or **absent (unjustified)**. Apologies that members sent beforehand fill in automatically; absences without notice are flagged. The quorum panel updates as you mark:

- *Quorum not met:* shows how many more members are needed.
- *Exactly at quorum:* shown in a separate warning colour. One person leaving would break quorum.
- *Quorum met.*

Quorum is 3/5 of the committee's own size by default (3 of 5, or 5 of 7). Attendance can be marked offline and is sent when you reconnect.

![The session view on a phone: quorum panel, agenda and attendance buttons; an apology received in advance is shown under the member's name](/guide/05-session-mobile.png)

### Taking a decision on an agenda item

1. Open the item. Every present member declares any conflict of interest first (see *Committee members*).
2. Draft the recommendation: choose the outcome from the list for that item type and write the recommendation text. For an award, add the recommended bidder reference and contract value.
3. *Open voting.* This is only possible when quorum is met and every present member has declared. The required number of votes is shown: 3/5 of the members **present** by default, after removing members who declared an interest. For example, with 5 present and 1 interest declared, 4 may vote and 3 votes are required.
4. Members vote. *Close voting*: the recommendation is **adopted** when the votes in favour reach the required number, otherwise **not adopted**.
5. The Chairperson (or Vice-Chairperson) **issues** the adopted recommendation, confirming with a code from the authenticator app. Then *submit to CBM*.

### Minutes

After the session, open **Minutes** from the session page and choose *Generate draft minutes*. The draft is built from what was recorded: header, attendance table, interest declarations, a section per agenda item that adapts to its type, the votes, a separate **dissenting opinions** block, and a signature block for every member present. Add any secretary notes, then *Submit for review*. Every member who was present must acknowledge the draft before you can *Finalise* it; editing or regenerating the draft asks for new acknowledgements. Final minutes cannot be changed. Download the Word file (.docx) at any time; it uses your institution's template and refers to files and sessions only by their numbers.

## Committee members

Everything in this section applies to all members, officers included. Votes and declarations are personal: each member records their own.

### Before a session: sending an apology

If you cannot attend, open the session (from **Sessions**, your dashboard or the reminder) and choose *Send apology*. Pick *absent with permission* or *on leave* and give the reason. It goes to the Chairperson and Secretary. Send it **before the session starts**: an apology sent after the start is recorded as late, and an absence with no apology is flagged as unjustified in the minutes. Apologies can be sent offline; they go out when you reconnect.

### Asking for a file to be tabled

On a file's page choose *Request tabling* and give your reason. The Chairperson accepts or declines it.

### Declaring a conflict of interest

Before an item is discussed, every member present declares whether they have any direct or indirect interest in it, for example a relative working for a bidder. Open the agenda item and answer the declaration; you are asked for a code from your authenticator app (or your password). If you declare an interest, describe it and say whether it is direct or indirect. You are then **excluded** from that item's discussion and vote, and the number of votes required is recalculated without you. Declarations are logged and appear in the minutes.

### Voting

When the officer opens voting, the item shows three large buttons: **For**, **Against**, **Abstain**. Above them you see how many members are present, how many are excluded by interest, how many may vote, and how many votes are needed. Confirm with a code from your app; the vote counts only once the screen confirms it was recorded. You cannot vote offline.

### Recording dissent

If you vote **Against** or **Abstain**, you must give your reasons. The reason field is on screen straight away; you do not need to look for it. Your reasons appear under your name in the *Dissenting opinions* section of the minutes. This protects you personally against joint liability for a decision you did not support. Members who dissent still sign the minutes.

![Voting on a phone: declarations, the recommendation, the votes required, the three vote buttons and the dissent reason field](/guide/06-vote-mobile.png)

### Acknowledging minutes

When the draft minutes are ready for review you receive a notification. Read them, add a comment if something is wrong, and choose *Acknowledge*. If the Secretary changes the draft afterwards, you are asked to acknowledge the new version. The minutes can only be finalised once every member who was present has acknowledged them.

## Working each item type

Each item type has its own screen on the file page. Officers record progress there; everyone on the committee can read it.

| Type | Matter | What the file page tracks |
| --- | --- | --- |
| A | Evaluation report review | Six evaluation stages, clarifications, bid validity |
| B | ToR / tender document review | Review checklist and outcome |
| C | Contract amendment (addendum) | Cumulative value, two tests, approval route |
| D | Contract extension (time) | Ground, timing, penalties, force majeure |
| E | Prequalification review | Checklist |
| F | Cancellation or rejection of all bids | Checklist |
| G | Framework agreement annual evaluation | Checklist; continue or cancel |
| H | Referred matter from the CBM | Referral reference and reason |
| I | Administrative (committee's own business) | No recommendation |

### Type A — evaluation report review

The **stage progress view** is the main screen for type A files. It shows all six stages with their status, who completed each and when, and what is blocking progress. Scores, prices and bid documents stay in Umucyo; here you record only outcomes, reasons and ranked order by bidder reference. The system rejects anything that looks like a score or price.

1. **Criteria lock (before anything else).** Choose *Lock the criteria* to confirm the committee evaluates only against the criteria published in the tender document, with nothing added or removed. If you find a defect in the published criteria, record it as a *finding* in its own field; do not adjust the criteria.
2. **A1 Preliminary examination.** Pass or fail per bidder, with the ground for each failure (late bid, bid form, bid security or declaration, validity, joint venture agreement, debarment, beneficial ownership, categorisation).
3. **A2 Technical evaluation.** Number evaluated and passing; for each failing bidder, the requirement not met and the committee's reason. A bare "non-compliant" is not accepted. Record any resource person, with confirmation that they took no part in decisions and have no interest.
4. **A3 Financial evaluation.** Arithmetic check, whether comparison used prices with taxes, and the ranking. A bid can be rejected as **abnormally high or low** only after recording the written request for explanation, the explanation received and the committee's analysis; the system will not let you reject it without them. If *all* bids are abnormal, over budget or non-responsive, raise the *Rejection of all bids*: this creates a new type F file.
5. **A4 Administrative compliance (first-ranked bidder only).** This comes **after** technical and financial evaluation, not before. Record the missing documents (tax clearance, social security, VAT certificate, site visit, licences) and the date of the cure request. The deadline is 5 working days by default, shown as a countdown.
6. **A5 Due diligence** on the successful bidder: who verified what, the result, and any disqualification ground.
7. **A6 Recommendation.** Completed automatically when the committee issues its recommendation on the file.

![The type A stage progress view: bid validity clock, criteria lock, the six stages with what is blocking each, and the clarification log](/guide/07-evaluation.png)

The **clarification log** records each request to a bidder: subject, dates and the committee's treatment. A bidder may **explain, not improve** their bid, so for each response you record whether it clarified the bid or attempted to alter it.

The **bid validity clock** runs from the expiry date entered on the file. It shows the date by which a validity extension request must reach the regulator and the days remaining, and reminders are sent well before both dates.

### Type C — contract amendment

Enter the requested value and description. The system adds up **all** previously approved amendments on the same contract, in the contract's own currency, and shows the resulting cumulative percentage against the ceiling. It warns as the total approaches the ceiling; several amendments each under the ceiling but together above it are flagged. Two tests are shown **separately**:

- **Qualitative:** does the amendment change the substance or nature of the contract? Answer yes or no with a justification. A "yes" bars the amendment; no approval level can cure it.
- **Quantitative:** is the cumulative value within the percentage ceiling and the absolute limit? This only decides the approval route. Passing it does **not** cure a failed qualitative test.

The route is either approval within the institution or escalation to the supervising authority. For an escalation, a countdown shows how much time is left before the contract completion date. Further prompts cover extending or increasing the performance security, changes to deliverables and, for consultancy, key personnel replacements of equal or better standard. The figures are frozen when the recommendation is issued.

![A type C amendment: cumulative value against the ceiling, the two tests shown separately, and the approval route](/guide/08-amendment.png)

### Type D — contract extension

Record the requested completion date, the ground (force majeure, act of government, unforeseen physical conditions, the institution's own delay, or the bidder's fault) and the date the request was received. The system shows whether it arrived before the original completion date. If the ground is the **bidder's fault**, delay penalties may apply instead: record that penalties were considered and your reasoning, or the recommendation cannot be issued. If the extension carries a **cost claim**, choose *Cost claim* to create the linked type C file, which must pass both amendment tests. For force majeure, the duration is tracked against the configured limit after which the contract is cancelled.

### Contract timeline

Under **Contracts**, open a contract to see every amendment and extension filed against it, with the running cumulative value and percentage, the current completion date and the performance security status (valid, expiring or expired).

### Types B, E, F and G

These files show a checklist of prompts set by your institution, each answered Yes, No or Not applicable with a note. For B the prompts include: scope clearly defined; specifications neutral with no brand names unless "or equivalent"; proportionate qualification requirements; no discriminatory clauses; complete evaluation criteria; deliverables and timelines; and the tender on the approved procurement plan with budget available. The recommendation is then chosen from the outcomes for that type, for example *recommend as submitted*, *recommend with changes* or *return for revision* for type B.

![A type B checklist: an answer of No is highlighted and must be addressed in the recommendation](/guide/09-checklist.png)

## Notifications, dashboards and reports

### Reminders

The system checks deadlines throughout the working day and sends each reminder **by email**, in the app (the bell, **Notifications**) and, if you turned it on, as a phone notification. Each reminder opens a single page that answers one question and offers one action. For example, "bid validity for F-2026/0042 needs an extension request by 30 Oct" opens the validity clock with a link to the evaluation.

| Reminder | Sent to |
| --- | --- |
| Cure deadline approaching or passed | Committee officers and the file's procurement officer |
| Bid validity: extension request due, overdue, validity expiring | Committee officers and procurement officer; a task is created for the extension request |
| Escalated amendment running short of time | Committee officers, procurement officer and CBM |
| Contract completion approaching; framework expiry or annual evaluation due | Committee officers, procurement officer and CBM |
| Committee term expiring | The member and the CBM |
| Regulator notification after replacing all members | CBM |
| Upcoming session; conflict-of-interest declaration pending | Committee members |
| Minutes awaiting your acknowledgement | Members who were present |

### Dashboards

- **My dashboard** (everyone): your files, your upcoming sessions, declarations you still owe and your attendance record.
- **Committee dashboard** (officers): workload per procurement officer, overdue files by stage and type, quorum risk for upcoming sessions, turnaround per type, bid validity expiring soon, and contracts approaching their amendment ceiling.
- **CBM dashboard:** all committees, recommendations awaiting your decision, composition compliance, term expiries and workload across committees.

![My dashboard: declarations and votes waiting for you, upcoming sessions and your files](/guide/03-dashboard.png)

### Reports

**Reports** gives files handled in a period by type, sessions held and attendance statistics per member, for individual and institutional performance reporting. Download any report as CSV for Excel.

### Internal audit (read-only)

Read-only users can open everything in their institution but change nothing. The **Audit log** lists every action with who did it, when, and the value before and after: creations, edits, votes, declarations, appointments, replacements, stage changes, sign-ins, failed sign-ins and lockouts. Filter by person, action or record. Audit entries can never be edited or deleted. Conflict-of-interest declarations can be exported from a session as CSV.

## Troubleshooting and FAQ

| Problem | What to do |
| --- | --- |
| Invitation link says invalid or expired | Links work once and expire after 72 hours. Ask your institution administrator to resend the invitation. |
| Forgot password | On the sign-in page choose *Forgot password*; the reset link arrives by email and is valid for 30 minutes. |
| "Too many failed attempts" | The account locks for 15 minutes after 5 failed attempts, longer if failures continue. Wait, or ask your administrator. |
| Asked for a 6-digit code but never set up an app | Two-step verification was set up on your account without you. Ask your institution administrator to reset it; your next sign-in shows the QR set-up screen. |
| Lost phone with the authenticator app | Use a recovery code instead of the app code, then set up the app on your new phone. Without recovery codes, ask your administrator to reset two-step verification. |
| Code from the app is refused | Check the phone's time is set automatically; codes depend on the clock. Each code works only once, so wait for the next one. |
| Asked to "confirm your identity" in the middle of work | Normal for votes, declarations and issuing a recommendation. Enter the current app code; your action continues on its own. |
| The file list in *Add agenda item* is empty | The committee has no open file of that type. Use *Register a new file of this type*, or check that the file was assigned to this committee and is not closed. |
| "Agenda item type must match the file" | Choose the item type equal to the file's own type. Other matters must come as a type H referral from the CBM. |
| Cannot open voting | Quorum must be met and every present member must have declared their interests. The item page shows who is missing. |
| Vote or declaration blocked while offline | By design: legally significant acts need a live connection. Reconnect and try again. |
| "Someone else changed this record" | Another person saved the same record first. Reload the page and repeat your change. |
| Appointment refused | Read the message: it names the rule (excluded office, procurement-officer majority, role already filled, size reached or term limit). |
| Sessions appear at the wrong time | All times are shown in Kigali time (CAT, UTC+2), whatever the device's time zone. |
| App looks out of date | Choose *Reload* on the "new version available" banner, or close and reopen the app. |

For anything else, contact your institution administrator.
