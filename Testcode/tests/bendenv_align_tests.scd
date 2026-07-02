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
var simulatePeaks = {|sorted, bendWall|
	var elapsed = 0;
	sorted.collect{|p|
		var onset, peakSource;
		elapsed = elapsed + p.wait;
		onset = elapsed;
		peakSource = (p.attackDur / p.rate.abs) - (p.position / p.rate.abs).max(0);
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

"".postln;
if(failures == 0) {
	"TESTS_PASSED".postln;
	0.exit;
} {
	("TESTS_FAILED " ++ failures).postln;
	1.exit;
};
)
