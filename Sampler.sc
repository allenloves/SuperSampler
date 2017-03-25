//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, Make sure you have SCMIR installed in your SuperCollider extensions.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
Sampler {
	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	//                           |------ Each Sample -------------|	 |-----Each Sample---
	//                            |--Section---|  |--Section---|      |--Section---|
	var <keyRanges;// in format [[[upper, lower], [upper, lower]..], [[upper, lower], [upper, lower]...], ....]


	*new{arg samplerName, dbname = \default;
		^super.new.init(samplerName, dbname);
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
	load {arg soundfiles, server = Server.default, filenameAsKeynum = true, action = nil;
		bufServer = server;
		fork{
			var dict = Dictionary.newFrom([filenames, samples].flop.flat);
			soundfiles.do{|filename, index|
				var sample;
				if(dict[filename.asSymbol].isNil.not)
				{"This file is already loaded, reloading".postln;
					dict[filename.asSymbol].free;
				};
				sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, server: server, action: action);
				dict.put(filename.asSymbol, sample);
			};
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




	//========================================
	//Play samples by giving key numbers
	//Defaults are also provided by SamplerArguments
	//Negative key numbers reverses the buffer to play.
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = 0, midiChannel = 0;
		var args = SamplerArguments.new;
		var playkey = keynums ? rrand(10.0, 100.0);
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		//args.setSamples(this.getPlaySamples(args));
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples
		args.playSamples = SamplerQuery.getPlayTime(args); // organize play time by peak and stratges

		Routine.run{
			args.playSamples.do{|thisSample, index| //thisSample are realizations of SamplerPrepare class
				var bufRateScale = bufServer.sampleRate / thisSample.sample.sampleRate;
				var buf = thisSample.buffer;
				var duration = args.dur ? ((thisSample.sample.activeDuration[thisSample.section]) / thisSample.rate.abs) * bufRateScale; // * (args.expand ? 1)
				var synthID = UniqueID.next.asSymbol;

				thisSample.wait.wait;
				case
				{thisSample.expand.isNumber}{
					case
					{buf.size == 2}{
						SamplerQuery.playing[midiChannel].put(synthID, Synth(\ssexpand2, [buf0: buf[0], buf1: buf[1], expand: thisSample.expand, dur: duration + 0.02, rate: thisSample.rate, startPos: thisSample.position, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bendenv: thisSample.bendenv, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]).onFree{SamplerQuery.playing[midiChannel].removeAt(synthID)});
					}
					{true}{
						SamplerQuery.playing[midiChannel].put(synthID, Synth(\ssexpand1, [buf: buf[0], expand: thisSample.expand, dur: duration + 0.02, rate: thisSample.rate, startPos: thisSample.position, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bendenv: thisSample.bendenv, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]).onFree{SamplerQuery.playing[midiChannel].removeAt(synthID)});
					};
				}
				{true}{
					case
					{buf.size == 2}{
						SamplerQuery.playing[midiChannel].put(synthID, Synth(\ssplaybuf2, [buf0: buf[0], buf1: buf[1], rate: thisSample.rate, startPos: thisSample.position, dur: duration, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bendenv: thisSample.bendenv, out: args.out]).onFree{SamplerQuery.playing[midiChannel].removeAt(synthID)});
					}
					{true}{
						SamplerQuery.playing[midiChannel].put(synthID, Synth(\ssplaybuf1, [buf: buf[0], rate: thisSample.rate, startPos: thisSample.position, dur: duration, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bendenv: thisSample.bendenv, out: args.out]).onFree{SamplerQuery.playing[midiChannel].removeAt(synthID)});
					};
				};

			};
		}
	}




	//==============================================================
	//TODO: Play a sample with the influence of a global envelope
	playEnv{arg keynums, env, syncmode = \percussive;

		Routine.run{
			var elapsed = 0;
			while({elapsed < env.duration},
				{
					var delayTime = 0.02;
					this.key(keynums.asArray.choose, syncmode, amp: env.at(elapsed));
					elapsed = elapsed + delayTime;
					delayTime.wait;
				}
			)
		}
	}
}//end of Sampler class


