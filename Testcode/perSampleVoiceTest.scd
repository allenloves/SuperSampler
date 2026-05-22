// perSampleVoiceTest.scd
//
// Per-(sample, section) voice-mode overrides:
//   - SSampler#setSampleVoiceArgs / #getSampleVoiceArgs / #clearSampleVoiceArgs
//   - SSampler#voiceEditor (UI)
//
// Each block below is meant to be evaluated one at a time. Comments
// document what you should HEAR.


// ============================================================
// 1. Setup -- load samples with filename-based pitch detection
// ============================================================
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
	t = SSampler(\tubularPerSample).load(a, filenameAsKeynum: true);
	"loaded".postln;
};
)


// ============================================================
// 2. Per-sample API -- set, read, clear
// ============================================================
// Pick the first loaded sample / section 0.
~s0 = t.samples[0];

// Set a sub-region loop + a release region + a long ADSR.
(
~mid = (~s0.activeBuffer[0][0].numFrames * 0.5).asInteger;
~end = ~s0.activeBuffer[0][0].numFrames;
~rs  = (~end * 0.7).asInteger;
t.setSampleVoiceArgs(~s0, 0, (
	loop: 1, loopDir: \fwd, loopMode: \xfade,
	loopStart: ~mid, loopEnd: ~end,
	attack: 0.1, decay: 0.3, sustainLevel: 0.7, release: 1.5,
	releaseMode: \loop, releaseStart: ~rs, releaseEnd: ~end,
	releaseXfade: 0.05
));
)

// Inspect.
t.getSampleVoiceArgs(~s0, 0);

// Trigger via noteOn -- per-sample overrides win over noteOn's arg defaults.
~v = t.noteOn(~s0.keynum[0]);
// HEAR: slow attack, loops the second half (xfade-style), then on note-off
// the release region (last 30%) loops while the amp env releases over 1.5s.
~v.do{|s| s.set(\gate, 0)};

// Same trigger again, with explicit args -- args still lose to overrides.
~v = t.noteOn(~s0.keynum[0], loopMode: \trapezoid, attack: 0.001);
// HEAR: SAME as the previous trigger -- attack still 0.1s, mode still xfade.
~v.do{|s| s.set(\gate, 0)};

// Partial update: merge in a new attack. Other override fields persist.
t.setSampleVoiceArgs(~s0, 0, (attack: 0.5));
t.getSampleVoiceArgs(~s0, 0);   // -> attack now 0.5, everything else unchanged

// Clear this section's overrides. Next trigger uses noteOn defaults again.
t.clearSampleVoiceArgs(~s0, 0);
t.getSampleVoiceArgs(~s0, 0);   // -> nil


// ============================================================
// 3. Custom Env (full breakpoint envelope) per sample
// ============================================================
// An ampenv override bypasses the ADSR builder and is plugged into \env
// directly. A release node is added automatically if the Env doesn't have
// one and the voice is gated.
(
t.setSampleVoiceArgs(~s0, 0, (
	loop: 1,
	ampenv: Env([0, 1, 0.2, 0.8, 0], [0.05, 0.2, 0.6, 1.5], \sin)
));
)
~v = t.noteOn(~s0.keynum[0]);
// HEAR: fast attack, dip, rise to 0.8 sustain.
~v.do{|s| s.set(\gate, 0)};
// HEAR: 1.5s release tail.

t.clearSampleVoiceArgs(~s0, 0);


// ============================================================
// 4. Different overrides on different sections of the same sample
// ============================================================
// (Only useful if your sample has multiple sections -- multi-onset files.)
~ns = ~s0.keynum.size;
~ns;                                // -> number of sections (often 1 for tubular)
(
if(~ns > 1) {
	t.setSampleVoiceArgs(~s0, 0, (attack: 0.01, release: 0.1));
	t.setSampleVoiceArgs(~s0, 1, (attack: 1.0,  release: 2.0));
	"two sections configured independently".postln;
} {
	"this sample has 1 section, skipping multi-section test".postln;
};
)
t.clearSampleVoiceArgs;             // clear everything across all samples


// ============================================================
// 5. UI: per-sample voice editor
// ============================================================
// Opens with the first (sample, section) selected. Use the top "Sample:"
// dropdown to switch. The cyan band is the loop region, orange is the
// release region -- drag the bands or edit the NumberBoxes. Changes save
// automatically. Click "Audition" to hear, then "Note Off" to release.
t.voiceEditor;

// Open scoped to a specific (sample, section).
t.voiceEditor(t.samples[0], 0);


// ============================================================
// 6. Main GUI: each row now has a "VEdit" button per section
// ============================================================
// Stars (VEdit *) light up when overrides exist for that row.
t.gui;


// ============================================================
// 7. Concatenative path unaffected -- regression check
// ============================================================
// Overrides apply only to keyVoice / noteOn. #key uses the concatenative
// SynthDefs and ignores per-sample voice args.
(
t.setSampleVoiceArgs(t.samples[0], 0, (
	attack: 2.0, release: 5.0,
	loopStart: 0, loopEnd: 100, loop: 1
));
)
t.key(81);                           // HEAR: normal #key behavior, untouched.
t.clearSampleVoiceArgs;
