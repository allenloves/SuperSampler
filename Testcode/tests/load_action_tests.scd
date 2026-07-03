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
	var db, treeSize, bufnums, err, kdBefore, t0;
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
	kdBefore = sampler.kdTreeNode.copy;
	fired = 0;
	sampler.load(files, action: { fired = fired + 1 });
	awaitFire.({fired}, 30);
	assertTrue.(fired == 1, "load action: re-loading already-loaded files still fires ONCE (got " ++ fired ++ ")");
	//the old code re-divided the already-averaged stats by numActiveBuffer on
	//every call, shrinking kdTreeNode each time the same files were re-loaded
	assertTrue.(sampler.kdTreeNode == kdBefore,
		"re-load: corpus statistics unchanged (kdTreeNode stays " ++ kdBefore ++ ", got " ++ sampler.kdTreeNode ++ ")");

	//duplicate entries in ONE call: warn, skip, and leave everything intact
	fired = 0;
	sampler.load(files ++ [files[0]], action: { fired = fired + 1 });
	awaitFire.({fired}, 30);
	assertTrue.(fired == 1, "dup-list: action still fires once (got " ++ fired ++ ")");
	assertTrue.(sampler.samples.size == 2, "dup-list: no extra samples registered (got " ++ sampler.samples.size ++ ")");
	assertTrue.(sampler.kdTreeNode == kdBefore, "dup-list: corpus statistics unchanged");

	//server data lost behind our back (e.g. a reboot): load must ASK the server
	//(async /b_query) instead of trusting the stale client-side numFrames,
	//and actually re-read the file
	sampler.samples[0].buffer.do{|b| b.free};
	sampler.samples[0].activeBuffer.flat.do{|b| b.free};
	fired = 0;
	sampler.load(files, action: { fired = fired + 1 });
	awaitFire.({fired}, 60);
	assertTrue.(fired == 1, "buffer-lost: reload fires action once (got " ++ fired ++ ")");
	assertTrue.(sampler.samples.every{|smp|
			(smp.buffer ++ smp.activeBuffer.flat).every{|b|
				Buffer.cachedBufferAt(Server.default, b.bufnum).notNil and: {(b.numFrames ? 0) > 0}}},
		"buffer-lost: every buffer is live on the server again");
	assertTrue.(sampler.samples.size == 2, "buffer-lost: sample count unchanged (got " ++ sampler.samples.size ++ ")");

	//waitOnUnixCmd must block until the spawned process exits — the analysis
	//pipeline reads the NRT feature file right after it returns (the old
	//ps-based polling doesn't exist on Windows and fell through after ~0.1s)
	t0 = Main.elapsedTime;
	SS_SCMIR.waitOnUnixCmd("sleep 1", 100);
	assertTrue.(Main.elapsedTime - t0 >= 0.9,
		"waitOnUnixCmd: blocks until the spawned process exits (elapsed " ++ (Main.elapsedTime - t0).round(0.01) ++ "s)");

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
