SamplerArguments{
	classvar < dbs;
	var <> samplerName;
	var <> keynums = 60;
	var <> keysign = 1;
	var <> detune = 0;
	var <> syncmode = \keeplength;
	var <> expand = nil;
	var <> dur = nil;
	var <> amp = 1;
	var <> ampenv;  //Default set in .init
	var <> pan = 0;
	var <> panenv;  //Default set in .init
	var <> bend;
	var <> bendenv = nil;
	var <> texture;
	var <> out = 0;
	var <> grainRate = 20;
	var <> grainDur = 0.15;
	var <> midiChannel = 0;

	//Voice-mode (gated keyboard playback) args.
	//Inert when loop == 0 and the voice path is not used.
	var <> gate = 1;
	var <> loop = 0;             //0 = one-shot, 1 = looped
	var <> loopDir = \fwd;       //\fwd / \rev / \palin
	var <> loopMode = \trapezoid;//\trapezoid (cheap) / \xfade (2-tap equal-power)
	var <> loopStart;            //frames; nil -> 0
	var <> loopEnd;              //frames; nil -> BufFrames of the section
	var <> loopXfade = 0.02;     //seconds; trapezoidal width; ignored when loopMode == \xfade
	var <> attack = 0.005;
	var <> decay = 0.0;          //seconds; 0 -> ADSR collapses to ASR
	var <> sustainLevel = 1;     //0..1; level held while gate == 1
	var <> release = 0.05;

	//Ableton-style release region (second loop). Engaged on note-off (gate -> 0).
	//Inert when releaseMode == \off.
	var <> releaseMode = \off;   //\off / \oneShot / \loop / \palin
	var <> releaseStart;         //frames; nil -> 0
	var <> releaseEnd;           //frames; nil -> BufFrames of the section
	var <> releaseXfade = 0.02;  //seconds; crossfade from sustain ptr to release ptr

	//for playEnv
	var <> env;
	var <> morphNum = 0;
	var <> morphMode = \atpeak;
	var <> morphCrossfade = 1;

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
		env = Env();
	}

	set{|keynums, syncmode, detune, dur, amp, ampenv, pan, out, panenv, bendenv, texture, expand, grainRate, grainDur, midiChannel, env, morph,
		gate, loop, loopDir, loopMode, loopStart, loopEnd, loopXfade, attack, decay, sustainLevel, release,
		releaseMode, releaseStart, releaseEnd, releaseXfade|
		this.keynums = keynums.value.asArray.flat ? this.keynums.asArray.flat;
		this.detune = detune.value ? this.detune;
		this.syncmode = syncmode ? this.syncmode;
		this.dur = dur.value ? this.dur;
		this.amp = amp.value ? this.amp;
		this.pan = pan.value ? this.pan;
		this.out = out ? this.out;
		this.midiChannel = midiChannel ? this.midiChannel;
		this.gate = gate ? this.gate;
		this.loop = loop ? this.loop;
		this.loopDir = loopDir ? this.loopDir;
		this.loopMode = loopMode ? this.loopMode;
		this.loopStart = loopStart ? this.loopStart;
		this.loopEnd = loopEnd ? this.loopEnd;
		this.loopXfade = loopXfade ? this.loopXfade;
		this.attack = attack ? this.attack;
		this.decay = decay ? this.decay;
		this.sustainLevel = sustainLevel ? this.sustainLevel;
		this.release = release ? this.release;
		this.releaseMode = releaseMode ? this.releaseMode;
		this.releaseStart = releaseStart ? this.releaseStart;
		this.releaseEnd = releaseEnd ? this.releaseEnd;
		this.releaseXfade = releaseXfade ? this.releaseXfade;
		// this.env = env.value ? this.env;
		this.texture = texture.value ? SSampler.defaultTexture;
		// this.morphNum = morph.asArray[0] ? this.morphNum;
		// this.morphCrossfade = morph.asArray[1] ? this.morphCrossfade;
		// this.morphMode = morph.asArray[2] ? this.morphMode;
		//
		// this.env = env.segment(this.morphNum, this.morphCrossfade, this.morphMode);

		case //for ampenv
		{ampenv.isArray} {this.ampenv= ampenv.pairsAsEnv.stretch.asArray}
		{ampenv.isKindOf(Env)}{this.ampenv=ampenv.stretch.asArray}
		{true}{this.ampenv=#[1, 1, -99, -99, 1, 1, 1, 0]}; // default [0 1 1 1]

		case  //for panenv
		{panenv.isArray} {this.panenv= panenv.pairsAsEnv.stretch.asArray}
		{panenv.isKindOf(Env)}{this.panenv=panenv.stretch.asArray}
		{true}{this.panenv=#[ 0, 1, -99, -99, 0, 1, 1, 0 ]}; // default [0 0 1 0]

		case  //for bendenv
		{bendenv.isArray} {this.bendenv= bendenv.pairsAsEnv.stretch.asArray}
		{bendenv.isKindOf(Env)}{this.bendenv=bendenv.stretch.asArray}
		{true}{this.bendenv=#[1, 1, -99, -99, 1, 1, 1, 0]}; // default [0 1 1 1]

		if (expand.isNil.not) {
			this.expand = expand.value.asArray.choose;
			if (grainRate.isNil.not) {
				this.grainRate = grainRate.value.asArray.choose;
			};
			if (grainDur.isNil.not) {
				this.grainDur = grainDur.value.asArray.choose;
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

	play {
		SSampler.playArgs(this);
	}

}