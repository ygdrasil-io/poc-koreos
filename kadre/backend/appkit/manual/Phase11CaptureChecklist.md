# AppKit Phase 11 capture checklist

Run `./gradlew :kadre:backend:appkit:phase11CaptureHarness` on a physical macOS host. The harness writes a TSV record without pixel bytes, window titles, source IDs, or native handles. Start with `help` and record each scenario through `result M<n> pass|fail|not-applicable <note>`.

| ID | Procedure | Expected evidence |
|---|---|---|
| M1 | Start the harness and run `snapshot` before `permission`. | No system prompt; the record reports an explicit capture availability/source state. |
| M2 | Run `permission`; exercise both refusal and grant when possible. | `REQUEST_PERMISSION` and the following snapshot distinguish denied/restricted state from granted state. |
| M3 | After a grant, run `refresh`. | A complete `enumerated=` inventory is reported, never a partial list or a fabricated empty inventory. |
| M4 | Run `open-picker`, dismiss it once, then select a source once. | Dismissal records `UserCancelled(CaptureOpen)`; selection records one session and one effective streaming configuration. |
| M5 | With a selected session, observe `FRAME` records. | Only metadata is recorded: BGRA format, dimensions, configuration revision and padded plane layout; no pixel data appears in the TSV. |
| M6 | Run `close <session>`. | One `COLLECT_TERMINAL` and one terminal outcome are recorded; no later frame record is admitted for that session. |
| M7 | Restart with `--frame-delay-ms=250`, open a source, then run `stress <session> 30`. | The record reports bounded frame progress and any drop/terminal diagnostic. Repeat while switching spaces, locking/unlocking, or changing the selected display configuration. |

`open-source <index>` is available after `refresh` as an alternative to the macOS 14+ picker. Region and same-session `Surface` capture remain deliberately unavailable in this increment; their absence is expected evidence, not a test failure.
