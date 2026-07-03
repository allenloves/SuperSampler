//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, it is now included in SuperSampler boundle.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
SSampler {

	classvar < allSampler;
	classvar <> defaultTexture;
	classvar < defaultOutputBus = 0;  //USER setting: SuperSampler's final destination (e.g. your FX-chain bus); the limiter follows it — see *defaultOutputBus_
	classvar <> defaultLoadingServer;
	classvar <> headroomRef = 0.7;
	classvar limiterSynth, <limiterEnabled = true, <limiterBus;  //dedicated internal stereo bus, allocated per server boot
	classvar limiterHooksRegistered = false;

	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	var <> normalize = true;  //when true, getSamplesByKeynum stamps each SamplerPrepare's normGain to align sample peaks to headroomRef
	var <> textureDetune = 0.3;  //max random detune (semitones) for texture-filling copies; 0 locks them to the exact pitch
	//                                                                |- section -|   |- section -|
	var <keyRanges;// Is a dictionary in format  (SampleDescrtipt -> [[lower, upper], [lower, upper],..], ..)
	var <keyRangeStrategy;  //strategy last used by setKeyRanges; setKeynums reuses it, and the
	                        //query fallback goes untransposed under \keynumOnly

	//Sampler metadata
	var <numActiveBuffer;
	var <averageDuration;
	var <averageTemporalCentroid;
	var <averageMFCC;

	var <kdTreeNode; // temporarily setting to be [averageDuration, averageTemporalCentroid, averageMFCC].flat

	// Initialization in this class is in SamplerInstruments.sc
	*initClass {
	}

	*new{arg samplerName, dbname = \default;

		if(allSampler.at(samplerName.asSymbol).isNil)
		{^super.new.init(samplerName, dbname)}
		{^allSampler.at(samplerName.asSymbol)};
	}



	//All distinct gestureIDs recorded in a SamplerScore ([args, delay] pairs).
	*gestureIDsOf {|argslist|
		^argslist.collect{|pair| pair[0].gestureID}.asSet.asArray
	}

	//Normalize a user-supplied envelope (flat pairs array or Env instance) to an Env,
	//matching the case handling in SamplerArguments.set.
	*toFlatEnv {|e|
		^case
			{e.isKindOf(Env)} {e}
			{e.isArray} {e.pairsAsEnv}
			{true} {e.asEnv};
	}

	//Amplitude macro-contour of a playEnv call: the main env pointwise-multiplied
	//by the user ampenv (stretched to the same domain). ampenv nil -> env itself.
	*envComposite {|env, ampenv|
		if(ampenv.isNil) {^env};
		^env ** SSampler.toFlatEnv(ampenv).stretch(env.duration);
	}

	//Slice the global (WALL-anchored, env.duration-domain) contours into one
	//gesture's CLIMAX-anchored envelope: a window of nominal length
	//attackDur + releaseDur positioned so the gesture's aligned peak (wall time
	//targetPeak) sits exactly at the attackDur point — matching getPlayTime's
	//nominal-timeline anchoring (peak at globalAttackDur). Values outside the
	//global env clamp to its edge levels, so an end-of-envelope peak holds its
	//level until the end-of-envelope cleanup gates the voices (accent-then-cut).
	*globalEnvSlices {|composite, panenv, bendenv, targetPeak, attackDur, releaseDur, envDur|
		var len = (attackDur + releaseDur).max(0.001);
		var start = targetPeak - attackDur;
		var slice = {|e| e !? {SSampler.toFlatEnv(e).stretch(envDur).subEnv(start, len)} };
		^(
			ampenv: slice.(composite),
			panenv: slice.(panenv),
			bendenv: slice.(bendenv)
		);
	}

	// args is a realization of SamplerArguments
	*playArgs{|args|
		args.playSamples = SamplerQuery.getPlayTime(args); // organize play time by peak and stratges

		Routine.run{
			args.playSamples.do{|thisSample, index| //thisSample are realizations of SamplerPrepare class
				var bufRateScale = thisSample.bufServer.sampleRate / thisSample.sample.sampleRate;
				var buf = thisSample.buffer;
				var duration = args.dur ? ((thisSample.sample.activeDuration[thisSample.section]) / thisSample.rate.abs) * bufRateScale; // * (args.expand ? 1)
				var synthID = UniqueID.next.asSymbol;

				thisSample.wait.wait;
				thisSample.play(args, synthID);
				};
			}
	}

	//Setting defaultOutputBus is the USER's routing decision (e.g. point SuperSampler
	//at your own FX-chain bus). The limiter output follows it live.
	*defaultOutputBus_ {|bus|
		defaultOutputBus = bus;
		limiterSynth !? { limiterSynth.set(\out, bus) };
	}

	//Where voices actually write by default: through the internal limiter bus when
	//the limiter is up, straight to defaultOutputBus otherwise. defaultOutputBus
	//itself is never touched by the limiter chain — it stays the user's setting.
	*voiceOutputBus {
		^if(limiterEnabled and: {limiterBus.notNil}) {limiterBus.index} {defaultOutputBus};
	}

	//Master limiter (stereo, ON by default). Voices default-route to a dedicated
	//internal audio bus; the limiter reads it at the tail of the default group and
	//MIXES the limited signal into defaultOutputBus (plain Out — other music on the
	//destination bus is untouched). *limiterOff bypasses the chain: voices then
	//default straight to defaultOutputBus. Explicit out: arguments always bypass.
	//Hooks: bus allocated fresh on every server boot (allocators reset there); the
	//synth re-spawns after every ServerTree rebuild (boot and CmdPeriod).
	*initLimiterHooks {
		if(limiterHooksRegistered) {^this};
		limiterHooksRegistered = true;
		ServerBoot.add({ limiterBus = Bus.audio(Server.default, 2) }, Server.default);
		ServerTree.add({ if(limiterEnabled) { this.prSpawnLimiter } }, Server.default);
	}

	*prSpawnLimiter {
		limiterBus = limiterBus ?? { Bus.audio(Server.default, 2) };
		limiterSynth = Synth(\sslimiter, [in: limiterBus.index, out: defaultOutputBus], Server.default.defaultGroup, \addToTail);
	}

	*limiterOn {
		limiterSynth.free;
		limiterSynth = nil;
		limiterEnabled = true;
		if(Server.default.serverRunning) { this.prSpawnLimiter };
	}

	*limiterOff {
		limiterEnabled = false;
		limiterSynth.free;
		limiterSynth = nil;
	}


	//==============================================================
	//return an array of samplers in the same SamplerDB database
	db{arg samplerName;
		if(samplerName.isNil.not)
		{
			var it = Dictionary.new;
			dbs.do({|samplerDB, index|
				if(samplerDB.samplers.keys.includes(samplerName))
					{
					it = it.put(samplerDB.label, samplerDB.samplers.at(samplerName));
					};
			});

			if (it.isEmpty) {
				Error("This sampler does not exist: " + samplerName).throw;
			};
			^it
		}
		{
			var it = Dictionary.new;
			dbs.do({|samplerDB, index|
				it = it.put(samplerDB.label, samplerDB.samplers);
			});
			if (it.isEmpty) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
	}


	//=============================
	init{arg samplerName, dbname;
		var database;
		dbs = Dictionary.new;

		if(samplerName.isNil){Error("A sampler name is needed").throw;};

		if(SamplerDB.isLoaded(dbname))
		{
			database = SamplerDB.dbs.at(dbname);
		}
		{
			database = SamplerDB.new(dbname);

		};

		name = samplerName.asSymbol;
		database.put(this);
		dbs.put(dbname.asSymbol, database);
		numActiveBuffer = 0;
		averageDuration = 0;
		averageTemporalCentroid = 0;
		allSampler.put(samplerName.asSymbol, this);
	}


	//==============================
	//TODO: Check freeing sampler
	free {
		SamplerDB.dbs[name].removeAt(name);
		samples.do{|thisSample|
			thisSample.free;
		};
		allSampler[name] = nil;
		^super.free;
	}

	//============================
	//load and analyze sound files
	load {arg soundfiles, server = this.class.defaultLoadingServer, filenameAsKeynum = false, normalize = false, startThresh=0.01, endThresh=0.01, override = false, action = nil;
		if(soundfiles.isArray.not){Error("Sound files has to be an array").throw};
		averageMFCC = averageMFCC ? Array.fill(13, 0);
		bufServer = server;
		fork{
			var sample;
			var dict = Dictionary.newFrom([this.filenames, this.samples].flop.flat);
			soundfiles.do{|filename, index|
				if(dict[filename.asSymbol].isNil.not && override.not)
				{
					//"This file is already loaded, reloading".warn;
					//dict[filename.asSymbol].free;
					dict[filename.asSymbol].buffer[0].updateInfo;
					if(dict[filename.asSymbol].buffer[0].numFrames == 0){
						"Can't find Buffer data, reloading....".warn;
						sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, normalize: normalize, server: server, action: action);
						dict.put(filename.asSymbol, sample);
					}
					{
						"This file has already loaded.".warn;
					}
				}
				{//load file
					sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, normalize: normalize, server: server, action: action);
					numActiveBuffer = numActiveBuffer + sample.activeDuration.size;
					averageDuration = averageDuration + sample.activeDuration.sum;
					averageTemporalCentroid = averageTemporalCentroid + sample.temporalCentroid.sum;
					averageMFCC = averageMFCC + sample.mfcc.sum;
					dict.put(filename.asSymbol, sample);
				};
			};

			averageDuration = averageDuration / numActiveBuffer;
			averageTemporalCentroid = averageTemporalCentroid / numActiveBuffer;
			averageMFCC = averageMFCC / numActiveBuffer;
			dict = dict.asSortedArray.flop;
			filenames = dict[0];
			samples = dict[1];

			kdTreeNode = [averageDuration, averageTemporalCentroid, averageMFCC].flat;

			dbs.do{|thisDB| thisDB.makeTree};

			this.setKeyRanges;
			//finalAction.value;
		}
	}


	//=============================================
	//get anchor keynums for the sample library
	keynums{
		var output = [];
		samples.do{|thisSample, index|
			output = output.add(thisSample.keynum);
		};
		^output;
	}


	//TODO: not Working
	//set anchor keynums arbirurarily
	setKeynums{arg keynumArray, resetKeyRanges = [true, 5];
		keynumArray = keynumArray.asArray;
		samples.do{|thisSample, index|
			var thiskeynum = keynumArray[index].asArray;
			thisSample.keynum.do{|thiskey, idx|
				thisSample.keynum[idx] = thiskeynum[idx] ? thisSample.keynum[idx];
			}
		};
		//rebuild the response ranges from the new keynums — once, after all updates,
		//reusing the current strategy (so a \keynumOnly mapping stays \keynumOnly).
		//(The old code passed the radius as setKeyRanges' STRATEGY argument, which
		//matched no switch case, so keyRanges silently never followed setKeynums.)
		if(resetKeyRanges[0]) { this.setKeyRanges(keyRangeStrategy ? \keynumRadious, resetKeyRanges[1]) };
	}


	//=================================================================
	//
	setKeyRanges{arg strategy = \keynumRadious, infoArray = [5];
		keyRanges = keyRanges ? Dictionary.new;
		keyRangeStrategy = strategy.asSymbol;
		switch(strategy.asSymbol,
			\keynumRadious,{//given a range radious from the keynum of each sample sections.
				samples.do{|thisSample, index|
					var radious = infoArray.asArray.wrapAt(index);
					keyRanges = keyRanges.add(thisSample ->  [(thisSample.keynum - radious).thresh(0), thisSample.keynum + radious].flop)
				};
			},
			\fullRange,{//every sample is responded in full range of midi key number.
				samples.do{|thisSample, index|
					var rangeArray = [];
					thisSample.keynum.size.do{
						rangeArray.add([0, 127]);
					};
					keyRanges = keyRanges.add(thisSample -> rangeArray);
				};
			},
			\keynumOnly,{//only respond to the keynum
				samples.do{|thisSample, index|
					var thisKeynum = thisSample.keynum;
					keyRanges = keyRanges.add(thisSample ->  [thisKeynum, thisKeynum].flop)
				};
			};
		)
	}

	setThresh{|startThresh=0.01, endThresh=0.01, loadToBuffer=true|
		var cond = Condition.new;
		Routine{
			samples.do{|sample|
				sample.freeBuffer;
				sample.arEnv(startThresh, endThresh);
				if(loadToBuffer){sample.loadToBuffer(action: {cond.unhang})};
				cond.hang;
			}
		}.play;
	}



	//========================================
	//Play samples by giving key numbers
	//Defaults are also provided by SamplerArguments
	//Negative key numbers reverses the buffer to play.
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.voiceOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples

		if(play){this.playArgs(args)};
		^args;
	}

	setArgs {arg keynums = keynums ? {rrand(10.0, 100.0)}, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.voiceOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		^args;
	}


	//play samples by giving an array of samples to play
	//the members of samplesArray contains two members: a SampleDescript object, and section index
	// etc. [[SampleDescript, 2], [SampleDescript, 0], ......]
	playSample {arg samplesArray, syncmode = \keeplength, detune = 0, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.voiceOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		args.set(syncmode: syncmode, detune: detune, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

	}

	// play a SampleArgument object
	playArgs {|args|
		this.class.playArgs(args);
	}




	//==============================================================
	playEnv {arg env, keynums, dur, amp = 1, pan = 0, maxtexture = 5, ampenv, panenv, bendenv, out = this.class.voiceOutputBus, midiChannel = 0;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		var argslist= SamplerScore.new;

		case
		// sound is short, repeat it to fill up the envelope
		{(this.averageDuration < 0.3) || ((dur ? 1) < 0.2)}
		{
			Routine.run{
				var composite = SSampler.envComposite(env, ampenv);
				var elapsed = 0;
				while({elapsed < env.duration},
					{
						var delayTime = 0.03;
						var texture = env.at(elapsed).linlin(0, env.levels.maxItem, 1, maxtexture).asInteger;
						var thisPan = (panenv !? {SSampler.toFlatEnv(panenv).stretch(env.duration).at(elapsed)}) ? pan;
						var thisBendenv = bendenv !? {SSampler.toFlatEnv(bendenv).stretch(env.duration).subEnv(elapsed, 0.3)};
						var args = this.key(keynums.asArray.choose, \percussive, dur: dur, amp: composite.at(elapsed) * amp, pan: thisPan, bendenv: thisBendenv, texture: texture, out: out, midiChannel: midiChannel);
						elapsed = elapsed + delayTime;
						argslist.add([args, delayTime]);
						delayTime.wait;
					}
				)
			}
		}
		// for sound with longer duration, put it's peak to each peaks of the envelope
		// reverse the sound to fit the envelope if the attack or release is too short
		{true}
		{
			var composite = SSampler.envComposite(env, ampenv);
			//For Each Peak time of the envelop, put a sound peaking at that moment
			env.peakTime.do{|thisPeakTime, index|
				var previousPeakTime = env.peakTime[index - 1] ? 0;
				var nextPeakTime = env.peakTime[index + 1] ? env.duration;
				var attackTime = (thisPeakTime - previousPeakTime).abs;
				var releaseTime = (nextPeakTime - thisPeakTime).abs;
				var thisDur = attackTime + releaseTime;
				var args = SamplerArguments.new;
				var texture, expand, slices;

						//put data into args
				args.set(keynums: playkey.value.asArray, out: out, midiChannel: midiChannel);
				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));


				if(attackTime > args.globalAttackDur){
					if(args.globalAttackDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};
				if(releaseTime > args.globalReleaseDur){
					if(args.globalReleaseDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};

				//if(thisDur > (args.globalDur * 1.6)){expand = thisDur / args.globalDur};

				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));
				texture = env.range.at(thisPeakTime).linlin(0, 1, 1, maxtexture).asInteger;
				slices = SSampler.globalEnvSlices(composite, panenv, bendenv,
					thisPeakTime, args.globalAttackDur ? 0, args.globalReleaseDur ? 0, env.duration);
				args.set(syncmode: [\peakat, thisPeakTime],
					amp: amp,           // 振幅形狀交給 ampenv 切片, 不再用 env.at(peak) 點值
					ampenv: slices[\ampenv], panenv: slices[\panenv], bendenv: slices[\bendenv],
					pan: pan, texture: texture, expand: expand);
				this.playArgs(args);
				argslist.add([args, 0])
			}
		};

		//end-of-envelope cleanup: gate off whatever this call spawned that is
		//still sounding shortly after the envelope ends (0.1s kill-gate fade)
		SystemClock.sched(env.duration + 0.05, {
			SamplerQuery.releaseGesture(SSampler.gestureIDsOf(argslist));
			nil;
		});

		^argslist;
	}
}//end of Sampler class


