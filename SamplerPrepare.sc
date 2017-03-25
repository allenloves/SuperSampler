SamplerPrepare {
	var <> bufServer;
	var <> samplerName;   //Sampler name as address in SamplerDB
	var <> sample;    //SampleDescript realization
	var <> buffer;    //buffer to be played = sample.activeBuffer[section]
	var <> section;   //subset of active data choosen
	var <> wait;      //Wait time for playback
	var <> rate;      //play rate for pitch adjustment
	var <> position;  //start position in the buffer
	var <> expand;    //Granular expansion
	var <> bendenv;
	var <> duration;  //play duration after pitch adjustment, before pitch bendenv

	*new {
		^super.new.init();
	}

	init {
		section = 0;
		wait = 0;
		rate = 1;
		position = 0;
		expand = nil;
	}

	setRate {|r|
		rate = r;
		duration = (sample.activeBuffer[section][0].duration / rate).abs;
	}
}