// gatedLoopTest.scd
//
// Audible verification of the voice-mode (gated keyboard) playback path
// added on branch feature/gated-loop.
//
// Each block below is meant to be evaluated one at a time. Comments
// document what you should HEAR (the test rig is otherwise silent).
//
// All blocks use \ssvoice1 via SSampler#keyVoice / #noteOn. The existing
// concatenative engine (key, playArgs, playEnv) is unaffected.


// ============================================================
// 1. Setup -- load samples with filename-based pitch detection
// ============================================================
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
	t = SSampler(\tublarVoice).load(a, filenameAsKeynum: true);
	"loaded".postln;
};
)

// Inspect what pitch detection produced.
t.keynums;                              // -> Array of detected MIDI keynums per sample/section.
t.samples[0].keynum;                    // -> e.g. [81] for a single-section sample tagged C5-ish.
t.keyRanges;                            // -> default keyRange radius. Section keynums -> [lo, hi].

// Tighten the radius (\keynumRadious strategy). Each section now only
// responds to ±2 keynums.
t.setKeyRanges(\keynumRadious, [2]);
t.keyRanges;                            // -> narrower ranges.

// Restore default radius for the rest of the script.
t.setKeyRanges(\keynumRadious, [5]);


// ============================================================
// 2. Parity baseline (loop = 0, gate = 1)
// ============================================================
// Existing stock path -- this is the reference.
t.key(81);                              // HEAR: one note, plays through, frees.

// New voice path with loop=0. Should sound perceptually equivalent.
t.keyVoice(81);                         // HEAR: same one-shot, ~50ms release tail.

// Both registries should be empty after notes finish.
SamplerQuery.playing[0];                // -> empty (or nearly so).
t.activeVoices;                         // -> empty IdentityDictionary.


// ============================================================
// 3. Forward loop, xfade off vs on
// ============================================================
(
~v = t.keyVoice(81, loop: 1, loopDir: \fwd, loopXfade: 0);
)
// HEAR: sustained tone with a click each time the pointer wraps from
// loopEnd back to loopStart.
~v.do{|s| s.set(\gate, 0)};             // HEAR: ~50ms release tail then silence.

(
~v = t.keyVoice(81, loop: 1, loopDir: \fwd, loopXfade: 0.05);
)
// HEAR: same tone but seam clicks are replaced by a per-cycle volume dip.
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 4. Reverse loop
// ============================================================
(
~v = t.keyVoice(81, loop: 1, loopDir: \rev, loopXfade: 0.05);
)
// HEAR: sustained sound, pointer running backward (timbre differs from fwd:
// attack transients now appear at the end of each cycle).
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 5. Palindrome
// ============================================================
(
~v = t.keyVoice(81, loop: 1, loopDir: \palin, loopXfade: 0);
)
// HEAR: smooth back-and-forth with no seam clicks (palindrome has no
// discontinuity, so the window is not required).
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 5b. Equal-power 2-tap crossfade loop (loopMode: \xfade)
// ============================================================
(
~v = t.keyVoice(81, loop: 1, loopDir: \fwd, loopMode: \xfade);
)
// HEAR: sustained tone with NO seam click. The continuous sin/cos crossfade
// reads two buffer pointers offset by half a cycle and blends them with
// equal-power gains; phaseyness on slow material is the tradeoff.
// loopXfade is ignored in this mode (the xfade is full-cycle).
~v.do{|s| s.set(\gate, 0)};

(
~v = t.keyVoice(81, loop: 1, loopDir: \rev, loopMode: \xfade);
)
// HEAR: reverse loop with continuous crossfade.
~v.do{|s| s.set(\gate, 0)};

(
~v = t.keyVoice(81, loop: 1, loopDir: \palin, loopMode: \xfade);
)
// HEAR: identical to loopMode: \trapezoid with palin -- palindrome has no
// seam, so the xfade path falls back to a single reader.
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 5c. Stereo dispatch (loads a stereo sample, plays through \ssvoice2)
// ============================================================
// Reel Palindromue is typically stereo in the included corpus. Load it
// fresh and verify the voice path produces a stereo image.
(
s.waitForBoot{
	g = SSampler(\reelVoice).load([Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff"]);
};
)

// Stereo sample -> playVoice picks \ssvoice2 automatically. Confirm by
// checking the SynthDef name on the live node.
~v = g.keyVoice(60, loop: 1, loopDir: \fwd, loopXfade: 0.05);
~v[0].defName;                          // -> \ssvoice2 if the sample is stereo
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 6. Sub-region loop (the latter half of a section)
// ============================================================
(
~b = t.samples[0].activeBuffer[0][0];   // mono section-0 buffer
~mid = (~b.numFrames * 0.5).asInteger;
~v = t.keyVoice(81,
	loop: 1, loopDir: \fwd, loopXfade: 0.02,
	loopStart: ~mid, loopEnd: ~b.numFrames);
)
// HEAR: only the second half of the slice loops -- attack is skipped.
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 6b. Release region (Ableton-style second loop)
// ============================================================
// On note-off, the playhead crossfades from the sustain pointer to the
// release-region pointer over `releaseXfade` seconds, then plays the
// release region according to `releaseMode` while the amp envelope
// releases. `release` must be long enough for this to be audible.
(
~b = t.samples[0].activeBuffer[0][0];   // mono section-0 buffer
~rs = (~b.numFrames * 0.7).asInteger;   // release region = last 30%
~v = t.keyVoice(81,
	loop: 1, loopDir: \fwd, loopXfade: 0.05,
	release: 1.5,
	releaseMode: \loop, releaseStart: ~rs, releaseEnd: ~b.numFrames);
)
~v.do{|s| s.set(\gate, 0)};
// HEAR: while held, sustain loops the whole slice. After noteOff, the
// last 30% loops by itself, fading out over the 1.5s release tail.

// One-shot release region: plays the release region once, then silence
// (or whatever the env release leaves).
(
~v = t.keyVoice(81,
	loop: 1, loopDir: \fwd, loopXfade: 0.05,
	release: 2,
	releaseMode: \oneShot, releaseStart: ~rs, releaseEnd: ~b.numFrames);
)
~v.do{|s| s.set(\gate, 0)};
// HEAR: sustain loop, then on release the tail plays through once.

// Palindrome ("back-and-forth") release region.
(
~v = t.keyVoice(81,
	loop: 1, loopDir: \fwd, loopXfade: 0.05,
	release: 2,
	releaseMode: \palin, releaseStart: ~rs, releaseEnd: ~b.numFrames);
)
~v.do{|s| s.set(\gate, 0)};
// HEAR: sustain loop, then release region bounces back and forth.

// releaseMode \off: byte-identical to the pre-release-mode behavior.
(
~v = t.keyVoice(81, loop: 1, loopDir: \fwd, loopXfade: 0.05,
	releaseMode: \off);
)
~v.do{|s| s.set(\gate, 0)};


// ============================================================
// 7. noteOn / noteOff -- stacking and registry hygiene
// ============================================================
t.noteOn(60, vel: 100);                 // HEAR: sustained voice at MIDI 60.
t.activeVoices[60].size;                // -> 1
t.noteOff(60);                          // HEAR: release tail, then silence.
t.activeVoices;                         // -> empty after release completes.

// Stack: multiple voices on the same note.
t.noteOn(60); t.noteOn(60); t.noteOn(60);
t.activeVoices[60].size;                // -> 3
t.noteOff(60);                          // releases all three.


// ============================================================
// 8. Voice cap -- \stack vs \steal
// ============================================================
SSampler.maxVoices = 4;
SSampler.voicePolicy = \stack;

// Press five distinct notes. The fifth is dropped silently.
(5.do{|i| t.noteOn(60 + i) });
t.activeVoices.size;                    // -> 4 keys (notes 60..63)
t.voiceOrder.size;                      // -> 4
t.allNotesOff;

// Same five presses under \steal: oldest is released to make room.
SSampler.voicePolicy = \steal;
(5.do{|i| t.noteOn(60 + i) });
// HEAR: note 60 fades out as note 64 starts.
t.activeVoices.size;                    // -> 4 keys (61..64)
t.voiceOrder.size;                      // -> 4
t.allNotesOff;

// Restore defaults.
SSampler.maxVoices = 16;
SSampler.voicePolicy = \stack;


// ============================================================
// 9. MIDI keyboard + sustain pedal
// ============================================================
// Connect a USB MIDI controller before running this section.
MIDIIn.connectAll;
m = SSamplerMIDI(t);                    // route Note On/Off + CC64 to t

// Play notes on the keyboard:
//   - Each key press should sustain until released.
//   - Held sustain pedal (CC64 >= 64): releasing keys does NOT free voices.
//     Voices ring until the pedal is lifted (CC64 < 64), at which point
//     all deferred releases fire.
//   - Pressing a key that was deferred during sustain: cancels the deferred
//     release; the new press stacks on top of the still-held voice.

// Sustain-pedal state introspection:
m.sustain;                              // true while pedal is down
m.heldByPedal.asArray;                  // notes deferred by the pedal

// Clean up:
m.free;                                 // detach handlers


// ============================================================
// 10. Concatenative path unchanged -- regression check
// ============================================================
// All of these go through the stock SynthDefs and should behave exactly
// as on upstream/main.
t.key(81);
t.key([30, 40, 50, 60, 70, 80]);
t.key([30, 40, 50, 60, 70, 80], \percussive);
t.key([30, 40, 50, 60, 70, 80], \stretchshort);
t.key(60, expand: 2);
t.key(60, bendenv: [0, 1, 0.2, 1, 1, 3]);
t.playEnv(Env(), {rrand(70, 90)});
