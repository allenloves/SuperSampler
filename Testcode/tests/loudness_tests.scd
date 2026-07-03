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

section.("Section 8: playEnv global envelope slices", {
	var env = Env([0, 1, 0], [1, 1]);          // 主 env, 2 秒
	var ampenv = Env([1, 0.5], [1]);           // 使用者 ampenv (將 stretch 到 2 秒)
	var composite = SSampler.envComposite(env, ampenv);
	// composite(1.0) = env(1.0) * ampenv@mid = 1 * 0.75
	assertClose.(composite.at(1.0), 0.75, 0.01, "composite: env ** ampenv pointwise");
	assertClose.(composite.duration, 2.0, 1e-6, "composite: domain = env.duration");

	// gesture 佔 [0, 1.2] 窗口, envDur 2
	{
		var slices = SSampler.globalEnvSlices(composite, Env([-1, 1], [1]), nil, 1.2, 2.0);
		assertClose.(slices[\ampenv].duration, 1.2, 1e-6, "slice: ampenv domain = gestureDur");
		assertClose.(slices[\ampenv].at(1.0), composite.at(1.0), 0.01, "slice: t=0 anchored (gesture time = global time)");
		assertClose.(slices[\panenv].at(0.6), -1 + (0.6 / 2 * 2), 0.01, "slice: panenv sliced over gesture window");
		assertTrue.(slices[\bendenv].isNil, "slice: nil passes through");
	}.value;
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
