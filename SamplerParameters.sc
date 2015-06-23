SamplerParameters{
	var <> keynum = 60;
	var <> dur = nil;
	var <> amp = 1;
	var <> ampenv;
	var <> pan = 0;
	var <> panenv;
	var <> syncmode;
	var <> texture = nil;


	*new{
		^super.new.init();
	}

	init{
		ampenv = Env([1, 1], [1]);
		panenv = Env([0, 0], [1]);
		syncmode = \keeplength;
	}

	setampenv{|env, curve|
		if(env.isArray)
		{this.ampenv = env.pairsAsEnv(curve)}
		{this.ampenv = env}
	}

	setpanenv{|env, curve|
		if(env.isArray)
		{this.panenv = env.pairsAsEnv(curve)}
		{this.panenv = env}
	}


}