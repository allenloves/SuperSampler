
//Instance of SamplerDB is a database of multiple Sampler
//The data structure of SamplerDB Instance is a Dictionary in this format
// [ \Sampler_Name -> Sampler_Instance,  \Sampler_Name -> Sampler_Instance,  ...]
SamplerDB{
	classvar <dbs;  //System log for all sampler databases.
	var <label;
	var <samplers;  //database of Samplers

	*new{arg dbname = \default;
		^super.new.init(dbname);
	}

	*initClass{
		dbs = Dictionary.new;
	}

	*isLoaded{arg dbname;
		^dbs.at(dbname.asSymbol).isNil.not;
	}

	*free {
		dbs = nil;
	}

	init{arg dbname;
		label = dbname;
		if(this.class.dbs.at(label).isNil.not)
		{
			"Overwritting existing database".postln;
			this.class.dbs.at(label).free;
			this.class.dbs.put(label, this)
		}
		{this.class.dbs.put(label, this)};

		samplers = Dictionary.new;
	}

	put{arg name, sampler;
		samplers.put(name.asSymbol, sampler);
	}


	//TODO: Free all Samplers in the database.
	free{
		dbs.removeAt(label);
		samplers = nil;
	}

	//testrun
	playEnv{arg keynums, env;

		samplers.do{|thisSampler, samplerIndex|
			if(thisSampler.samples[0].temporalCentroid[0] < 0.15)
			{thisSampler.playEnv(keynums, env)}
			{
				env.peakTime.do{|thisPeakTime|
//					if(1.rand <= env.range.at(thisPeakTime))
//					{
						var args = SamplerArguments.new.set(keynums: keynums);
						var maxTexture = thisSampler.getPlaySamples(args).size;
						var texture = env.range.at(thisPeakTime).linlin(0, 1, 0, maxTexture).asInteger;
						//("this texture = " + texture).postln;
						thisSampler.key(keynums, [\peakat, thisPeakTime], amp: env.at(thisPeakTime), texture: texture);
//					};
				}
			};
		}
	}
}//End of SamplerDB class

