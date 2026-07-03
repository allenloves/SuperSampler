// Automated tests for bendenv peak alignment (cumulative-integral inversion).
// Run headless:  sclang Testcode/tests/bendenv_align_tests.scd
// Prints TESTS_PASSED or TESTS_FAILED <n> and exits with matching status code.
(
var failures = 0;
var assertClose = {|got, expected, tolerance, label|
	var ok;
	ok = try { (got - expected).abs <= tolerance } { false };
	if(ok.not) {
		failures = failures + 1;
		("FAIL: " ++ label ++ " — expected " ++ expected ++ " ± " ++ tolerance ++ ", got " ++ got).postln;
	} {
		("ok: " ++ label).postln;
	};
};
var assertTrue = {|cond, label|
	if(cond.not) {
		failures = failures + 1;
		("FAIL: " ++ label).postln;
	} {
		("ok: " ++ label).postln;
	};
};
var section = {|name, fn|
	("== " ++ name ++ " ==").postln;
	try { fn.value } {|err|
		failures = failures + 1;
		("FAIL (section error): " ++ name ++ " — " ++ err.errorString).postln;
	};
};

// Independent oracle: brute-force source consumption under a wall-time bend env.
// Returns the wall time at which `sourceAmount` (nominal-playback seconds) has
// been consumed, starting from wall time `from`.
var bruteWallTimeFor = {|env, from, sourceAmount, dt = 0.0001|
	var t = from, consumed = 0;
	while { consumed < sourceAmount } {
		var level = if(t < env.duration) { env.at(t) } { env.levels.last };
		consumed = consumed + (level * dt);
		t = t + dt;
	};
	t;
};

// Mock a SamplerPrepare with a fake SampleDescript (Event duck-typing).
// `dur` mirrors getSamplesByKeynum: samplePrep.duration = args.dur BEFORE setRate.
var mockPrep = {|attack, release, rate = 1, dur = nil|
	var total = attack + release;
	var prep = SamplerPrepare.new;
	prep.sample = (
		attackDur: [attack],
		releaseDur: [release],
		activeBuffer: [[(duration: total)]]
	);
	prep.buffer = prep.sample.activeBuffer[0];
	prep.section = 0;
	prep.duration = dur;
	prep.setRate(rate);
	prep;
};

var makeArgs = {|preps, syncmode, bendenv, dur = nil|
	var args = SamplerArguments.new;
	args.set(keynums: [60], syncmode: syncmode, bendenv: bendenv, dur: dur);
	args.setSamples(preps);  //real path: sets playSamples and runs getGlobalDur
	args;
};

// simulate scheduled playback, return each sample's wall-clock peak time
// (sign-aware: reversed voices traverse their release first, and their start
// trim is measured from the buffer END via position)
var simulatePeaks = {|sorted, bendWall|
	var elapsed = 0;
	sorted.collect{|p|
		var onset, pre, trim, peakSource;
		elapsed = elapsed + p.wait;
		onset = elapsed;
		pre = if(p.rate.isPositive) {p.attackDur} {p.releaseDur} / p.rate.abs;
		trim = if(p.rate.isPositive)
			{p.position / p.rate.abs}
			{(p.sample.activeBuffer[0][0].duration - p.position) / p.rate.abs};
		peakSource = (pre - trim.max(0)).max(0);
		bruteWallTimeFor.(bendWall, onset, peakSource);
	};
};

// Shared bend shape: strong swing, piecewise linear, duration 1 (normalized).
var bendPairs = [0, 0.5, 0.4, 2.0, 0.8, 1.0, 1, 1.0];

section.("Section 1: Env:cumIntegral / cumIntegralInverse", {
	// constant env b=2 over 1s:  M(t) = 2t
	var env = Env([2, 2], [1]);
	assertClose.(env.cumIntegral(0.5), 1.0, 1e-9, "cumIntegral constant mid");
	assertClose.(env.cumIntegral(1.0), 2.0, 1e-9, "cumIntegral constant full");
	assertClose.(env.cumIntegral(1.5), 3.0, 1e-9, "cumIntegral extends past end");
	assertClose.(env.cumIntegralInverse(1.0), 0.5, 1e-9, "inverse constant");
	assertClose.(env.cumIntegralInverse(3.0), 1.5, 1e-9, "inverse extends past end");
	assertClose.(env.cumIntegralInverse(0), 0, 1e-9, "inverse at zero");

	// linear ramp 1→3 over 2s: b(t)=1+t, M(t) = t + t²/2 ; M(1)=1.5, M(2)=4
	env = Env([1, 3], [2]);
	assertClose.(env.cumIntegral(1.0), 1.5, 1e-9, "cumIntegral ramp mid");
	assertClose.(env.cumIntegral(2.0), 4.0, 1e-9, "cumIntegral ramp full");
	assertClose.(env.cumIntegralInverse(1.5), 1.0, 1e-9, "inverse ramp mid");
	assertClose.(env.cumIntegralInverse(4.0), 2.0, 1e-9, "inverse ramp full");

	// descending segment (negative slope) round-trip
	env = Env([2, 0.5, 0.5], [1, 1]);
	assertClose.(env.cumIntegralInverse(env.cumIntegral(0.7)), 0.7, 1e-9, "inverse round-trip negative slope");

	// multi-segment round-trip at many points
	env = bendPairs.pairsAsEnv;
	assertTrue.(
		(0.05, 0.1 .. 0.95).every{|t| (env.cumIntegralInverse(env.cumIntegral(t)) - t).abs < 1e-9 },
		"inverse round-trip multi-segment sweep"
	);
});

section.("Section 2: \\keeplength peak alignment under bendenv", {
	var preps = [mockPrep.(2.0, 1.0), mockPrep.(0.8, 1.5), mockPrep.(0.1, 0.9)];
	var args = makeArgs.(preps, \keeplength, bendPairs);
	var sorted = SamplerQuery.getPlayTime(args);
	var bendWall = bendPairs.pairsAsEnv.copy.duration_(args.globalDur);
	var peakWallTimes = simulatePeaks.(sorted, bendWall);
	var elapsed = 0;

	assertTrue.(
		peakWallTimes.every{|t| (t - peakWallTimes[0]).abs < 0.002 },
		"keeplength: all peaks within 2ms (got " ++ peakWallTimes ++ ")"
	);

	// wall durations must match the bent consumption of each source
	sorted.do{|p, i|
		var onset, srcDur, wallEnd;
		elapsed = elapsed + p.wait;
		onset = elapsed;
		srcDur = (p.sample.activeBuffer[0][0].duration / p.rate.abs) - (p.position / p.rate.abs).max(0);
		wallEnd = bruteWallTimeFor.(bendWall, onset, srcDur);
		assertClose.(p.duration, wallEnd - onset, 0.002, "keeplength: wall duration of sample " ++ i);
	};
});

section.("Section 3: \\peakat peak alignment under bendenv", {
	var preps = [mockPrep.(2.0, 1.0), mockPrep.(0.8, 1.5), mockPrep.(0.1, 0.9)];
	var targetPeak = 1.2;
	var args = makeArgs.(preps, [\peakat, targetPeak], bendPairs);
	var sorted = SamplerQuery.getPlayTime(args);
	var bendWall = bendPairs.pairsAsEnv.copy.duration_(args.globalDur);
	var peakWallTimes = simulatePeaks.(sorted, bendWall);

	assertTrue.(
		peakWallTimes.every{|t| (t - targetPeak).abs < 0.002 },
		"peakat: all peaks at t=" ++ targetPeak ++ " within 2ms (got " ++ peakWallTimes ++ ")"
	);
});

section.("Section 4: \\peakat with early target trims sample starts", {
	// target peak earlier than the longest attack: the long sample must be
	// trimmed (position > 0) so its peak still lands on target.
	var preps = [mockPrep.(2.0, 1.0), mockPrep.(0.3, 0.5)];
	var targetPeak = 0.5;
	var args = makeArgs.(preps, [\peakat, targetPeak], bendPairs);
	var sorted = SamplerQuery.getPlayTime(args);
	var bendWall = bendPairs.pairsAsEnv.copy.duration_(args.globalDur);
	var peakWallTimes = simulatePeaks.(sorted, bendWall);

	assertTrue.(
		peakWallTimes.every{|t| (t - targetPeak).abs < 0.005 },
		"peakat-early: all peaks at t=" ++ targetPeak ++ " within 5ms (got " ++ peakWallTimes ++ ")"
	);
	assertTrue.(sorted[0].position > 0, "peakat-early: long sample start trimmed (position > 0)");
});

section.("Section 5: no-bend regression (\\keeplength)", {
	var preps = [mockPrep.(2.0, 1.0), mockPrep.(0.8, 1.5), mockPrep.(0.1, 0.9)];
	var args = makeArgs.(preps, \keeplength, nil);  // default bendenv
	var sorted = SamplerQuery.getPlayTime(args);

	// classic behavior: sorted by attack desc; waits are peak-time deltas
	assertClose.(sorted[0].wait, 0.0, 1e-6, "no-bend: first wait 0");
	assertClose.(sorted[1].wait, 1.2, 1e-6, "no-bend: second wait = 2.0 - 0.8");
	assertClose.(sorted[2].wait, 0.7, 1e-6, "no-bend: third wait = 0.8 - 0.1");
	assertClose.(sorted[0].duration, 3.0, 1e-6, "no-bend: duration untouched");
});

section.("Section 6: dur cap — bend domain matches the actual gesture", {
	// Long samples (attack 3 / release 5) hard-capped to dur: 1. The bend
	// envelope must span the ACTUAL ~1s gesture, not the full 8s sample length —
	// otherwise each voice only sees the flat first sliver of the bend and
	// bendenv audibly does nothing (Samplertestcode.scd:224).
	var preps = [
		mockPrep.(3.0, 5.0, 1, 1),
		mockPrep.(2.0, 4.0, 1, 1),
		mockPrep.(1.5, 3.5, 1, 1)
	];
	var args = makeArgs.(preps, \keeplength, [0, 1, 0.3, 1, 1, 0.3], 1);
	var sorted = SamplerQuery.getPlayTime(args);
	var bendWall, peakWallTimes;

	assertClose.(args.globalDur, 1.0, 1e-6,
		"dur-cap: globalDur equals the effective (capped) gesture length");

	// every voice's local bend slice must actually bend (reach the 0.3 dip region)
	sorted.do{|p, i|
		assertTrue.(p.bendenv.asEnv.levels.minItem < 0.6,
			"dur-cap: sample " ++ i ++ " local bendenv is not flat (min level "
				++ p.bendenv.asEnv.levels.minItem ++ ")");
	};

	// and peaks still align under dur + bend
	bendWall = args.bendenv.asEnv.copy.duration_(args.globalDur);
	peakWallTimes = simulatePeaks.(sorted, bendWall);
	assertTrue.(
		peakWallTimes.every{|t| (t - peakWallTimes[0]).abs < 0.002 },
		"dur-cap: peaks aligned (got " ++ peakWallTimes ++ ")"
	);
});

section.("Section 7: expand mode — bendenv sliced per voice over the gesture", {
	// Granular (expand) playback: bend must NOT alter timing (grain clock is a
	// fixed Line), but each voice must receive its SLICE of the gesture-level
	// bend contour — not the whole envelope restarted per voice
	// (Samplertestcode.scd:62). Three voices to also pin elapsed accumulation.
	//
	// mocks: attacks 2/0.5/0.2, releases 1/2.5/1, expand 2
	// globalDur = (2 + 2.5) * 2 = 9
	// onsets (plain expanded waits): 0, 3, 3.6 → normalized 0, 0.3333, 0.4
	// bend [0,1, 0.2,1, 1,3]: b(x) = 1 for x<=0.2, else 1 + ((x - 0.2) / 0.8 * 2)
	var mkPreps = {[
		mockPrep.(2.0, 1.0),
		mockPrep.(0.5, 2.5),
		mockPrep.(0.2, 1.0)
	]};
	var bend = [0, 1, 0.2, 1, 1, 3];
	var args, sorted;

	// --- \keeplength ---
	args = makeArgs.(mkPreps.(), \keeplength, bend);
	args.expand = 2;
	args.getGlobalDur;  //recompute with expand
	sorted = SamplerQuery.getPlayTime(args);

	assertClose.(args.globalDur, 9.0, 1e-6, "expand: globalDur includes expand factor");
	assertClose.(sorted[0].wait, 0.0, 1e-6, "expand: first wait 0");
	assertClose.(sorted[1].wait, 3.0, 1e-6, "expand: second wait = 1.5 * 2");
	assertClose.(sorted[2].wait, 0.6, 1e-6, "expand: third wait = 0.3 * 2");

	assertClose.(sorted[0].bendenv.asEnv.levels.first, 1.0, 0.02,
		"expand keeplength: voice A slice starts at gesture start (level 1)");
	assertClose.(sorted[0].bendenv.asEnv.levels.last, 2.1667, 0.02,
		"expand keeplength: voice A slice ends mid-ramp (b(0.667) ≈ 2.17, not 3)");
	assertClose.(sorted[1].bendenv.asEnv.levels.first, 1.3333, 0.02,
		"expand keeplength: voice B slice starts mid-gesture (b(0.333) ≈ 1.33, not 1)");
	assertClose.(sorted[2].bendenv.asEnv.levels.first, 1.5, 0.02,
		"expand keeplength: voice C slice starts at b(0.4) = 1.5 (elapsed accumulation)");

	// --- \peakat ---
	args = makeArgs.(mkPreps.(), [\peakat, 2], bend);
	args.expand = 2;
	args.getGlobalDur;
	sorted = SamplerQuery.getPlayTime(args);

	assertClose.(sorted[1].bendenv.asEnv.levels.first, 1.3333, 0.02,
		"expand peakat: voice B slice starts at b(0.333)");
	assertClose.(sorted[2].bendenv.asEnv.levels.first, 1.5, 0.02,
		"expand peakat: voice C slice starts at b(0.4) (no double-counted waits)");
});

section.("Section 8: \\stretchshort two-segment stretch with mixed ± keys", {
	// Peaks sit wherever the analysis found them (possibly near the end for
	// crescendo-like sounds), and negative keys traverse the buffer backwards —
	// so pre-peak and post-peak lengths must be aligned SEPARATELY via a
	// two-segment position envelope (no single stretch factor can do both).
	//
	// A: attack 2 / release 1, positive → pre 2,   post 1
	// B: attack 0.5 / release 2.5, rate -1 → pre 2.5, post 0.5 (reversed)
	// globalAttack = 2.5, globalRelease = 1, total wall = 3.5
	var preps = [mockPrep.(2.0, 1.0, 1), mockPrep.(0.5, 2.5, -1)];
	var args = makeArgs.(preps, \stretchshort, nil);
	var sorted = SamplerQuery.getPlayTime(args);
	var vA = sorted.detect{|p| p.rate.isPositive};
	var vB = sorted.detect{|p| p.rate.isPositive.not};
	var envA, envB;

	assertTrue.(vA.notNil and: {vB.notNil}, "stretchshort: both voices present");

	envA = vA.posenv.asEnv;
	envB = vB.posenv.asEnv;

	// both voices start together and share the same wall length
	assertClose.(vA.wait, 0, 1e-9, "stretchshort: A starts at 0");
	assertClose.(vB.wait, 0, 1e-9, "stretchshort: B starts at 0");
	assertClose.(vA.duration * (vA.expand ? 1), 3.5, 1e-6, "stretchshort: A wall length = 3.5");
	assertClose.(vB.duration * (vB.expand ? 1), 3.5, 1e-6, "stretchshort: B wall length = 3.5");

	// two-segment timing: peak hit at globalAttack (normalized 2.5/3.5)
	assertClose.(envA.times[0], 2.5/3.5, 0.001, "stretchshort: A pre-peak segment = 0.714");
	assertClose.(envB.times[0], 2.5/3.5, 0.001, "stretchshort: B pre-peak segment = 0.714");

	// A (positive): pointer 0 → peak at 2/3 of buffer → 0.95
	assertClose.(envA.levels[0], 0.0, 0.001, "stretchshort: A starts at buffer head");
	assertClose.(envA.levels[1], 2/3, 0.001, "stretchshort: A peak breakpoint at 2/3");
	// B (negative): pointer 1 → peak at 0.5/3 of buffer → 0
	assertClose.(envB.levels[0], 1.0, 0.001, "stretchshort: B starts at buffer tail");
	assertClose.(envB.levels[1], 0.5/3, 0.001, "stretchshort: B peak breakpoint at 1/6");
	assertClose.(envB.levels[2], 0.0, 0.001, "stretchshort: B ends at buffer head");

	// a voice that is already the longest on BOTH sides keeps the plain path
	preps = [mockPrep.(2.5, 1.0, 1), mockPrep.(0.5, 0.5, 1)];
	args = makeArgs.(preps, \stretchshort, nil);
	sorted = SamplerQuery.getPlayTime(args);
	assertTrue.(sorted[0].posenv.isNil,
		"stretchshort: dominant voice keeps plain playback (posenv nil)");
	assertTrue.(sorted[1].posenv.notNil,
		"stretchshort: shorter voice gets a two-segment posenv");
});

section.("Section 9: dur cap with extreme rate spread (percussive bank)", {
	// Tubular-bell case (listening test 6b, t.key([70,30,50,40,60], dur: 1, bendenv: ...)):
	// tiny attacks, long rings, keys far below the sample keynum → rate << 1. The
	// wall-clock budget is dur = 1s, but releaseDur/rate blows the naive envelope
	// domain up to ~5s, so every voice only saw the flat first sliver of the bend.
	var preps = [
		mockPrep.(0.01, 5.0, 1, 1),
		mockPrep.(0.01, 5.0, 2 ** (-11/12), 1),
		mockPrep.(0.01, 5.0, 2 ** (-39/12), 1)
	];
	var args = makeArgs.(preps, \keeplength, [0, 1, 0.3, 1, 1, 0.3], 1);
	var sorted = SamplerQuery.getPlayTime(args);

	assertTrue.(args.globalDur < 1.2,
		"rate-spread dur-cap: globalDur stays near the 1s wall budget (got " ++ args.globalDur ++ ")");
	sorted.do{|p, i|
		assertTrue.(p.bendenv.asEnv.levels.minItem < 0.6,
			"rate-spread dur-cap: voice " ++ i ++ " bendenv not flat (min "
				++ p.bendenv.asEnv.levels.minItem ++ ")");
	};
});

section.("Section 10: \\peakat trimming keeps envelopes on the nominal timeline", {
	// Symmetric ± pair (Samplertestcode.scd:76 case): forward post-peak == reversed
	// pre-peak, so the nominal gesture is symmetric and the aligned peak sits at the
	// exact middle of the envelope domain. An early target trims the reversed
	// voice's source start — the envelope window must skip the SAME leading
	// portion, so Env([3,0,3])'s central zero still lands on the aligned peak.
	//
	// mocks: attack 0.5 / release 8 at rate ±1 → pre/post maxima both 8,
	// globalDur 16, peak at nominal 8 (= domain middle). target 2 → reversed
	// voice trimmed by 6 (pre 8 → 2), forward voice waits 1.5.
	var preps = [mockPrep.(0.5, 8.0, 1), mockPrep.(0.5, 8.0, -1)];
	var args = makeArgs.(preps, [\peakat, 2], nil);
	var sorted, vRev, vFwd;
	args.set(ampenv: Env([3, 0, 3]));
	sorted = SamplerQuery.getPlayTime(args);
	vRev = sorted[0];  // pre 8 — sorted first
	vFwd = sorted[1];

	assertClose.(args.globalDur, 16, 1e-6, "peakat-trim: symmetric nominal domain");
	assertClose.(vRev.wait, 0, 1e-9, "peakat-trim: trimmed voice starts immediately");
	assertClose.(vFwd.wait, 1.5, 1e-6, "peakat-trim: forward voice waits target - pre");
	assertClose.(vRev.duration, 2.5, 1e-6, "peakat-trim: trimmed voice duration = remaining source");

	// reversed voice: env window [6/16, +2.5/16]; its peak at local 0.8 → global 0.5 → 0
	assertClose.(vRev.ampenv.asEnv.at(0.8), 0, 0.02,
		"peakat-trim: trimmed voice ampenv is ZERO at the aligned peak");
	assertClose.(vRev.ampenv.asEnv.at(0), 0.75, 0.02,
		"peakat-trim: trimmed voice ampenv starts at global(0.375) = 0.75");

	// forward voice: window [7.5/16, +8.5/16]; peak at local 0.5/8.5 → global 0.5 → 0
	assertClose.(vFwd.ampenv.asEnv.at(0.5 / 8.5), 0, 0.02,
		"peakat-trim: forward voice ampenv is ZERO at the aligned peak");
	assertClose.(vFwd.ampenv.asEnv.at(1), 3, 0.02,
		"peakat-trim: forward voice ampenv ends at global end = 3");
});

section.("Section 11: bend branches anchor + WARP envelopes to the nominal timeline", {
	// Same principle as Section 10, with bendenv active: envelope windows sit on
	// the nominal timeline (trim-aware), and breakpoint times are warped through
	// the bend map M so the contour stays glued to the source content. The
	// assertions probe the env at the WALL position where the peak actually
	// sounds — this is what the synth (linear wall-time EnvGen) will play.
	var preps, args, sorted, vRev, vFwd, bendWall, onsetFwd, peakWall;

	// --- \peakat with trimming ---
	preps = [mockPrep.(0.5, 8.0, 1), mockPrep.(0.5, 8.0, -1)];
	args = makeArgs.(preps, [\peakat, 2], bendPairs);
	args.set(ampenv: Env([3, 0, 3]), bendenv: bendPairs);  //set() resets omitted envs to defaults
	sorted = SamplerQuery.getPlayTime(args);
	vRev = sorted[0];  // pre 8 -> trimmed, onset 0
	vFwd = sorted[1];
	onsetFwd = vRev.wait + vFwd.wait;

	assertClose.(vRev.ampenv.asEnv.at((2 - 0) / vRev.duration), 0, 0.02,
		"bend-peakat: trimmed voice ampenv ZERO at the audible peak (wall position)");
	assertClose.(vFwd.ampenv.asEnv.at((2 - onsetFwd) / vFwd.duration), 0, 0.02,
		"bend-peakat: forward voice ampenv ZERO at the audible peak (wall position)");
	assertClose.(vFwd.ampenv.asEnv.at(1), 3, 0.05,
		"bend-peakat: forward voice ampenv ends at global end = 3");
	bendWall = bendPairs.pairsAsEnv.copy.duration_(args.globalDur);
	assertTrue.(simulatePeaks.(sorted, bendWall).every{|t| (t - 2).abs < 0.005},
		"bend-peakat: peaks still aligned at target (timing untouched)");

	// --- \keeplength (no trim; nominal peak at domain middle) ---
	preps = [mockPrep.(0.5, 8.0, 1), mockPrep.(0.5, 8.0, -1)];
	args = makeArgs.(preps, \keeplength, bendPairs);
	args.set(ampenv: Env([3, 0, 3]), bendenv: bendPairs);  //set() resets omitted envs to defaults
	sorted = SamplerQuery.getPlayTime(args);
	bendWall = bendPairs.pairsAsEnv.copy.duration_(args.globalDur);
	peakWall = simulatePeaks.(sorted, bendWall)[0];
	assertClose.(sorted[0].ampenv.asEnv.at((peakWall - 0) / sorted[0].duration), 0, 0.02,
		"bend-keeplength: first voice ampenv ZERO at the audible peak");
	assertClose.(sorted[1].ampenv.asEnv.at((peakWall - sorted[1].wait) / sorted[1].duration), 0, 0.02,
		"bend-keeplength: second voice ampenv ZERO at the audible peak");
});

section.("Section 12: \\percussive with negative keys gives a minimum audible snippet", {
	// Reversed \\percussive used to be effectively silent: playback starts at the
	// peak (attackDur - 0.01) and runs backward into a few-ms attack. Now the
	// start position is floored so at least ~0.03s (in AUDIBLE seconds, i.e.
	// scaled by |rate|) of reversed transient sounds. Natural behavior is kept
	// when the attack is already long enough, and forward voices are untouched.
	var preps = [
		mockPrep.(0.01, 2.0, 1),     // forward percussive — untouched
		mockPrep.(0.01, 2.0, -2),    // reversed, tiny attack, transposed up
		mockPrep.(0.01, 2.0, -0.5),  // reversed, tiny attack, transposed down
		mockPrep.(1.0, 2.0, -1)      // reversed, attack already long — natural
	];
	var args = makeArgs.(preps, \percussive, nil);
	var sorted = SamplerQuery.getPlayTime(args);
	var fwd = sorted.detect{|p| p.rate.isPositive};
	var revHi = sorted.detect{|p| p.rate == -2};
	var revLo = sorted.detect{|p| p.rate == -0.5};
	var revNat = sorted.detect{|p| p.rate == -1 and: {p.attackDur == 1.0}};

	assertClose.(fwd.position, 0.0, 1e-6, "percussive: forward tiny-attack position unchanged (attackDur-0.01 -> 0)");
	assertClose.(revHi.position / 2, 0.03, 1e-6, "percussive: reversed (rate -2) audible length floored at 0.03s");
	assertClose.(revLo.position / 0.5, 0.03, 1e-6, "percussive: reversed (rate -0.5) audible length floored at 0.03s");
	assertClose.(revNat.position, 0.99, 1e-6, "percussive: reversed long attack keeps natural start (attackDur-0.01)");
	assertClose.(revHi.duration, 0.03, 1e-6, "percussive: reversed voice duration = audible snippet length");
	assertClose.(revNat.duration, 0.99, 1e-6, "percussive: reversed natural voice duration = attack playback time");
});

section.("Section 13: \\peakat target is wall-clock under expand", {
	// expand stretches playback 10x; [\peakat, 3] must still land peaks at
	// wall-clock 3s, not 3 * expand = 30s (Samplertestcode.scd:190).
	var preps = [mockPrep.(0.2, 1.0), mockPrep.(0.05, 1.0)];
	var args = makeArgs.(preps, [\peakat, 3], nil);
	var sorted;
	args.expand = 10;
	args.getGlobalDur;
	sorted = SamplerQuery.getPlayTime(args);

	assertClose.(sorted[0].wait, 1.0, 1e-6, "expand-peakat: first onset = 3 - (0.2 * 10)");
	assertClose.(sorted[1].wait, 1.5, 1e-6, "expand-peakat: second onset 2.5 (delta 1.5)");
	assertClose.(sorted[0].wait + (sorted[0].attackDur * 10), 3.0, 1e-6,
		"expand-peakat: first peak at wall 3");
	assertClose.(sorted[0].wait + sorted[1].wait + (sorted[1].attackDur * 10), 3.0, 1e-6,
		"expand-peakat: second peak at wall 3");

	// trimming: attack*expand exceeds the target -> source start trimmed so the
	// (stretched) remaining attack still peaks exactly at wall 3
	preps = [mockPrep.(0.5, 1.0)];
	args = makeArgs.(preps, [\peakat, 3], nil);
	args.expand = 10;
	args.getGlobalDur;
	sorted = SamplerQuery.getPlayTime(args);
	assertClose.(sorted[0].wait, 0, 1e-6, "expand-peakat-trim: starts immediately");
	assertClose.(sorted[0].position, 0.2, 1e-6, "expand-peakat-trim: source trimmed by pk - target/expand");
});

"".postln;
if(failures == 0) {
	"TESTS_PASSED".postln;
	0.exit;
} {
	("TESTS_FAILED " ++ failures).postln;
	1.exit;
};
)
