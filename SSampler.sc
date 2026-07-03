//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, it is now included in SuperSampler boundle.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
SSampler {

	classvar < allSampler;
	classvar <> defaultTexture;
	classvar <> defaultOutputBus = 0;
	classvar <> defaultLoadingServer;
	classvar <> headroomRef = 0.7;
	classvar <> headroomTarget = 0.9;
	classvar limiterSynth, <limiterEnabled = false, limiterBus = 0;
	classvar limiterTreeRegistered = false;

	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	var <> normalize = true;  //when true, getSamplesByKeynum stamps each SamplerPrepare's normGain to align sample peaks to headroomRef
	//                                                                |- section -|   |- section -|
	var <keyRanges;// Is a dictionary in format  (SampleDescrtipt -> [[lower, upper], [lower, upper],..], ..)

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

	//Slice the global (env.duration-domain) contours down to one gesture's wall
	//window [0, gestureDur]. Gestures all spawn at t = 0, so no offset is needed.
	*globalEnvSlices {|composite, panenv, bendenv, gestureDur, envDur|
		var slice = {|e| e !? {SSampler.toFlatEnv(e).stretch(envDur).subEnv(0, gestureDur)} };
		^(
			ampenv: slice.(composite),
			panenv: slice.(panenv),
			bendenv: slice.(bendenv)
		);
	}

	// args is a realization of SamplerArguments
	*playArgs{|args|
		args.playSamples = SamplerQuery.getPlayTime(args); // organize play time by peak and stratges

		//predictive gain management: keep the summed level at this gesture's peak
		//moment within headroomTarget (only scales the NEW gesture -- running
		//voices have their amp baked at EnvGen init and cannot be rescaled)
		if(args.autoGain == true) {
			var tAbs = thisThread.seconds + SamplerQuery.predictedPeakOffset(args);
			var lOld = SamplerQuery.predictedLevelAt(tAbs);
			var perVoicePeak = args.playSamples.collect{|p| args.amp * (p.normGain ? 1) * ((p.sample.peakAmp ? #[1])[p.section] ? 1)}.maxItem ? 0;
			var lNew = args.playSamples.size.sqrt * perVoicePeak;
			args.amp = args.amp * SamplerQuery.autoGainScale(lOld, lNew);
		};

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

	//Safety limiter at the tail of the default group (stereo, default OFF).
	//Re-spawns itself after every ServerTree rebuild (e.g. Server.default.reboot / CmdPeriod
	//in some workflows) as long as limiterEnabled is still true; the ServerTree action itself
	//is only ever registered once, guarded by limiterTreeRegistered.
	*limiterOn {|out = 0|
		this.limiterOff;
		limiterBus = out;
		limiterEnabled = true;
		limiterSynth = Synth(\sslimiter, [out: limiterBus], Server.default.defaultGroup, \addToTail);
		if(limiterTreeRegistered.not) {
			ServerTree.add({ if(limiterEnabled) {
				limiterSynth = Synth(\sslimiter, [out: limiterBus], Server.default.defaultGroup, \addToTail);
			}}, Server.default);
			limiterTreeRegistered = true;
		};
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
				if(resetKeyRanges[0]){this.setKeyRanges(resetKeyRanges[1])};
			}
		}
	}


	//=================================================================
	//
	setKeyRanges{arg strategy = \keynumRadious, infoArray = [5];
		keyRanges = keyRanges ? Dictionary.new;
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
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.autoGain = normalize;
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples

		if(play){this.playArgs(args)};
		^args;
	}

	setArgs {arg keynums = keynums ? {rrand(10.0, 100.0)}, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.autoGain = normalize;
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		^args;
	}


	//play samples by giving an array of samples to play
	//the members of samplesArray contains two members: a SampleDescript object, and section index
	// etc. [[SampleDescript, 2], [SampleDescript, 0], ......]
	playSample {arg samplesArray, syncmode = \keeplength, detune = 0, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		args.autoGain = normalize;
		args.set(syncmode: syncmode, detune: detune, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

	}

	// play a SampleArgument object
	playArgs {|args|
		this.class.playArgs(args);
	}




	//==============================================================
	playEnv {arg env, keynums, dur, amp = 1, pan = 0, maxtexture = 5, ampenv, panenv, bendenv, out = this.class.defaultOutputBus, midiChannel = 0;
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

				args.autoGain = normalize;
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
					min(args.globalDur ? env.duration, env.duration), env.duration);
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


