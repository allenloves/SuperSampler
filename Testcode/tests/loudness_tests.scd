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

"".postln;
if(failures == 0) {
	"TESTS_PASSED".postln;
	0.exit;
} {
	("TESTS_FAILED " ++ failures).postln;
	1.exit;
};
)
