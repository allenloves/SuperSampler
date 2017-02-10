SamplerPrepare {
	var <> samplerName;   //Sampler name as address in SamplerDB
	var <> sample;    //SampleDescript realization
	var <> section;   //subset of active data choosen
	var <> wait;      //Wait time for playback
	var <> rate;      //play rate for pitch adjustment
	var <> position;  //start position in the buffer
	var <> expand;    //Granular expansion
	var <> bend;
	var <> duration;  //play duration after pitch adjustment, before pitch bend

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