(
var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
var win, drg, sfw, drw;
var soundfile;

//Window.closeAll;
win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200)).front.alwaysOnTop_(true).background_(colorSet1[2]);
// receive anthing
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

		~temp = SampleDescript(obj.string);

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

			//***Draw onset***
			~temp.onsetTime.do({|otime|
				var linelocation = uview.bounds.width * otime / ~temp.duration;
				Pen.strokeColor_(Color.red)
				.moveTo(linelocation @ 0)
				.lineTo(linelocation @ (uview.bounds.height))
				.stroke;
			});

			//** Draw Section BreakPoint **
			~temp.sectionBreakPoint.do({|btime|
				var linelocation = uview.bounds.width * btime * SCMIR.hoptime / ~temp.duration;
				Pen.strokeColor_(Color.cyan)
				.moveTo(linelocation @ 0)
				.lineTo(linelocation @ (uview.bounds.height))
				.stroke;
			});


			//*** Draw Peak Index ***
			~temp.peakIndex.do({|ptime|
				var linelocation = uview.bounds.width * ptime * SCMIR.hoptime / ~temp.duration;
				Pen.strokeColor_(Color.white)
				.moveTo(linelocation @ 0)
				.lineTo(linelocation @ (uview.bounds.height))
				.stroke;
			});


			//****Draw section start time***
			// f.startTime.do({|stime|
			// 	var linelocation = uview.bounds.width * stime / ~temp.duration;
			// 	Pen.strokeColor_(Color.green)
			// 	.moveTo(linelocation @ 0)
			// 	.lineTo(linelocation @ uview.bounds.height)
			// 	.stroke;
			// });//draw section lines

		};)//.drawFunc
	}//if statement
})//.action

)

~temp.peakIndex;
~temp.rmsDataBySection.size;
~temp.soundFile.plot;
