// Listening test: peak alignment under bendenv (2026-07 cumulative-integral rewrite).
// The automated math tests live in Testcode/tests/bendenv_align_tests.scd (run headless).
// This script is for the ears: with the fix, every chord below should sound as ONE
// united gesture whose peaks land together, even under extreme pitch bends.

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
	t = SSampler(\tublar).load(a, filenameAsKeynum: true);
}
)

// --- 1. Baseline (no bend): peaks aligned, as before ---
t.key([30, 50, 70, 81]);

// --- 2. Rising bend across the whole gesture ---
// Before the fix the peaks smeared apart; now they land together while the
// whole gesture glides up a fifth.
t.key([30, 50, 70, 81], bendenv: [0, 1, 1, 1.5]);

// --- 3. Extreme swing: slow half-speed start, whip to double speed ---
// The most punishing case for alignment.
t.key([30, 50, 70, 81], bendenv: [0, 0.5, 0.4, 2.0, 0.8, 1.0, 1, 1.0]);

// --- 4. \peakat under bend: peak of the whole gesture lands at t = 2s ---
// Count "one-two" after evaluating; the united peak should hit on "two".
t.key([30, 50, 70, 81], [\peakat, 2], bendenv: [0, 0.5, 0.4, 2.0, 1, 1.0]);

// --- 5. Early \peakat forces start-trimming, still under bend ---
t.key([30, 50, 70, 81], [\peakat, 0.5], bendenv: [0, 1, 0.5, 1.8, 1, 1.0]);

// --- 6. Dive bend (descending) ---
t.key([30, 50, 70, 81], bendenv: [0, 1, 1, 0.6]);

// --- 6b. dur-capped playback WITH bend (Samplertestcode.scd:224 case) ---
// Previously the bend envelope was stretched over the samples' full length, so a
// 1-second capped gesture only saw its flat first sliver → no audible bend.
// Now the bend spans the actual gesture: the dip must be clearly audible.
t.key([70, 30, 50, 40, 60], dur: 1, bendenv: [0, 1, 0.3, 1, 1, 0.3]);

// --- 7. Regression checks: paths that must sound unchanged ---
t.key([30, 50, 70, 81]);                          // no bend
t.key([30, 50, 70, 81], \percussive);             // other strategies untouched
t.key([30, 50, 70, 81], \stretchshort);
t.key(60, expand: 2, bendenv: [0, 1, 1, 1.5]);    // granular: bend affects pitch only (grain clock is linear)

// --- 7b. expand + bend on a chord (Samplertestcode.scd:62 case) ---
// Previously each voice restarted the whole bend contour individually; now the
// contour spans the gesture — the later-entering voice comes in already bent,
// and both voices glide as ONE shape.
t.key([-60, 60], expand: 2, bendenv: [0, 1, 0.2, 1, 1, 3]);

// --- 8. Single sample sanity: bend contour audible, length follows the bend ---
t.key(81, bendenv: [0, 0.5, 1, 2]);

// --- 9. \stretchshort with mixed ± keys (two-segment stretch) ---
// Every voice now reaches its peak at the same moment AND ends together —
// including reversed (negative-key) voices whose pre-peak side is the release.
// Listen for: one united swell, one united decay, no straggling tails.
t.key([70, -81], \stretchshort);
t.key([30, -40, 50, -60, 70], \stretchshort);
t.key([70, -81], \stretchshort, bendenv: [0, 1, 1, 1.5]);  // contour sliced per voice too
