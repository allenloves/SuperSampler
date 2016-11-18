SamplerPrepare {
	var <> sample;    //SampleDescript realization
	var <> section;   //subset of active data choosen
	var <> wait;      //Wait time for playback
	var <> rate;      //play rate for pitch adjustment
	var <> position;  //start position in the buffer
	var <> expand;    //Granular expansion
	var <> bend;

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
}