# Gated-voice fork — upstream-PR-worthiness vs. private quark

A short assessment to inform the question: should this branch be submitted
upstream to `allenloves/SuperSampler`, or kept as a private fork / forked quark?

## What this branch adds

- `\ssvoice1` (mono) and `\ssvoice2` (stereo) SynthDefs, **additive**: zero edits
  to the existing `\ssplaybuf{1,2}` / `\ssexpand{1,2}` defs.
- `SSampler#keyVoice`, `#noteOn`, `#noteOff`, `#allNotesOff` with an
  `activeVoices` registry and a `\stack`/`\steal` voice-cap policy.
- `SSamplerMIDI` MIDI bridge (Note On/Off + CC64 sustain pedal).
- Loop modes: forward / reverse / palindrome; trapezoidal seam window
  (default) and optional equal-power 2-tap crossfade (`loopMode: \xfade`).
- HelpSource updates for both new classes.
- `Testcode/gatedLoopTest.scd` covering the audible test surface.

The new code path is opt-in: existing `key`, `playArgs`, `playEnv`, `setKeyRanges`,
and `Testcode/Samplertestcode.scd` are byte-identical to upstream and run
through the same SynthDefs as before.

## Case for upstream PR

1. **Strictly additive.** No edits to existing SynthDefs, the analysis pipeline,
   `SamplerQuery.getPlayTime`, or any public method signature in `SSampler#key`.
   A maintainer can audit the diff and confirm that every existing call site is
   untouched. Low review burden.
2. **Fills a real gap.** Concatenative/auto-analysis playback is great for
   gestural and envelope-driven contexts, but the obvious next question for any
   user with a sampler-like asset library is "can I play it with a MIDI keyboard?"
   This branch is the smallest plausible answer to that question.
3. **Core UGens only.** No sc3-plugins required in the new SynthDefs (sticks to
   `BufRd`, `Phasor`, `Select`, `EnvGen`, `Line`, `Pan2`, `Balance2`). Compatible
   with constrained targets (scsynth-on-WASM, low-dep installs).
4. **Documented.** Both new classes ship help files in the same `.schelp` style
   as the rest of the project, and the test script makes audible expectations
   explicit (so a reviewer without context can verify quickly).

## Case against — or for keeping it as a fork

1. **Maintainer activity.** Recent upstream commits are sparse and mostly
   bug-fix / help-file corrections (last few: a help-file PR merge, a start-time
   bug fix, quark version bump). A non-trivial feature PR may sit for a long
   time, and that cost is asymmetric: you are blocked on review for something
   that already works locally.
2. **Scope alignment.** The project's stated focus
   (README: *"applying audio content analysis techniques to make decisions on
   sample processing"*) is the auto-analysis / concatenative side. Voice-mode
   keyboard playback is orthogonal — useful, but not the headline. A maintainer
   who deeply cares about the analytical core might reasonably want a smaller
   surface, not larger.
3. **API surface growth.** This branch adds ~9 new public methods on
   `SSampler`, 2 new SynthDefs, 1 new class, 2 new classvars, and ~10 new
   `SamplerArguments` fields. Each is small individually; collectively they
   widen the contract that future upstream changes have to respect.
4. **Tests are manual.** Audio-correctness is verified by listening to
   `Testcode/gatedLoopTest.scd`. There is no headless test harness in the
   project, so upstream maintenance of this feature carries an open-ended
   regression-listening burden.
5. **Pulled vs. private quark cost is low.** SuperCollider's Quark
   distribution makes a private/fork quark essentially a one-line install for
   anyone who wants it. The friction of "you'll need my fork" is small.

## Recommendation

**File a PR but plan to maintain a fork either way.** Specifically:

1. Cut a PR that proposes the additive parts only, and stage it so the
   maintainer can take any subset:
   - PR 1: `\ssvoice1` + `keyVoice` + `noteOn`/`noteOff` + `activeVoices`
     (mono, trapezoid window only). Smallest possible footprint, biggest
     practical win.
   - PR 2 (follow-up): `\ssvoice2` stereo, `loopMode: \xfade`,
     `voicePolicy: \steal`, `SSamplerMIDI`, HelpSource updates.
2. In the cover note, foreground the additive guarantee and the
   `Testcode/Samplertestcode.scd` regression argument, and offer to keep the
   PR rebased against `main`.
3. **In parallel**, publish the full branch as a forked Quark — both as
   an immediately-usable artefact and as a clear "no hard feelings, here is
   the integrated artifact" gesture if upstream review stalls.

The split-PR approach (1) costs almost nothing to prepare from this branch
(the commit history is already broken into reviewable pieces), and (3) means
the timeline of upstream review does not block downstream consumers.

## What would change my mind toward "fork only, do not PR"

- If upstream silence persisted past ~6 weeks on the PR with no acknowledgement
  — the cost of carrying a long-running fork-rebase against an inactive trunk
  begins to exceed the value of being mainlined.
- If the maintainer requested a substantially different API surface for the
  voice mode (e.g. event-stream-based rather than method-based). The mismatch
  cost is then design-deep, and "two related but distinct projects" is the
  cleaner outcome than a multi-month back-and-forth.
- If the project picks up a separate "concatenative-only, no keyboard"
  identity narrative in its docs / README. The fork then has a sharper
  audience and is better off as its own thing.
