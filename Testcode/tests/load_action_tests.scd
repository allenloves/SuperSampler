//Headless integration tests for SSampler.load's action semantics.
//Boots a real scsynth and loads real files: action must fire exactly ONCE,
//only after every file's buffers are loaded AND the sampler's post-processing
//(averages, kd-tree, keyRanges) is done. Re-loading already-loaded files must
//also fire it (the old code fired once PER FILE, unordered against the
//post-processing, and never fired at all on the already-loaded branch).
//Run: sh runtests.sh loadaction
(
var failures = 0;
var assertTrue = {|val, msg|
	if(val == true) {("ok: " ++ msg).postln} {failures = failures + 1; ("FAIL: " ++ msg).postln};
};
var dir = PathName(thisProcess.nowExecutingPath).pathOnly ++ "../Sounds/";
var files = [dir ++ "Tubular/a4.aiff", dir ++ "Tubular/a5.aiff"];
//wait for the first firing (analysis + buffer reads take a while), then keep
//listening through a grace window so extra per-file firings are caught too
var awaitFire = {|getCount, limit = 120|
	var t = 0;
	while({(getCount.value == 0) and: {t < limit}}, {0.2.wait; t = t + 0.2});
	3.wait;
};

fork {
	var fired = 0, state, sampler;
	var db, treeSize, bufnums, err;
	Server.default.bootSync;

	sampler = SSampler(\loadActionTest);
	sampler.load(files, action: {
		fired = fired + 1;
		state = (
			numSamples: (sampler.samples ? []).size,
			tree: sampler.kdTreeNode,
			numRanges: (sampler.keyRanges ? []).size,
			buffersReady: sampler.samples.notNil and: {sampler.samples.every{|smp|
				smp.activeBuffer.flat.every{|b| (b.numFrames ? 0) > 0}}},
			probe: sampler.samples !? {sampler.samples.collect{|smp|
				smp.activeBuffer.flat.collect{|b| b.numFrames}}}
		);
	});
	awaitFire.({fired});

	assertTrue.(fired == 1, "load action: fires exactly ONCE for a 2-file load (got " ++ fired ++ ")");
	assertTrue.(state.notNil and: {state[\numSamples] == 2}, "load action: both samples registered at fire time");
	assertTrue.(state.notNil and: {state[\tree].notNil}, "load action: kd-tree node built at fire time");
	assertTrue.(state.notNil and: {state[\numRanges] > 0}, "load action: keyRanges built at fire time");
	assertTrue.(state.notNil and: {state[\buffersReady] == true}, "load action: every section buffer loaded at fire time (numFrames " ++ (state !? {state[\probe]}) ++ ")");

	//reload the same files: the already-loaded branch must still complete the load
	fired = 0;
	sampler.load(files, action: { fired = fired + 1 });
	awaitFire.({fired}, 30);
	assertTrue.(fired == 1, "load action: re-loading already-loaded files still fires ONCE (got " ++ fired ++ ")");

	//=== SSampler.free must undo everything load registered ===
	db = SamplerDB.dbs[\default];

	//the kd-tree must rebuild from the sampler registry, not accumulate onto itself
	db.makeTree;
	treeSize = (db.kdTree.tryPerform(\asArray, true) ? []).size;
	db.makeTree;
	assertTrue.((db.kdTree.tryPerform(\asArray, true) ? []).size == treeSize,
		"makeTree: rebuilds from the registry instead of accumulating (size stays " ++ treeSize ++ ")");

	bufnums = sampler.samples.collect{|smp| (smp.buffer ++ smp.activeBuffer.flat).collect{|b| b.bufnum}}.flat;
	err = try { sampler.free; nil } {|e| e};
	assertTrue.(err.isNil, "free: completes without throwing (got " ++ (err !? {err.class}) ++ ")");
	assertTrue.(db.samplers[\loadActionTest].isNil, "free: sampler removed from its SamplerDB");
	assertTrue.(SSampler.allSampler[\loadActionTest].isNil, "free: sampler removed from SSampler.allSampler");
	assertTrue.((db.kdTree.tryPerform(\asArray, true) ? []).every{|entry| entry.last !== sampler},
		"free: kd-tree holds no reference to the freed sampler");
	assertTrue.(bufnums.size > 0 and: {bufnums.every{|n| Buffer.cachedBufferAt(Server.default, n).isNil}},
		"free: every server buffer released (" ++ bufnums.size ++ " bufnums uncached)");

	"".postln;
	Server.default.quit;
	if(failures == 0) {
		"TESTS_PASSED".postln;
		0.exit;
	} {
		("TESTS_FAILED " ++ failures).postln;
		1.exit;
	};
};
)
