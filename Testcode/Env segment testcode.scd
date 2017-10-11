//Env.segment test code



Env([0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0]).segment(11, 0.2);








(
~testEnv = {|env, numseg = 1, crossfade = 0.01, strategy = \atpeak|

	var segments = env.segment(numseg, crossfade, strategy);

	Routine{
		segments.do{|thisseg, index|
			var envelop = thisseg[0];
			var waittime = thisseg[1];

			{SinOsc.ar(freq: exprand(200, 800), mul: envelop.kr(doneAction: 2)) ! 2}.play;
			waittime.yield;
		}
	}.play;
}
)

(
~testEnv.value(Env([1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1]).duration_(10), 5, 0.7);
~testEnv.value(Env([1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1]).duration_(10));
)

g = SampleDescript("/Users/mac/Library/Application Support/SuperCollider/downloaded-quarks/SuperSampler/Testcode/Sounds/cage.wav");
~cageenv = g.env.normalize;

g.buffer[0];

~cageenv.duration;
g.buffer[0].duration;


~cageenv.segment[0][0].duration;
g.play;

(
~testEnv.value(~cageenv);
g.play;
)




(
~cageenv.segment[0][0].test;
g.play;
)

4800/4410

(
{SinOsc.ar(440, mul: EnvGen.kr(~cageenv, doneAction:2)) ! 2}.play;
g.play;
)
