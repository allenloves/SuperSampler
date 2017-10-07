
//Instance of SamplerDB is a database of multiple Sampler
//The data structure of SamplerDB Instance is a Dictionary in this format
// [ \Sampler_Name -> Sampler_Instance,  \Sampler_Name -> Sampler_Instance,  ...]
SamplerDB{
	classvar < dbs;  //System log for all sampler databases.
	classvar < samplers; //System log for all loaded samplers.
	var < label;     //name of this SamplerDB instance
	var < samplers;  //a Dictionary with the database of Samplers (name -> Sampler)

	//Corpus:
	//Data stores in the form of KDTree
	// var < shapeTree = nil;
	// var < mfccTree = nil;
	var < kdTree = nil;


	*new{arg dbname = \default;
		if(dbs.at(dbname.asSymbol).isNil)
		{^super.new.init(dbname)}
		{^dbs.at(dbname.asSymbol)};
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

	copy {
		var copy;
		copy = this;
		^copy;
	}

	makeTree {
		// var shapeTreePrep;
		// var mfccTreePrep;
		var kdTreePrep;

		// if(shapeTree.isNil)
		// {shapeTreePrep = []}
		// {shapeTreePrep = shapeTree.asArray(incLabels: true)};
		//
		// if(mfccTree.isNil)
		// {mfccTreePrep = []}
		// {mfccTreePrep = mfccTree.asArray(incLabels: true)};

		if(kdTree.isNil)
		{kdTreePrep = []}
		{kdTreePrep = kdTree.asArray(incLabels: true)};

		samplers.do{|sampler|
			var thisKDTreeNode = sampler.kdTreeNode;
			// var thisTemporalCentroid = sampler.averageTemporalCentroid;
			// var thisDuration = sampler.averageDuration;
			// var thisMFCC = sampler.averageMFCC;

			// shapeTreePrep = shapeTreePrep.add([thisDuration, thisTemporalCentroid, sampler]);
			// mfccTreePrep = mfccTreePrep.add([thisMFCC, sampler].flat);
			kdTreePrep = kdTreePrep.add([thisKDTreeNode, sampler].flat);

		};

		// shapeTree = KDTree.new(shapeTreePrep, lastIsLabel: true);
		// mfccTree = KDTree.new(mfccTreePrep, lastIsLabel: true);
		kdTree = KDTree.new(kdTreePrep, lastIsLabel: true);
	}


	//TODO: Free all Samplers in the database.
	free {arg freeSamplers = true;
		dbs.removeAt(label);
		if(freeSamplers){samplers.do{|thisSampler| thisSampler.free;}};
		samplers = nil;
	}


	//morph is an array contains three elements: number of segments, crossfade, strategy.  See documentation of Env class.
	//texture indicates the number of sampler instruments played in the same time if possible.
	//samplerThickness is the number of sounds in a sampler instrument played in the same time.
	playEnv {arg env, pitch, amp = 1, pan = 0, dur = nil, numSampler = 2, samplerThickness = 2, morph = [0, 0, \atpeak], diversity = nil, out = 0, midiChannel = 0;
		var morphEnvs = env.segment(numSegs: morph.asArray[0] ? 0, crossfade: morph.asArray[1] ? 0, strategy: morph.asArray[2] ? \atpeak);
		var playingSamplers;

		Routine.run({
			//for each segmented envelope, assign different sampler to play
			morphEnvs.do{|thisEnv, envIndex|
				var envelope = thisEnv[0];
				var waittime = thisEnv[1];

				if(envIndex == 0)
				{
					var samplerKeys = samplers.keys.asArray.scramble[0..(numSampler-1)];
					playingSamplers = Dictionary.newFrom([samplerKeys, samplers.atAll(samplerKeys).asArray].flop.flat);

				}
				// starting from the second section of envelope
				{
					if(diversity.isNumber)
					{

						var lastPlayingSamplers = playingSamplers;
						playingSamplers = Dictionary.new;
						lastPlayingSamplers.do{|thisSampler, index|
							var samplerByRadious;

							samplerByRadious = kdTree.radiusSort(thisSampler.kdTreeNode, diversity).at(rrand(0,3));

							if(samplerByRadious.isNil)
							{playingSamplers.put(thisSampler.name, thisSampler)}
							{playingSamplers.put(samplerByRadious.label.name, samplerByRadious.label)};
						};
					}
					{
						var samplerKeys = samplers.keys.asArray.scramble[0..(numSampler-1)];
						playingSamplers = Dictionary.newFrom([samplerKeys, samplers.atAll(samplerKeys).asArray].flop.flat);

					};
				};


				playingSamplers.do{|thisSampler, samplerIndex|
					thisSampler.playEnv(envelope, pitch, dur: dur, amp: amp, pan: pan, maxtexture: samplerThickness, out: out, midiChannel: midiChannel);

					/*
					if(thisSampler.samples[0].temporalCentroid[0] < 0.15)
					{thisSampler.playEnv(envelope, pitch)}
					{
						env.peakTime.do{|thisPeakTime|
							var args = SamplerArguments.new.set(keynums: pitch.asArray.choose);
							var maxTexture = SamplerQuery.getSamplesByKeynum(thisSampler, args).size;
							var texture = envelope.range.at(thisPeakTime).linlin(0, 1, 1, maxTexture).asInteger;
							thisSampler.key(pitch.asArray.choose, [\peakat, thisPeakTime], amp: envelope.at(thisPeakTime), texture: texture);
						}
					};
					*/

				};
				waittime.yield;
			}
		});
	}


	key {arg keynums, syncmode = \keeplength, numSampler = 3, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = nil, expand = nil, grainRate = 20, grainDur = 0.15, out = 0, midiChannel = 0;
		var args = SamplerArguments.new;
		var playkey = keynums ? rrand(10.0, 100.0);
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		samplers.asArray.scramble[0..((numSampler-1).thresh(0))].do{|thisSampler, samplerIndex|
			args.playSamples = args.playSamples.add(SamplerQuery.getSamplesByKeynum(thisSampler, args)).flat;
		};
		args.getGlobalDur;
		Sampler.playArgs(args);
	}


}//End of SamplerDB class

