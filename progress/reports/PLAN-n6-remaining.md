# N6 remaining fix plan

Chain order. Each step gates the next. Evidence per step.

## Step 1. Capture the VerifyError message

Run the puzzles game path on a fresh tree. Save the full VerifyError text with method plus bci offset. Locate offset with javap -c. Confirm v49 frameless with javap -v. Name the emitter function in src/translate.zig. No code change. Evidence: message text plus disassembly slice plus emitter pointer.

## Step 2. Fix the emitter

One diff in src/translate.zig against the ranked suspects. Descriptor check first, then branch-merge types, then new plus init pairing, then two-slot discipline. Evidence: error gone plus AlertDialogLayout reproducing wrap top plus fill content plus min-height buttons. Calculator regression PASS.

## Step 3. Prove a game starts

Drive chooser to game start on a fresh tree. Evidence: game activity maps, backend resolves, native engine attempted in bionic log. Calculator regression PASS.

## Step 4. Engine stress

Solved puzzle plus thread plus callback plus GC plus exception behavior on the started game. Evidence: flow logs plus screenshots.

## Step 5. Operator isolation run

Human runs the default-isolated N6 gate. Same shape as the C7 gate. Evidence retained under evidence/N6 with date.

## Step 6. N18 re-survey

Re-run the caller grep from progress/reports/IMPL-n18-proof.md once N6 lands. A native caller plus the fd proof closes N18.
