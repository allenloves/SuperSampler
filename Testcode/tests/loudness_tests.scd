// Automated tests for SamplerVoice proxy + registry migration (Task 1).
// Run headless:  sclang Testcode/tests/loudness_tests.scd
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

section.("Section 1: SamplerVoice proxy", {
	var setCalls = [], onFreeFn, freeResult;
	// NB: an Event literal (set: ..., free: ...) can't be used here — Event already
	// defines real .set/.free methods (JITLib's NodeEvents extension, for controlling
	// Events-as-pseudo-nodes), which would shadow these keys and never reach them via
	// doesNotUnderstand. IdentityDictionary has no such methods, so with know_(true) it
	// exercises the same key-lookup doesNotUnderstand path a real Synth would take.
	var mockSynth = IdentityDictionary.new.know_(true);
	var v;
	mockSynth.put(\set, {|self ...args| setCalls = setCalls.add(args)});
	mockSynth.put(\onFree, {|self, fn| onFreeFn = fn});
	v = SamplerVoice(mockSynth, 0.7, 12.5, \fakeSample, 0, 1.0, 3.5, \g1);

	assertClose.(v.ampScale, 0.7, 1e-9, "voice: ampScale getter");
	assertClose.(v.onsetAbs, 12.5, 1e-9, "voice: onsetAbs getter");
	assertTrue.(v.gestureID == \g1, "voice: gestureID getter");

	v.set(\gate, 0);   // 未知訊息 → 轉發給 synth
	assertTrue.(setCalls == [[\gate, 0]], "voice: .set forwards to synth (MIDI CC 用法不變)");

	// Object itself defines a no-op free (^this), so a naïve doesNotUnderstand-only
	// proxy would silently swallow .free on the voice instead of reaching the synth.
	// SamplerVoice overrides .free explicitly; confirm it truly reaches the synth by
	// checking the call resolves on mockSynth (whose own Object:free returns ^this),
	// not on the voice itself (which would return the voice instead).
	freeResult = v.free;
	assertTrue.(freeResult === mockSynth, "voice: .free forwards to synth (not swallowed by Object's no-op free)");
});

section.("Section 2: registerVoice bookkeeping", {
	var onFreeFn;
	var mockSynth = (set: {|self ...args|}, onFree: {|self, fn| onFreeFn = fn});
	var prep = SamplerPrepare.new;
	var args = SamplerArguments.new;
	prep.sample = (attackDur: [1], releaseDur: [1], activeBuffer: [[(duration: 2.0)]], peakAmp: [0.5]);
	prep.section = 0; prep.midiChannel = 3; prep.setRate(1);
	args.set(keynums: [60], amp: 0.8);

	prep.registerVoice(mockSynth, args, \testID);
	assertTrue.(SamplerQuery.playing[3][\testID].isKindOf(SamplerVoice), "register: entry is SamplerVoice");
	assertClose.(SamplerQuery.playing[3][\testID].wallDur, 2.0, 1e-6, "register: wallDur from duration");
	assertClose.(SamplerQuery.playing[3][\testID].ampScale, 0.4, 1e-9, "register: ampScale includes section peakAmp (true output units)");
	onFreeFn.value;   // 模擬 node free
	assertTrue.(SamplerQuery.playing[3][\testID].isNil, "register: onFree removes entry");
});

section.("Section 3: gestureID + releaseGesture", {
	var setCalls = [];
	// Same mock strategy as Section 1: IdentityDictionary + know_(true), NOT an Event
	// literal — Event's NodeEvents extension defines real .set, which would shadow a
	// (set: ...) key and never reach it via doesNotUnderstand.
	var mkSynth = {|gid|
		var synth = IdentityDictionary.new.know_(true);
		synth.put(\set, {|self ...a| setCalls = setCalls.add([gid] ++ a)});
		synth.put(\onFree, {|self, fn|});
		synth;
	};
	var mkVoice = {|gid| SamplerVoice(mkSynth.(gid), 1, 0, nil, 0, 1, 1, gid)};
	var a1 = SamplerArguments.new, a2 = SamplerArguments.new;

	assertTrue.(a1.gestureID.notNil and: {a1.gestureID != a2.gestureID}, "gestureID: auto-assigned and unique");

	SamplerQuery.playing[0].put(\v1, mkVoice.(\gA));
	SamplerQuery.playing[0].put(\v2, mkVoice.(\gB));
	SamplerQuery.playing[5].put(\v3, mkVoice.(\gA));

	SamplerQuery.releaseGesture([\gA]);
	assertTrue.(setCalls.size == 2 and: {setCalls.every{|c| c == [\gA, \gate, 0]}},
		"releaseGesture: exactly the two \\gA voices gated, \\gB untouched (got " ++ setCalls ++ ")");
	SamplerQuery.playing[0].removeAt(\v1); SamplerQuery.playing[0].removeAt(\v2); SamplerQuery.playing[5].removeAt(\v3);
});

section.("Section 4: normalize gain stamping", {
	// 直接測 getSamplesByKeynum 的蓋章路徑需要完整 sampler；改測與其等價的公式封裝:
	// SamplerQuery.*normGainFor {|sampler, sample, section|}
	var mockSampler = (normalize: true);
	var mockSample = (peakAmp: [0.5, 0.001]);
	assertClose.(SamplerQuery.normGainFor(mockSampler, mockSample, 0), 0.7 / 0.5, 1e-9,
		"normGain: headroomRef / peakAmp");
	assertClose.(SamplerQuery.normGainFor(mockSampler, mockSample, 1), 0.7 / 0.001, 1e-9,
		"normGain: tiny peak clamps at 1e-4 floor -- here 0.001 > floor so exact");
	mockSampler = (normalize: false);
	assertClose.(SamplerQuery.normGainFor(mockSampler, mockSample, 0), 1.0, 1e-9,
		"normGain: normalize off -> 1");
});

section.("Section 5: ampShapeAt + prediction", {
	var mockRms = [0, 0.5, 1.0, 0.5, 0];   // 三角形，peak 在 normPos 0.5
	var mkVoice = {|amp, onset, wallDur, shape|
		SamplerVoice((set: {|self ...a|}, onFree: {|self, fn|}), amp, onset,
			(ampShapeAt: {|self, sec, pos| shape.(pos)}), 0, 1, wallDur, \gX)};
	var triShape = {|pos| if(pos.isNil or: {pos < 0} or: {pos >= 1}) {0} {1 - ((pos - 0.5).abs * 2)}};

	// ampShapeAt 本體 (真 SampleDescript 的陣列內插): 用鴨子型別繞不過, 直接建最小 mock 測公式
	// -> 對 SampleDescript 實體測太重, 由公式測試涵蓋: 建一個帶 rmsDataBySection 的假物件不可行,
	//    因此 ampShapeAt 以 SamplerQuery.*shapeLookup {|data, normPos|} 純函式實作, SampleDescript 轉呼叫它
	assertClose.(SamplerQuery.shapeLookup(mockRms, 0.5), 1.0, 1e-9, "shape: peak");
	assertClose.(SamplerQuery.shapeLookup(mockRms, 0.25), 0.5, 1e-9, "shape: interpolated");
	assertClose.(SamplerQuery.shapeLookup(mockRms, 1.2), 0.0, 1e-9, "shape: past end -> 0");
	assertClose.(SamplerQuery.shapeLookup(mockRms, -0.1), 0.0, 1e-9, "shape: negative -> 0");

	// predictedLevelAt: 兩個在響 voice, onset 0 與 2, wallDur 4, 三角 shape, amp 0.6/0.8
	SamplerQuery.playing[0].put(\p1, mkVoice.(0.6, 0, 4, triShape));
	SamplerQuery.playing[0].put(\p2, mkVoice.(0.8, 2, 4, triShape));
	// t=2: voice1 normPos 0.5 -> shape 1 -> 0.6 ; voice2 normPos 0 -> 0
	assertClose.(SamplerQuery.predictedLevelAt(2), 0.6, 1e-6, "predict: level at t=2");
	// t=4: voice1 pos 1 -> 0 ; voice2 pos 0.5 -> 0.8
	assertClose.(SamplerQuery.predictedLevelAt(4), 0.8, 1e-6, "predict: level at t=4");
	SamplerQuery.playing[0].removeAt(\p1); SamplerQuery.playing[0].removeAt(\p2);
});

section.("Section 7: playEnv gesture collection", {
	var a1 = SamplerArguments.new, a2 = SamplerArguments.new;
	var score = SamplerScore.new;
	score.add([a1, 0]); score.add([a2, 0.03]);
	assertTrue.(SSampler.gestureIDsOf(score).asSet == Set[a1.gestureID, a2.gestureID],
		"playEnv: collects unique gestureIDs from score");
});

section.("Section 8: playEnv climax-anchored gesture slices", {
	var env = Env([0, 1, 0], [1, 1]);          // 主 env, 2 秒, 牆鐘錨定
	var ampenv = Env([1, 0.5], [1]);           // 使用者 ampenv (將 stretch 到 2 秒)
	var composite = SSampler.envComposite(env, ampenv);
	var slices;
	// composite(1.0) = env(1.0) * ampenv@mid = 1 * 0.75
	assertClose.(composite.at(1.0), 0.75, 0.01, "composite: env ** ampenv pointwise");
	assertClose.(composite.duration, 2.0, 1e-6, "composite: domain = env.duration");

	// gesture: peak 目標在牆鐘 1.5, 名義 attack 0.5 / release 1.0 —
	// 切片必須讓「climax 錨點」(attackDur 位置) 讀到全域輪廓在 peak 牆鐘時刻的值,
	// 與 getPlayTime 的名義軸錨定精確對接
	slices = SSampler.globalEnvSlices(composite, Env([-1, 1], [1]), nil, 1.5, 0.5, 1.0, 2.0);
	assertClose.(slices[\ampenv].duration, 1.5, 1e-6, "slice: domain = attack + release");
	assertClose.(slices[\ampenv].at(0.5), composite.at(1.5), 0.01,
		"slice: value at the attackDur point == global contour at the peak wall time");
	assertClose.(slices[\panenv].at(0.5), Env([-1, 1], [1]).stretch(2.0).at(1.5), 0.01,
		"slice: panenv climax-anchored too");
	assertTrue.(slices[\bendenv].isNil, "slice: nil passes through");

	// end-peak case (Samplertestcode.scd:134 bug): peak at the env END — the
	// gesture env must be LOUD at its climax (reading the global end level),
	// and the tail beyond the global env clamps to that level (accent-then-cut,
	// not a fade that swallows the peak)
	slices = SSampler.globalEnvSlices(Env([1, 0, 1], [1, 1]), nil, nil, 2.0, 0.04, 4.0, 2.0);
	assertClose.(slices[\ampenv].at(0.04), 1.0, 0.02,
		"slice: END-peak gesture is LOUD at its climax (not the mid dip)");
	assertClose.(slices[\ampenv].at(4.04), 1.0, 0.02,
		"slice: tail beyond the global env clamps to the end level");
});

section.("Section 10: setKeynums rebuilds keyRanges", {
	// setKeynums passed the radius as setKeyRanges' STRATEGY argument, which
	// matched no switch case — keyRanges silently never followed the new keynums.
	var t = SSampler(\krTest10);
	var s1 = (keynum: [60.0]), s2 = (keynum: [70.0]);
	t.instVarPut(\samples, [s1, s2]);
	t.setKeyRanges;

	assertClose.(t.keyRanges[s1][0][0], 55, 1e-6, "keyRanges baseline: 60 - 5");
	assertClose.(t.keyRanges[s2][0][1], 75, 1e-6, "keyRanges baseline: 70 + 5");

	t.setKeynums([[64], [72]]);
	assertClose.(s1.keynum[0], 64, 1e-6, "setKeynums updates the sample keynum");
	assertClose.(t.keyRanges[s1][0][0], 59, 1e-6, "keyRanges follows setKeynums (64 - 5)");
	assertClose.(t.keyRanges[s1][0][1], 69, 1e-6, "keyRanges follows setKeynums (64 + 5)");
	assertClose.(t.keyRanges[s2][0][1], 77, 1e-6, "keyRanges follows setKeynums (72 + 5)");

	t.setKeynums([[40]], [true, 2]);  // custom radius via resetKeyRanges[1]
	assertClose.(t.keyRanges[s1][0][0], 38, 1e-6, "keyRanges honors the custom radius (40 - 2)");

	t.setKeyRanges(\keynumOnly);
	t.setKeynums([[50]]);
	assertClose.(t.keyRanges[s1][0][0], 50, 1e-6, "setKeynums keeps the current strategy (keynumOnly low)");
	assertClose.(t.keyRanges[s1][0][1], 50, 1e-6, "setKeynums keeps the current strategy (keynumOnly high)");
});

section.("Section 11: getSamplesByKeynum — distinct preps, in-tune original", {
	// The keyRange loop mutated and re-added ONE shared SamplerPrepare, and the
	// texture wrapExtend "copies" were references to that same object — so the
	// final rand2(0.3) detune retuned EVERY voice (no in-tune original left),
	// and overlapping range matches collapsed onto the last section.
	var mkSample = {|keynums|
		(keynum: keynums,
		 activeBuffer: keynums.collect{[(duration: 1.0)]},
		 attackDur: keynums.collect{0.2},
		 releaseDur: keynums.collect{0.8},
		 peakAmp: keynums.collect{1.0})
	};
	var sample1 = mkSample.([60.0]);
	var sampler1 = (keyRanges: Dictionary[sample1 -> [[60, 60]]],
		name: \mockSampler11a, bufServer: nil, normalize: false, samples: [sample1]);
	var sample2 = mkSample.([60.0, 60.0]);
	var sampler2 = (keyRanges: Dictionary[sample2 -> [[55, 65], [55, 65]]],
		name: \mockSampler11b, bufServer: nil, normalize: false, samples: [sample2]);
	var args, list;

	// texture filling from a single \keynumOnly match
	args = SamplerArguments.new;
	args.set(keynums: [60], texture: 3);
	list = SamplerQuery.getSamplesByKeynum(sampler1, args);
	assertTrue.(list.size == 3, "texture: fills to 3 voices (got " ++ list.size ++ ")");
	assertTrue.(list.collect{|pp| pp.identityHash}.asSet.size == 3,
		"texture: three DISTINCT prep objects (no aliasing)");
	assertTrue.(list.any{|pp| pp.rate == 1.0},
		"texture: the original voice stays exactly in tune (rate 1)");
	assertTrue.(list.every{|pp| (pp.rate.abs >= (2 ** (-0.31/12))) and: {pp.rate.abs <= (2 ** (0.31/12))}},
		"texture: detune bounded by ±0.3 semitones");

	// overlapping multi-section match keeps both sections
	args = SamplerArguments.new;
	args.set(keynums: [60], texture: 2);
	list = SamplerQuery.getSamplesByKeynum(sampler2, args);
	assertTrue.(list.collect{|pp| pp.section}.asSet == Set[0, 1],
		"overlap: both matching sections survive (got sections " ++ list.collect{|pp| pp.section} ++ ")");
});

section.("Section 12: oversized envelopes are resampled to fit SynthDef controls", {
	// scsynth prints "envelope went past end of inputs" and reads garbage when an
	// env array exceeds the control size (32 segs for amp/pan/bend, 8 for posenv).
	var big = Env(Array.series(101, 0.0, 0.01), Array.fill(100, 0.01)).asArray;
	var small = Env([1, 1], [1]).asArray;
	var prep = SamplerPrepare.new;

	prep.ampenv = big;
	assertTrue.(((prep.ampenv.size - 4) / 4) <= 32, "cap: oversized ampenv resampled to <= 32 segments");
	assertClose.(prep.ampenv.asEnv.duration, 1.0, 1e-6, "cap: resampling preserves the duration");
	assertClose.(prep.ampenv.asEnv.at(0.5), 0.5, 0.02, "cap: resampling preserves the shape (midpoint)");

	prep.bendenv = small;
	assertTrue.(prep.bendenv == small, "cap: small envs pass through unchanged");
	prep.panenv = nil;
	assertTrue.(prep.panenv.isNil, "cap: nil passes through");

	prep.posenv = Env(Array.series(21, 0.0, 0.05), Array.fill(20, 0.05)).asArray;
	assertTrue.(((prep.posenv.size - 4) / 4) <= 8, "cap: posenv fits its 8-segment control");
});

section.("Section 13: \\keynumOnly fallback plays the nearest sample UNtransposed", {
	// \\keynumOnly promises "no pitch-changed sounds" — a key with no mapping now
	// falls back to the nearest sample at its ORIGINAL pitch (reversal kept);
	// other strategies keep the historical transposing fallback.
	var mkSample = {|keynums|
		(keynum: keynums,
		 activeBuffer: keynums.collect{[(duration: 1.0)]},
		 attackDur: keynums.collect{0.2},
		 releaseDur: keynums.collect{0.8},
		 peakAmp: keynums.collect{1.0})
	};
	var sample1 = mkSample.([60.0]);
	var samplerStrict = (keyRanges: Dictionary[sample1 -> [[60, 60]]], keyRangeStrategy: \keynumOnly,
		name: \mock13a, bufServer: nil, normalize: false, samples: [sample1], textureDetune: 0);
	var samplerLoose = (keyRanges: Dictionary[sample1 -> [[60, 60]]],
		name: \mock13b, bufServer: nil, normalize: false, samples: [sample1], textureDetune: 0);
	var args, list;

	args = SamplerArguments.new; args.set(keynums: [80], texture: 1);
	list = SamplerQuery.getSamplesByKeynum(samplerStrict, args);
	assertClose.(list[0].rate, 1.0, 1e-9, "keynumOnly fallback: nearest sample at rate 1 (no transposition)");

	args = SamplerArguments.new; args.set(keynums: [-80], texture: 1);
	list = SamplerQuery.getSamplesByKeynum(samplerStrict, args);
	assertClose.(list[0].rate, -1.0, 1e-9, "keynumOnly fallback: reversed keys stay reversed at rate -1");

	args = SamplerArguments.new; args.set(keynums: [80], texture: 1);
	list = SamplerQuery.getSamplesByKeynum(samplerLoose, args);
	assertClose.(list[0].rate, 2 ** (20/12), 1e-6, "other strategies keep the historical transposing fallback");
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
