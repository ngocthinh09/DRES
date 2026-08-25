# HTTP polling synchronization for DRES evaluations

## Purpose

As of commit `12638bdd`, the DRES frontend synchronizes realtime evaluation views
through HTTP polling instead of the DRES WebSocket client. This makes task state,
the visible countdown, scores, submissions, and judgement queues update reliably
even when a proxy or browser does not deliver WebSocket frames.

The backend remains authoritative: it decides task state, time limits, submission
acceptance, verdicts, and scores. Polling only updates the client view; it does not
change evaluation semantics or scoring.

## Polling cadence and data

All affected realtime screens request their dynamic data every second. A successful
admin action also triggers an immediate refresh instead of waiting for the next
polling tick.

| Data | Endpoint | Consumers |
| --- | --- | --- |
| Evaluation task state and clock | `GET /api/v2/evaluation/{evaluationId}/state` | Viewer and synchronous admin |
| Current task submissions | `GET /api/v2/evaluation/{evaluationId}/submission/list` | Team cards and recent-submission widgets |
| Current task scores | `GET /api/v2/score/evaluation/{evaluationId}/current` | Task score, scoreboard, compact team widgets |
| Evaluation scoreboards | `GET /api/v2/score/evaluation/{evaluationId}` | Competition-score views |
| Admin overview and viewer list | `GET /api/v2/evaluation/admin/{evaluationId}/overview`, `.../viewer/list` | Synchronous/asynchronous admin views |
| Admin submission history | `GET /api/v2/evaluation/admin/{evaluationId}/submission/list/{templateId}` | Submission list and admin counters |
| Judgement/voting queue | Existing `judge/status`, `judge/next`, and `vote/next` endpoints | Judge and vote views |

`state` is the source for `taskStatus`, `taskTemplateId`, `timeLeft`, and
`timeElapsed`. Consequently the viewer timer updates with the one-second polling
cadence. The HTTP response reflects the server clock, so a client display that is
briefly stale can never extend the server's submission deadline.

The header health widget may separately request `GET /api/v2/status/time` to show
RTT and clock status. It is not the source of the task countdown.

## Task lifecycle

1. The host invokes start, stop, switch, force-viewer, or duration APIs through
   HTTP POST as before.
2. The initiating admin view refreshes immediately after a successful response.
3. Other viewer/admin/controller pages observe the changed `state`, `overview`,
   score, or submission data during their next poll, normally within one second.
4. Participants submit through the existing REST submission API and receive their
   verdict in that HTTP response. The viewer receives the resulting submission and
   score through its next polling requests.

## WebSocket compatibility

The frontend WebSocket service, message types, URL configuration, and associated
frontend tests were removed. New browser bundles no longer open `/api/ws/run`.

The backend WebSocket endpoint and event stream were deliberately retained. This
avoids breaking old deployed frontends or external clients that still use that API;
the official current frontend simply does not depend on it.

## Validation and deployment

Build the updated frontend package with:

```bash
cd /home/thinhvln/DRES
./gradlew --console=plain -q --rerun-tasks :frontend:packageFrontend
```

After deploying and restarting DRES, use browser DevTools → Network:

- No new `ws://.../api/ws/run` connection should be opened by the current frontend.
- `state`, `current`, `list`, and score requests should recur about once per second
  while an evaluation view is open.
- Starting, stopping, or switching a task should appear on another open evaluation
  view within one polling interval.
- The server must still reject submissions after a task deadline, independent of
  what a browser currently displays.
