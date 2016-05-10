//Sampler is dependent on following extentions:
//SCMIR
//VKey
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
Sampler{
	var <dbs;  // a SamplerDB instance.
	var <dbName;  //name of the SamplerDB class this sample stored into
	var <samplerName;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	//                           |------ Each Sample -------------|	 |-----Each Sample---
	//                            |--Section---|  |--Section---|      |--Section---|
	var <keyRanges;// in format [[[upper, lower], [upper, lower]..], [[upper, lower], [upper, lower]...], ....]


	*new{arg name, dbname = \default;
		^super.new.init(name, dbname);
	}


	//==============================================================
	//return an array of samplers in the same SamplerDB database
	db{arg name;
		if(name.isNil.not)
		{
			var it = dbs.samplers.at(name);
			if (it.isNil) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
		{
			var it = dbs.samplers;
			if (it.isNil) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
	}


	//=============================
	init{arg name, dbname;
		dbName = dbname;
		if(name.isNil){Error("A sampler name is needed").throw;};
		if(SamplerDB.isLoaded(dbname))
		{
			dbs = SamplerDB.dbs.at(dbname);
		}
		{
			dbs = SamplerDB.new(dbname);

		};
		dbs.put(name.asSymbol, this);
		samplerName = name.asSymbol;
	}


	//==============================
	//TODO: Check freeing sampler
	free {
		SamplerDB.dbs[dbName].removeAt(samplerName);
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
	load {arg soundfiles, server = Server.default, filenameAsKeynum = true;
		bufServer = server;
		Routine.run{
			var dict = Dictionary.newFrom([filenames, samples].flop.flat);
			soundfiles.do{|filename, index|
				var sample;
				if(dict[filename.asSymbol].isNil.not)
				{"This file is already loaded, reloading".postln;
					dict[filename.asSymbol].free;
				};
				sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum);
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


	//======================================================================================================
	//provide a list of SampleDescripts and it's keynum of each active buffer inside the key range of provided keynum.
	//for each qualified buffers, ourput a bufData array of [SampleDescript, section index, playRate]
	//Out put will be [[bufferData1], [bufferData2], .....]
	getPlaySamples{|args filterFunc = true|
		var keyNums = args.keynums;
		var texture = args.texture;
		var finalList = [];

		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			var keySign = keyNum.sign;
			keyNum = keyNum.abs;

			//find keyNums in the keyRanges of each sample sections, send the sample section information
			keyRanges.do{|thisSample, index|
				thisSample.do{|thisSection, idx|
					var playRate;
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						playRate = 2**((keyNum - samples[index].keynum[idx])/12);
						sampleList = sampleList.add([samples[index], idx, playRate * keySign])};
				}
			};

			//When nothing is found in the keyRange, find the closest keynum to be the buffer.
			if(sampleList.isEmpty)
			{
				var sortIndexes = Dictionary.new;
				var closestSample, closestSection, playRate;
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
				closestSample = samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				closestSection = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				playRate = 2**((keyNum - closestSample.keynum[closestSection]) / 12);

				sampleList = sampleList.add([closestSample, closestSection, playRate * keySign])
			};

			//reduce samples by texture value, based on the distance of key Numbers.
			//Sample pitch closer to the key number gets picked first.
			sampleList = sampleList.sort({|a,b| (a[0].keynum[a[1]]-keyNum).abs < (b[0].keynum[b[1]]-keyNum).abs})[0..(texture !? {texture-1})];

			finalList = finalList ++ sampleList;
		};

		finalList = finalList.scramble[0..(texture !? {texture-1})];
		args.playSamples = finalList;
		^finalList;  //[SampleDescript, section index, playRate]
	}





	//====================================================
	//calculate starting time for each sample in a group
	//The list from getPlaySamples sends to here.
	//the structure of sampleList is
	//[SampleDescript, section index, playRate]
	getPlayTime{arg args;
		var playSamples = args.playSamples;
		var syncmode = args.syncmode;

		switch(syncmode.asArray[0].asSymbol,
			//keep the full length to samples, line up the peak time together
			//Not working correctly.
			\keeplength,{
				var waittime = 0, startpos = 0;

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a[2].isPositive) {a[0].attackDur[a[1]]} {a[0].releaseDur[a[1]]};
					var bAttack = if(b[2].isPositive) {b[0].attackDur[b[1]]} {b[0].releaseDur[b[1]]};
					(aAttack / a[2].abs) > (bAttack / b[2].abs)
				});

				playSamples.do{|thisSample, index|
					var previousIndex = (index - 1).thresh(0);
					var previousSample = playSamples[previousIndex];
					var thisPeakTime, previousPeakTime;
					thisPeakTime = if(thisSample[2].isPositive)
					{thisSample[0].attackDur[thisSample[1]] / thisSample[2].abs}
					{thisSample[0].releaseDur[thisSample[1]] / thisSample[2].abs};

					previousPeakTime = if(previousSample[2].isPositive)
					{previousSample[0].attackDur[previousSample[1]] / previousSample[2].abs}
					{previousSample[0].releaseDur[previousSample[1]] / previousSample[2].abs};


					//("thisPeakTime =" + thisPeakTime).postln;
					waittime = (previousPeakTime - thisPeakTime).thresh(0);

					startpos = if(thisSample[2].isNegative){thisSample[0].activeBuffer[thisSample[1]].duration};
					playSamples[index] = playSamples[index] ++ waittime ++ startpos;
				}
			},

/*
			//Replaced by peakat
			\startat,{
				var startPoint = syncmode.asArray[1] ? 0;
				var startpos = 0, waittime = 0;
				var previousPeakTime;

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a[2].isPositive) {a[0].attackDur[a[1]]} {a[0].releaseDur[a[1]]};
					var bAttack = if(b[2].isPositive) {b[0].attackDur[b[1]]} {b[0].releaseDur[b[1]]};
					(aAttack / a[2].abs) > (bAttack / b[2].abs)
				});

				//initial peak time with the first sample
				previousPeakTime = ((playSamples[0][0].attackDur[playSamples[0][1]] / playSamples[0][2]) - (startPoint * playSamples[0][2])).thresh(0);

				playSamples.do{|thisSample, index|
					var thisPeakTime = (thisSample[0].attackDur[thisSample[1]] / thisSample[2]);
					var adjust = previousPeakTime - thisPeakTime;

					waittime = adjust.thresh(0);
					startpos = adjust.neg.thresh(0) * thisSample[2];

					thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);

					//("thisPeakTime =" + thisPeakTime).postln;
					playSamples[index] = playSamples[index] ++ waittime ++ startpos;
					previousPeakTime = thisPeakTime;

				}
			},
*/


			//assign a peak time where the pick of sound gesture happens
			\peakat,{
				var startpos = 0, waittime = 0;
				var previousPeakTime = syncmode.asArray[1] ? 0; //initial peak time

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a[2].isPositive) {a[0].attackDur[a[1]]} {a[0].releaseDur[a[1]]};
					var bAttack = if(b[2].isPositive) {b[0].attackDur[b[1]]} {b[0].releaseDur[b[1]]};
					(aAttack / a[2].abs) > (bAttack / b[2].abs)
				});


				playSamples.do{|thisSample, index|
					var thisPeakTime, adjust;
					thisPeakTime = if(thisSample[2].isPositive)
					{thisSample[0].attackDur[thisSample[1]] / thisSample[2].abs}
					{thisSample[0].releaseDur[thisSample[1]] / thisSample[2].abs};
					adjust = previousPeakTime - thisPeakTime;

					waittime = adjust.thresh(0);


					startpos = if(thisSample[2].isPositive)
					{adjust.neg.thresh(0) * thisSample[2].abs}
					{thisSample[0].activeBuffer[thisSample[1]].duration - (adjust.neg.thresh(0) * thisSample[2].abs)};

					thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);
					//("thisPeakTime =" + thisPeakTime).postln;
					playSamples[index] = playSamples[index] ++ waittime ++ startpos;

					previousPeakTime = thisPeakTime;
				}

			},

			//cut the beginning of sample file, start from the peak point
			\percussive,{
				var startpos = 0, waittime = 0;
				playSamples.do{|thisSample, index|
					var thisPeakTime, startpos;

					thisPeakTime = thisSample[0].attackDur[thisSample[1]];
					startpos = (thisPeakTime-0.01).thresh(0);
					playSamples[index] = playSamples[index] ++ waittime ++ startpos;
				}
			},

			//conventional sample playing
			\nosorting,{
				var startpos = 0, waittime = 0;
				playSamples.do{|thisSample, index|
					startpos = if(thisSample[2].isNegative){thisSample[0].activeBuffer[thisSample[1]].duration};
					playSamples[index] = playSamples[index] ++ waittime ++ startpos;
				}
			}
		);
		args.playBoundles = playSamples;
		^playSamples;
	}



	//========================================
	//Play samples by giving key numbers
	//Defaults are also provided by SamplerArguments
	//Negative key numbers reverses the buffer to play.
	key{arg keynums = 60, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bend = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15;
		var args = SamplerArguments.new;
		var playSamples;

		args.set(keynums: keynums, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bend: bend, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur);
		this.getPlaySamples(args);
		this.getPlayTime(args);


		//for Each Sample, the dataStructure is
		//[SampleDescript, section index, play rate, wait time, start position]
		Routine.run{
			args.playBoundles.do{|thisSample, index|
				var bufRateScale = bufServer.sampleRate / thisSample[0].sampleRate;
				var buf = thisSample[0].activeBuffer[thisSample[1]];
				var duration = args.dur ? ((thisSample[0].activeDuration[thisSample[1]]) / thisSample[2].abs) * bufRateScale * (args.expand ? 1);
				//("wait" + thisSample[3] + "seconds").postln;
				//thisSample.postln;
				thisSample[3].wait;

				//[buf, thisSample[2], thisSample[4], dur + 0.02, amp, pan].postln;
				//[thisSample[0], thisSample[1], thisSample[2], thisSample[3], thisSample[4]].postln;

				case
				{args.expand.isNumber}{Synth(\expand, [buf: buf, expand: args.expand, dur: duration + 0.02, rate: thisSample[2], startPos: thisSample[4], amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: args.bend, grainRate: args.grainRate, grainDur: args.grainDur]);}

				{true}{Synth(\playbuf, [buf: buf, rate: thisSample[2], startPos: thisSample[4], dur: duration + 0.02, amp: args.amp, ampenv: args.ampenv, pan: args.pan, panenv: args.panenv, bend: args.bend]);};

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


