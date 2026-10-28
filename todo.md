# Remaining-fixes program checklist (autopilot-full, adapted)

Program: implement reports/Remaining N6 N18 fix plan.md. Four independent PRs off trunk. Direct merge after review. No forge PRs. Single maintainer repo.

## Playbook steps (autopilot-full, verbatim with skips)

1. **The operator's gates come first.** skip: no forge PRs in this repo. Direct merge after review. Owner reports merge-ready with pinned head SHA. Operator go already given as implement the plan plus implement all in parallel.
2. Spawn one owner per PR, and keep its work where a replacement can find it. ADAPTED: owners are background subagents in isolated worktrees. Branches merge directly, no PR tool. Decision trail in progress/IMPL-<slug>.md plus evidence dirs.
3. Run owners in parallel off trunk, never stacked, so each PR lands on its own.
4. Swarm-verify once. ADAPTED: repo verification is pinned app flows per verify-isakai/SKILL.md, not ten-lane swarms. Each owner runs the affected flows plus regressions on fresh trees. Independent review lane runs once over the stack before merges.
5. The operator merges only what you verified, tested on the trunk it lands on. ADAPTED: root reviews each diff and merges. Green build plus flow evidence required.
6. Run the root layer. skip: no gate loosening expected. Any allowlist or budget change needs verifier proof plus root countersign.
7. Stand down at once on the operator's stop. A hold reaches every owner as a zero-writes order.

## PRs

- [x] PR1 translator sealed metadata. Merged. byLowerCase plus getAll resolve on jshell evidence. Calculator PASS on retranslated classes.
- [x] PR2 loader extinfo plus dlerror. File: src/native/bionic_shim.zig. Merged as 55c656c. Matrix observed message-then-NULL. Chooser maps. Calculator PASS.
- [x] PR3 appcompat icon plus Up navigation plus density. Merged. FAB blue with white plus on screenshot. Up single-intent rule. Calculator PASS.
- [x] PR4 text watchers on all mutations. Merged. 5/6 before and after, 0 gate misses each. Calculator PASS.
- [x] Independent review lane over merged stack: ships all four, no blockers.
- [x] N18 proof complete. Stored fd plus offset verified. Zero NDK callers in pinned libs. Program closed.

## N6 closeout

- [ ] Step 3. Game start proof on fresh build (chooser to game, backend resolves, engine attempted).
- [ ] Step 4. Engine stress behind step 3 (solved puzzle, threads, callbacks, GC, exceptions).
- [ ] Step 5. Operator isolation run (human, command recorded by step 3 worker).
- [ ] Step 6. N18 caller re-survey once N6 lands.

## Standing orders

Full program objective: land all four PRs verified on real app surfaces. Verification rule: build green plus affected flows PASS on fresh trees plus regression flows PASS. Root merges. Done when all four are on main with evidence recorded.
