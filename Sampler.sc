//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, Make sure you have SCMIR installed in your SuperCollider extensions.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
Sampler {

	classvar <> defaultTexture;
	classvar <> defaultOutputBus = 0;

	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	//                           |------ Each Sample -------------|	 |-----Each Sample---
	//                            |--Section---|  |--Section---|      |--Section---|
	var <keyRanges;// in format [[[upper, lower], [upper, lower]..], [[upper, lower], [upper, lower]...], ....]

	//Sampler metadata
	var <numActiveBuffer;
	var <averageDuration;
	var <averageTemporalCentroid;
	var <averageMFCC;


	*new{arg samplerName, dbname = \default;
		^super.new.init(samplerName, dbname);
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
		database.put(samplerName.asSymbol, this);
		dbs.put(dbname.asSymbol, database);
		name = samplerName.asSymbol;
		numActiveBuffer = 0;
		averageDuration = 0;
		averageTemporalCentroid = 0;
	}


	//==============================
	//TODO: Check freeing sampler
	free {
		SamplerDB.dbs[name].removeAt(name);
		samples.do{|thisSample|
			thisSample.free;
		};
		samples = [];
		filenames = [];
		bufServer = nil;
		keyRanges = [];

	}

	//============================
	//load and analyze sound files
	load {arg soundfiles, server = Server.default, filenameAsKeynum = false, normalize = false, startThresh=0.01, endThresh=0.01, action = nil;
		averageMFCC = averageMFCC ? Array.fill(13, 0);
		if(soundfiles.isArray.not){Error("Sound files has to be an array").throw};
		bufServer = server;
		fork{
			var sample;
			var dict = Dictionary.newFrom([filenames, samples].flop.flat);
			soundfiles.do{|filename, index|
				if(dict[filename.asSymbol].isNil.not)
				{"This file is already loaded, reloading".postln;
					dict[filename.asSymbol].free;
				};
				sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, normalize: normalize, server: server, action: action);
				numActiveBuffer = numActiveBuffer + sample.activeDuration.size;
				averageDuration = averageDuration + sample.activeDuration.sum;
				averageTemporalCentroid = averageTemporalCentroid + sample.temporalCentroid.sum;
				averageMFCC = averageMFCC + sample.mfcc.sum;
				dict.put(filename.asSymbol, sample);
			};

			averageDuration = averageDuration / numActiveBuffer;
			averageTemporalCentroid = averageTemporalCentroid / numActiveBuffer;
			averageMFCC = averageMFCC / numActiveBuffer;
			dict = dict.asSortedArray.flop;
			filenames = dict[0];
			samples = dict[1];

			dbs.do{|thisDB| thisDB.makeTree};

			this.setKeyRanges;
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
		keyRanges = [];
		switch(strategy.asSymbol,
			\keynumRadious,{//given a range radious from the keynum of each sample sections.
				samples.do{|thisSample, index|
					var radious = infoArray.asArray.wrapAt(index);
					keyRanges = keyRanges.add([(thisSample.keynum - radious).thresh(0), thisSample.keynum + radious].flop)
				};
			},
			\fullRange,{//every sample is responded in full range of midi key number.
				samples.do{|thisSample, index|
					var sectionRanges = [];
					thisSample.keynum.do{|thisSection, idx|
						sectionRanges = sectionRanges.add([0, 127]);
					};
					keyRanges = keyRanges.add(sectionRanges);
				}
			},
			\keynumOnly,{//only respond to the keynum
				samples.do{|thisSample, index|
					var thisKeynum = thisSample.keynum;
					keyRanges = keyRanges.add([thisKeynum, thisKeynum].flop)
				}
			}
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
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples

		if(play){this.playArgs(args)};
		^args;
	}


	//play samples by giving an array of samples to play
	//the members of samplesArray contains two members: a SampleDescript object, and section index
	// etc. [[SampleDescript, 2], [SampleDescript, 0], ......]
	playSample {arg samplesArray, syncmode = \keeplength, detune = 0, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
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

		case
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
						this.key(keynums.asArray.choose, \percussive, dur: dur, amp: env.at(elapsed) * amp, pan: pan, texture: texture, out: out, midiChannel: midiChannel);
						elapsed = elapsed + delayTime;
						delayTime.wait;
					}
				)
			}
		}
		{true}
		{
			//For Each Peak time of the envelop, put a sound peaking at that moment
			env.peakTime.do{|thisPeakTime, index|
				var previousPeakTime = env.peakTime[index - 1] ? 0;
				var nextPeakTime = env.peakTime[index + 1] ? env.duration;
				var attackTime = (thisPeakTime - previousPeakTime).abs;
				var releaseTime = (nextPeakTime - thisPeakTime).abs;
				var args = SamplerArguments.new;
				var maxTexture, texture;

				//put data into args
				args.set(keynums: playkey.value.asArray, out: out, midiChannel: midiChannel);
				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));


				if(attackTime > args.globalAttackDur){
					if(args.globalAttackDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};
				if(releaseTime > args.globalReleaseDur){
					if(args.globalReleaseDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};
				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));
				texture = env.range.at(thisPeakTime).linlin(0, 1, 1, maxtexture).asInteger;
				args.set(syncmode: [\peakat, thisPeakTime], amp: env.at(thisPeakTime) * amp, pan: pan, texture: texture);
				this.playArgs(args);
			}
		};



	}
}//end of Sampler class


