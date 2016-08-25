(
var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
var win, drg, sfw, drw;
var onsetButton, onsetView, breakButton, breakView, peakIndexButton, peakIndexView, peakTimeButton, peakTimeView, startButton, startView;
var soundfile;
~temp = nil;


win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200)).front.alwaysOnTop_(true).background_(colorSet1[2]);

//*** Drop Area ***
drg = DragSink(win, Rect(15, 15, 700, 50))
.resize_(2)
.background_(colorSet1[1])
.align_(\center)
.string_("drop file here")
.action_({|obj|
	obj.string.postln;
	if(obj.string.isSoundFile){  //using wslib
		sfw = SoundFileView(win, Rect(15, 80, win.bounds.width-30, win.bounds.height-100))
		.resize_(5).background_(colorSet1[0]).gridOn_(false)
		.soundfile_(SoundFile.openRead(obj.string)).read(closeFile: true).refresh;

		~temp = SampleDescript(obj.string, loadToBuffer: false);

		//*** Draw index lines ***
		drw = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
		.drawFunc_({|uview|

			//*** Draw SCMIR hoptime grid line***
			block{|break|
				var linelocation = 0;
				while({linelocation < uview.bounds.width},
					{
						Pen.strokeColor_(Color.gray)
						.moveTo(linelocation @ 0)
						.lineTo(linelocation @ (uview.bounds.height))
						.stroke;
						linelocation = linelocation + (SCMIR.hoptime * uview.bounds.width / ~temp.duration);
					};
				)
			};

		};)//.drawFunc
	}//if statement
});//.DragSink action



//** Draw Onset **//
onsetButton = Button(win, Rect(730, 15, 90, 22))
.resize_(3)
.states_([
	["Onset", Color.black, colorSet1[1]],
	["Onset", Color.white, Color.red]
])
.action_({|butt|
	if(~temp.class == SampleDescript){
		if(butt.value == 1)
		{
			onsetView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
			.drawFunc_({|onsetView|
				//***Draw onset***
				~temp.onsetTime.do({|otime|
					var linelocation = onsetView.bounds.width * otime / ~temp.duration;
					Pen.strokeColor_(Color.red)
					.moveTo(linelocation @ 0)
					.lineTo(linelocation @ (onsetView.bounds.height))
					.stroke;
				});
			});
		}
		{onsetView.remove};
	}
});//onsetButton

//** Draw Section BreakPoint **//
breakButton = Button(win, Rect(830, 15, 90, 22))
.resize_(3)
.states_([
	["Break Point", Color.black, colorSet1[1]],
	["Break Point", Color.black, Color.cyan]
])
.action_({|butt|
	if(~temp.class == SampleDescript){
		if(butt.value == 1)
		{
			breakView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
			.drawFunc_({|breakView|
				~temp.sectionBreakPoint.do({|btime|
					var linelocation = breakView.bounds.width * btime * SCMIR.hoptime / ~temp.duration;
					Pen.strokeColor_(Color.cyan)
					.moveTo(linelocation @ 0)
					.lineTo(linelocation @ (breakView.bounds.height))
					.stroke;
				});
			});
		}
		{breakView.remove};
	}
});//breakButton



//*** Draw Peak Index ***//
peakIndexButton = Button(win, Rect(730, 42, 90, 22))
.resize_(3)
.states_([
	["Peak Index", Color.black, colorSet1[1]],
	["Peak Index", Color.black, Color.white]
])
.action_({|butt|
	if(~temp.class == SampleDescript){
		if(butt.value == 1)
		{
			peakIndexView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
			.drawFunc_({|breakView|
				~temp.peakIndex.do({|ptime|
					var linelocation = peakIndexView.bounds.width * ptime * SCMIR.hoptime / ~temp.duration;
					Pen.strokeColor_(Color.white)
					.moveTo(linelocation @ 0)
					.lineTo(linelocation @ (peakIndexView.bounds.height))
					.stroke;
				});
			});
		}
		{peakIndexView.remove};
	}
});//peakIndexButton


//*** Draw Peak Time ***//
peakTimeButton = Button(win, Rect(830, 42, 90, 22))
.resize_(3)
.states_([
	["Peak Time", Color.black, colorSet1[1]],
	["Peak Time", Color.black, Color.white]
])
.action_({|butt|
	if(~temp.class == SampleDescript){
		if(butt.value == 1)
		{
			peakTimeView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
			.drawFunc_({|breakView|
				~temp.peakTime.do({|ptime|
					var linelocation = peakTimeView.bounds.width * ptime / ~temp.duration;
					Pen.strokeColor_(Color.white)
					.moveTo(linelocation @ 0)
					.lineTo(linelocation @ (peakTimeView.bounds.height))
					.stroke;
				});
			});
		}
		{peakTimeView.remove};
	}
});//peakTimeButton


//**Draw Start Time**//
startButton = Button(win, Rect(930, 15, 90, 22))
.resize_(3)
.states_([
	["Start Time", Color.black, colorSet1[1]],
	["Start Time", Color.black, Color.green]
])
.action_({|butt|
	if(~temp.class == SampleDescript){
		if(butt.value == 1)
		{
			startView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
			.drawFunc_({|breakView|
				~temp.startTime.do({|stime|
					var linelocation = startView.bounds.width * stime / ~temp.duration;
					Pen.strokeColor_(Color.green)
					.moveTo(linelocation @ 0)
					.lineTo(linelocation @ startView.bounds.height)
					.stroke;
				});//draw section lines
			});
		}
		{startView.remove};
	}
});//startButton

)


~temp.inspect;
~temp.onsetIndex;
~temp.peakIndex;
~temp.peakAmp;
~temp.peakTime;
~temp.sectionBreakPoint;
~temp.rmsDataBySection.plot;
~temp.peakAmp;
~temp.activeRMSData.plot;
