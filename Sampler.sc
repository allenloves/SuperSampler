//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, Make sure you have SCMIR installed in your SuperCollider extensions.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
Sampler {

	classvar < allSampler;
	classvar <> defaultTexture;
	classvar <> defaultOutputBus = 0;
	classvar <> defaultLoadingServer;

	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
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
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples

		if(play){this.playArgs(args)};
		^args;
	}


	setArgs {arg keynums = keynums ? {rrand(10.0, 100.0)}, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0).postln};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		^args;
	}


	//play samples by giving an array of samples to play
	//the members of samplesArray contains two members: a SampleDescript object, and section index
	// etc. [[SampleDescript, 2], [SampleDescript, 0], ......]
	playSample {arg samplesArray, syncmode = \keeplength, detune = 0, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		args.set(syncmode: syncmode, detune: detune, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

	}


	playArgs {|args|
		this.class.playArgs(args);
	}




	//==============================================================
	//TODO: Play a sample with the influence of a global envelope
	playEnv {arg env, keynums, dur, amp = 1, pan = 0, maxtexture = 5, out = this.class.defaultOutputBus, midiChannel = 0;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		var argslist= [];

		case
		// sound is short, repeat it to fill up the envelope
		{(this.averageDuration < 0.3) || ((dur ? 1) < 0.2)}
		{
			Routine.run{
				var elapsed = 0;
				while({elapsed < env.duration},
					{
						var delayTime = 0.02;
						var texture = env.at(elapsed).linlin(0, env.levels.maxItem, 1, maxtexture).asInteger;
						//args.set(syncmode: \percussive, amp: env.at(elapsed), texture: texture);
						//this.playArgs(args);
						this.key(keynums.asArray.choose, \percussive, dur: min((dur ? 1), 0.2), amp: env.at(elapsed) * amp, pan: pan, texture: texture, out: out, midiChannel: midiChannel);
						elapsed = elapsed + delayTime;
						delayTime.wait;
					}
				)
			}
		}
		// for sound with longer duration, put it's peak to each peaks of the envelope
		// reverse the sound to fit the envelope if the attack or release is too short
		{true}
		{
			//For Each Peak time of the envelop, put a sound peaking at that moment
			env.peakTime.do{|thisPeakTime, index|
				var previousPeakTime = env.peakTime[index - 1] ? 0;
				var nextPeakTime = env.peakTime[index + 1] ? env.duration;
				var attackTime = (thisPeakTime - previousPeakTime).abs;
				var releaseTime = (nextPeakTime - thisPeakTime).abs;
				var thisDur = attackTime + releaseTime;
				var args = SamplerArguments.new;
				var ampenv, envStartTime, maxTexture, texture, expand;

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
				envStartTime = (thisPeakTime-args.globalAttackDur).thresh(0);
				ampenv = env.subEnv(envStartTime, min(args.globalDur, env.duration - thisPeakTime + envStartTime));
				//args.set(syncmode: [\peakat, thisPeakTime], amp: amp, ampenv: ampenv, pan: pan, texture: texture);
				args.set(syncmode: [\peakat, thisPeakTime], amp: env.at(thisPeakTime) * amp, pan: pan, texture: texture, expand: expand);

				this.playArgs(args);
			}
		};



	}
}//end of Sampler class


