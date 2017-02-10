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

	/*
	//TODO: not Working
	//set anchor keynums arbirurarily
	setKeynums{|keynumArray, resetKeyRanges = [true, 5]|
		keynumArray = keynumArray.asArray;
		samples.do{|thisSample, index|
			thisSample.keynum = keynumArray[index] ? thisSample.keynum;
			if(resetKeyRanges[0]){setKeyRanges(resetKeyRanges[1])};
			[thisSample.filename, thisSample.keynum].postln;
		}
	}
	*/


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




getPlaySamples{|args, filterFunc = true|
		var keyNums = args.keynums;
		var texture = args.texture;
		var finalList = [];

		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			var keySign = keyNum.sign;
			var samplePrep = SamplerPrepare.new;
			keyNum = keyNum.abs;

			//find keyNums in the keyRanges of each sample sections, send the sample section information
			keyRanges.do{|thisSample, index|
				thisSample.do{|thisSection, idx|
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						samplePrep.sample = samples[index];
						samplePrep.samplerName = name;
						samplePrep.setRate(2**((keyNum - samples[index].keynum[idx])/12) * keySign);
						samplePrep.section = idx;
						sampleList = sampleList.add(samplePrep)};
				}
			};

			//When nothing is found in the keyRange, find the closest keynum to be the buffer.
			if(sampleList.isEmpty)
			{
				var sortIndexes = Dictionary.new;
				samples.do{|thisSample, index|
					thisSample.keynum.do{|thisKeynum, idx|
						//address arrays in the form of [Which sample, Which section]
						sortIndexes.put(thisKeynum, [index, idx]);
					}
				};

				sortIndexes = sortIndexes.asSortedArray.flop;
				// sortIndexes[0]==keynums in sorted order
				// sortIndexes[1]==Index arrays in sorted order
				// address for the clost keynum will be:
				//sortIndexes[1][sortIndexes[0].indexIn(keyNum)]
				samplePrep.sample = samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				samplePrep.samplerName = name;
				samplePrep.section = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				samplePrep.setRate(2**((keyNum - samplePrep.sample.keynum[samplePrep.section]) / 12) * keySign);

				sampleList = sampleList.add(samplePrep)
			};

			//reduce samples by texture value, based on the distance of key Numbers.
			//Sample pitch closer to the key number gets picked first.
			sampleList = sampleList.sort({|a,b| (a.sample.keynum[a.section]-keyNum).abs < (b.sample.keynum[b.section]-keyNum).abs})[0..(texture !? {texture-1})];

			finalList = finalList ++ sampleList;
		};

		finalList = finalList.scramble[0..(texture !? {texture-1})];

		//args.setSamples(finalList); //send the list to SamplerArguments class and get global duration

		^finalList;  //list of SamplePrepare class
	}



	//====================================================
	//calculate starting time for each sample in a group
	getPlayTime {arg args;  //args is a SamplerArguments class
		var playSamples = args.playSamples;
		var syncmode = args.syncmode;
		var globalDur = args.globalDur;

		switch(syncmode.asArray[0].asSymbol,


			//keep the full length to samples, line up the peak time together
			\keeplength,{
				var waittime = 0, startpos = 0, elapsed = 0;

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});

				//thisSample is a SamplePrepare object
				playSamples.do{|thisSample, index|
					var previousIndex = (index - 1).thresh(0);
					var previousSample = playSamples[previousIndex];
					var thisPeakTime, previousPeakTime;

					thisPeakTime = if(thisSample.rate.isPositive)
					{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
					{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

					previousPeakTime = if(previousSample.rate.isPositive)
					{previousSample.sample.attackDur[previousSample.section] / previousSample.rate.abs}
					{previousSample.sample.releaseDur[previousSample.section] / previousSample.rate.abs};

					waittime = (previousPeakTime - thisPeakTime).thresh(0);  //wait time before pitch bend.

					thisSample.wait = waittime;
					thisSample.position = if(thisSample.rate.isNegative){thisSample.sample.activeBuffer[thisSample.section][0].duration}{startpos};
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;


					//pitch bend adjustments
					if(args.bend != #[1, 1, -99, -99, 1, 1, 1, 0]){
						var nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
						var nWait = waittime / globalDur;
						var nDur = thisSample.duration / globalDur;
						var rBendEnv = args.bend.asEnv.reciprocal;

						//wait time equals to the integral of reciprocal bend envelope
						thisSample.wait = (rBendEnv.integral(nElapsed + nWait) - rBendEnv.integral(nElapsed)) * rBendEnv.copy.duration_(globalDur).integral;
						//local bend envelope for each sound
						thisSample.bend = args.bend.asEnv.subEnv(nElapsed + nWait, nDur).asArray;

						elapsed = elapsed + thisSample.wait;
					};
					{elapsed = elapsed + waittime;}
				}
			},


			//assign a peak time where the pick of sound gesture happens
			\peakat,{
				var startpos = 0, waittime = 0;
				var previousPeakTime = syncmode.asArray[1] ? 0; //initial peak time

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});



				playSamples.do{|thisSample, index|
					var thisPeakTime, adjust;
					thisPeakTime = if(thisSample.rate.isPositive)
					{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
					{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};
					adjust = previousPeakTime - thisPeakTime;

					thisSample.wait = adjust.thresh(0);
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;
					thisSample.position = if(thisSample.rate.isPositive){
						adjust.neg.thresh(0) * thisSample.rate.abs
					}
					{
						thisSample.sample.activeBuffer[thisSample.section][0].duration - (adjust.neg.thresh(0) * thisSample.rate.abs);
					};

					thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);
					previousPeakTime = thisPeakTime;
				}

			},

			//cut the beginning of sample file, start from the peak point
			\percussive,{
				var startpos = 0, waittime = 0;
				playSamples.do{|thisSample, index|
					var thisPeakTime, startpos;
					thisPeakTime = thisSample.sample.attackDur[thisSample.section];

					thisSample.position = (thisPeakTime-0.01).thresh(0);
					thisSample.wait = waittime;
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;
				}
			},

			//conventional sample playing
			\nosorting,{
				var startpos = 0, waittime = 0;
				playSamples.do{|thisSample, index|
					thisSample.position = if(thisSample.rate.isNegative){thisSample.sample.activeBuffer[thisSample.section][0].duration}{startpos};
					thisSample.wait = 0;
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;
				}
			},

			//expand shorter sample to fit the largest sample
			\stratchshort,{
				var globalAttackDur = playSamples.collect({|thisSample, index| thisSample.sample.attackDur[thisSample.section]}).maxItem;
				playSamples.do{|thisSample, index|
					var stratch = globalAttackDur / (thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs);
					thisSample.wait = 0;
					thisSample.position = 0;
					if(stratch != 1){thisSample.expand = stratch * (args.expand ? 1)}{thisSample.expand = args.expand;};
					thisSample.bend = args.bend;
				}
			}
		);
		//args.playSamples = playSamples;
		^playSamples;
	}



	//========================================
	//Play samples by giving key numbers
	//Defaults are also provided by SamplerArguments
	//Negative key numbers reverses the buffer to play.
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bend = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = 0;
		var args = SamplerArguments.new;
		var playkey = keynums ? rrand(10.0, 100.0);
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bend: bend, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out);
		args.setSamples(this.getPlaySamples(args));
		args.playSamples = this.getPlayTime(args);

		Routine.run{
			args.playSamples.do{|thisSample, index| //thisSample are realizations of SamplerPrepare class
				var bufRateScale = bufServer.sampleRate / thisSample.sample.sampleRate;
				var buf = thisSample.sample.activeBuffer[thisSample.section];
				var duration = args.dur ? ((thisSample.sample.activeDuration[thisSample.section]) / thisSample.rate.abs) * bufRateScale; // * (args.expand ? 1)

				thisSample.wait.wait;
				case
				{thisSample.expand.isNumber}{
					case
					{buf.size == 2}{
						Synth(\ssexpand2, [buf0: buf[0], buf1: buf[1], expand: thisSample.expand, dur: duration + 0.02, rate: thisSample.rate, startPos: thisSample.position, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: thisSample.bend, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]);
					}
					{true}{
						Synth(\ssexpand1, [buf: buf[0], expand: thisSample.expand, dur: duration + 0.02, rate: thisSample.rate, startPos: thisSample.position, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: thisSample.bend, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]);
					};
				}
				{true}{
					case
					{buf.size == 2}{
						Synth(\ssplaybuf2, [buf0: buf[0], buf1: buf[1], rate: thisSample.rate, startPos: thisSample.position, dur: duration, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: thisSample.bend, out: args.out]);
					}
					{true}{
						Synth(\ssplaybuf1, [buf: buf[0], rate: thisSample.rate, startPos: thisSample.position, dur: duration, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: thisSample.bend, out: args.out]);
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
					var delayTime = 0.05;
					this.key(keynums, syncmode, amp: env.at(elapsed));
					elapsed = elapsed + delayTime;
					delayTime.wait;
				}
			)
		}
	}
}//end of Sampler class


