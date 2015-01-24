
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
}//End of SamplerDB class

