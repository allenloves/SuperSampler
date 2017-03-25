
//Instance of SamplerDB is a database of multiple Sampler
//The data structure of SamplerDB Instance is a Dictionary in this format
// [ \Sampler_Name -> Sampler_Instance,  \Sampler_Name -> Sampler_Instance,  ...]
SamplerDB{
	classvar < dbs;  //System log for all sampler databases.
	var < label;     //name of this SamplerDB instance
	var < samplers;  //a Dictionary with the database of Samplers (name -> Sampler)

	//Corpus:
	//Data stores in the form of KDTree
	var < shapeTree = nil;
	var < mfccTree = nil;


	*new{arg dbname = \default;
		^super.new.init(dbname);
	}

	*initClass {
		dbs = Dictionary.new;
	}

	*isLoaded {arg dbname;
		^dbs.at(dbname.asSymbol).isNil.not;
	}

	*free {
		dbs = nil;
	}

	init {arg dbname;
		label = dbname;
		if(this.class.dbs.at(label).isNil.not)
		{
			"Overwritting existing Sampler database".warn;
			this.class.dbs.at(label).free;
			this.class.dbs.put(label, this)
		}
		{this.class.dbs.put(label, this)};

		samplers = Dictionary.new;
	}


	remove {arg sampler;
		if(sampler.isSymbol)
		{samplers.removeAt(sampler)}
		{samplers.removeAt(sampler.name)};
		^samplers;
	}


	//Register a Sampler to SamplerDB
	put {arg name, sampler;
		samplers.put(name.asSymbol, sampler);
		^samplers;
	}

	add {arg name, sampler;
		samplers.put(name.asSymbol, sampler);
		^samplers;
	}

	makeTree {
		var shapeTreePrep;
		var mfccTreePrep;

		if(shapeTree.isNil)
		{shapeTreePrep = []}
		{shapeTreePrep = shapeTree.asArray(incLabels: true)};

		if(mfccTree.isNil)
		{mfccTreePrep = []}
		{mfccTreePrep = mfccTree.asArray(incLabels: true)};

		samplers.do{|sampler|
			sampler.samples.do{|thisSample, index|
				thisSample.activeDuration.do{|thisDur, idx|
					var thisTemporalCentroid = thisSample.temporalCentroid[idx];
					var thisAttackDur = thisSample.attackDur[idx];
					var thisMFCC = thisSample.mfcc[idx];
					shapeTreePrep = shapeTreePrep.add([thisDur, thisTemporalCentroid, thisAttackDur, [thisSample, idx]]);
					mfccTreePrep = mfccTreePrep.add(thisMFCC.add([thisSample, idx]));
				};
			}
		};

		shapeTree = KDTree.new(shapeTreePrep, lastIsLabel: true);
		mfccTree = KDTree.new(mfccTreePrep, lastIsLabel: true);
	}


	//TODO: Free all Samplers in the database.
	free {arg freeSamplers = true;
		dbs.removeAt(label);
		if(freeSamplers){samplers.do{|thisSampler| thisSampler.free;}};
		samplers = nil;
	}

/*
	//testrun
	playEnv {arg keynums, env;

		samplers.do{|thisSampler, samplerIndex|
			if(thisSampler.samples[0].temporalCentroid[0] < 0.15)
			{thisSampler.playEnv(keynums, env)}
			{
				env.peakTime.do{|thisPeakTime|
						var args = SamplerArguments.new.set(keynums: keynums.asArray.choose);
						var maxTexture = thisSampler.getPlaySamples(args).size;
						var texture = env.range.at(thisPeakTime).linlin(0, 1, 0, maxTexture).asInteger;
						//("this texture = " + texture).postln;
						thisSampler.key(keynums.asArray.choose, [\peakat, thisPeakTime], amp: env.at(thisPeakTime), texture: texture);
				}
			};
		}
	}
*/

	playEnv {arg env, pitch, maxTexture = nil;

		samplers.do{|thisSampler, samplerIndex|
			if(thisSampler.samples[0].temporalCentroid[0] < 0.15)
			{thisSampler.playEnv(pitch, env)}
			{
				env.peakTime.do{|thisPeakTime|
					var args = SamplerArguments.new.set(keynums: pitch.asArray.choose);
					var maxTexture = SamplerQuery.getSamplesByKeynum(thisSampler, args).size;
					var texture = env.range.at(thisPeakTime).linlin(0, 1, 0, maxTexture).asInteger;
					//("this texture = " + texture).postln;
					thisSampler.key(pitch.asArray.choose, [\peakat, thisPeakTime], amp: env.at(thisPeakTime));
				}
			};
		}
	}



	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = 0, midiChannel = 0;
		var args = SamplerArguments.new;
		var playkey = keynums ? rrand(10.0, 100.0);
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		samplers.do{|thisSampler, samplerIndex|
			args.playSamples = args.playSamples.add(SamplerQuery.getSamplesByKeynum(thisSampler, args)).flat;
		};
		args.getGlobalDur;

		args.playSamples = SamplerQuery.getPlayTime(args);

		Routine.run{
			args.playSamples.do{|thisSample, index| //thisSample are realizations of SamplerPrepare class
				var bufRateScale = thisSample.bufServer.sampleRate / thisSample.sample.sampleRate;
				var buf = thisSample.sample.activeBuffer[thisSample.section];
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





}//End of SamplerDB class

