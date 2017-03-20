SamplerArguments{
	classvar < dbs;
	var <> keynums = 60;
	var <> syncmode = \keeplength;
	var <> expand = nil;
	var <> dur = nil;
	var <> amp = 1;
	var <> ampenv;  //Default set in .init
	var <> pan = 0;
	var <> panenv;  //Default set in .init
	var <> bend;
	var <> bendenv = nil;
	var <> texture = nil;
	var <> out = 0;
	var <> grainRate = 20;
	var <> grainDur = 0.15;
	var <> midiChannel = 0;

	//============================
	//These are outcomes from query calculation
	var <> playSamples;  //Contains SamplePrepare class


	//Duration after play rate, before pitch bend
	var <> globalDur;
	var <> globalAttackDur;
	var <> globalReleaseDur;




	*initClass{
		dbs = Dictionary.new;
	}

	*new{
		^super.new.init();
	}

	*db{|name|
		^this.class.dbs.at(name);
	}

	init{
		var timestamp = Date.getDate.format("%m%H%M%S");
		var serialNumber = 0;
		var name = (timestamp.asString ++ "_" ++ serialNumber).asSymbol;

		while ({this.class.dbs.at(name).isNil.not},
			{
				serialNumber = serialNumber + 1;
				name = (timestamp.asString ++ "_" ++ serialNumber).asSymbol;
		});

		this.class.dbs.put(name,this);  //trace back old sounds
		playSamples = [];
		ampenv = Env([1, 1], [1]);
		panenv = Env([0, 0], [1]);
	}

	set{|keynums, syncmode, dur, amp, ampenv, pan, out, panenv, bendenv, texture, expand, grainRate, grainDur, midiChannel|
		this.keynums = keynums ? this.keynums.asArray.flat;
		this.syncmode = syncmode ? this.syncmode;
		this.dur = dur ? this.dur;
		this.amp = amp ? this.amp;
		this.pan = pan ? this.pan;
		this.out = out ? this.out;
		this.midiChannel = midiChannel ? this.midiChannel;

		case //for ampenv
		{ampenv.isArray} {this.ampenv= ampenv.pairsAsEnv.asArray}
		{ampenv.isKindOf(Env)}{this.ampenv=ampenv.asArray}
		{true}{this.ampenv=#[1, 1, -99, -99, 1, 1, 1, 0]}; // default [0 1 1 1]

		case  //for panenv
		{panenv.isArray} {this.panenv= panenv.pairsAsEnv.asArray}
		{panenv.isKindOf(Env)}{this.panenv=panenv.asArray}
		{true}{this.panenv=#[ 0, 1, -99, -99, 0, 1, 1, 0 ]}; // default [0 0 1 0]

		case  //for bendenv
		{bendenv.isArray} {this.bendenv= bendenv.pairsAsEnv.asArray}
		{bendenv.isKindOf(Env)}{this.bendenv=bendenv.asArray}
		{true}{this.bendenv=#[1, 1, -99, -99, 1, 1, 1, 0]}; // default [0 1 1 1]



		this.texture = texture ? this.texture;

		if (expand.isNumber) {
			this.expand = expand;
			if (grainRate.isNumber) {
				this.grainRate = grainRate;
			};
			if (grainDur.isNumber) {
				this.grainDur = grainDur;
			};
		};
	}

	setSamples {|samples|
		playSamples = samples;
		this.getGlobalDur;
	}

	getGlobalDur {
		var positiveArray;
		var negativeArray;
		var dursBeforePeak = [];
		var dursAfterPeak = [];

		positiveArray = playSamples.select({|thisSample| thisSample.rate.isPositive});
		negativeArray = playSamples.select({|thisSample| thisSample.rate.isPositive.not});

		dursBeforePeak = dursBeforePeak.add(positiveArray.collect({|thisSample| thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}));
		dursBeforePeak = dursBeforePeak.add(negativeArray.collect({|thisSample| thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs})).flat;

		dursAfterPeak = dursAfterPeak.add(positiveArray.collect({|thisSample| thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs}));
		dursAfterPeak = dursAfterPeak.add(negativeArray.collect({|thisSample| thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs})).flat;

		globalAttackDur = dursBeforePeak.maxItem * (expand ? 1);
		globalReleaseDur = dursAfterPeak.maxItem * (expand ? 1);
		globalDur = (dursBeforePeak.maxItem + dursAfterPeak.maxItem) * (expand ? 1);
		^globalDur;
	}

}