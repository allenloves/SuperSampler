//Sample Descripter By Allen SC Wu
//Extra classes requirements: wslib Quark.



SampleDescript{

	classvar guiTempFile;
	classvar <> pitchThresh;

	//raw data
	var <mirDataByFeatures; //[[RMS], [Pitch], [hasPitch], [centroid], [SensoryDissonance], [SpecFlatness]]
	var <rmsData;
	var <centroidData;
	var <noiseData;
	var <rmsDataBySection;
	var <pitchData; //[[pitch, hasPitch]....]
	var <mfccData;
	var <activeCentroidData;  //frequency centroid
	var <activeNoiseData;  // higher value indicates dissonance
	var <activeRMSData;
	var <activeSpecFlatness;  // 0-1, 0 is single sinesoid wave, 1 is white noise.  This indicates the degree of noiseness.


	//General information
	var <file;  //An SCMIRAudioFile.
	var <filename;
	var <sampleRate;
	var <numSamples;
	var <numChannels;
	var <bufferServer;
	var <buffer;
	var <activeBuffer;  //Array of buffers with each section (is there a way to play one part of buffer without making a copy?)
	var <keynumFromFileName;  //get key number from file name, nil if there is none
	var <keynumFromPitchFound;  //get key number from pitch detection. not necessarily an integer.
	var <hoptime;      //for internal use, time for each hop
	var <frameTimes;  //Time stamp of each frame


	//****** global description *****
	// time domain
	var <duration;  //Total duration of sound file.
	var <numFrames;
	var <sectionBreakPoint;  //nadir (lowest) point in between onsets.
	var <globalPeakIndex;
	var <globalPeakAmp;  //Amplitude at RMS peak point.
	var <globalPeakTime;  //Time on the peak point.


	// **** local information by Onsets *****
	// Temporal information
	var <> keynum;
	var <activeDuration;
	var <mfcc;
	var <onsetTime;
	var <onsetIndex; //frame index at onset
	var <startIndex;
	var <startTime;  //beginning time of start frame
	var <endIndex;
	var <endTime;
	var <peakIndex; //frame index at the peak time.  NOTE: this one might not be an integer
	var <peakTime;  //global time point of each local peak
	var <peakAmp;
	var <attackDur;  // Attack Time
	var <releaseDur;  // Release Time
	var <temporalCentroid; // Shorter value indicates percussive sounds, longer value indicates sustained sounds.
	var <activeEnv;

	// Information for Debugging
	var <soundFile;
	var <soundFileArray;



	*initClass {
		Platform.case(
			\osx,       { pitchThresh = 0.9 },
			\linux,     { pitchThresh = 0.5 },
			\windows,   { pitchThresh = 0.9 }
		);
	}


	//parameters
	//File Name, Normalize, Start Tine, Duration, Threshold for attack point, Threshold for end point, Threshold for onset, Time threshold for onset grouping.
	*new {arg filename, normtype=0, start=0, dur=0, startThresh=0.01, endThresh=0.01, onsetThresh=0.5, groupingThresh = 0.32, filenameAsNote = false, loadToBuffer = false, normalize = false, server = Server.default, action;

		^super.new.init(filename, normtype, start, dur, startThresh, endThresh, onsetThresh, groupingThresh, filenameAsNote, loadToBuffer, normalize, server, action);
	}

	init {|fileName, normtype, start, dur, startThresh, endThresh, onsetThresh, groupingThresh, filenameAsNote, loadToBuffer, normalize, server, action|
		var cond = Condition.new(false);
		//var soundFile, soundFileArray;

		buffer = [];
		server.postln;
		bufferServer = server;

		// //If a buffer is loaded to the sampler, write into a file before load to SCMIRAudioFile
		// fork{

		// //Write Buffer into a file if the input is a buffer.
		// if(fileName.class == Buffer)
		// {//if input is a buffer, save the buffer into a file before loading to SCMIR
		// 	buffer = fileName;
		// 	loadToBuffer = false;
		// 	filenameAsNote = false;
		// 	//provide a tempbuffer filename and save to a file.
		// 	filename = Platform.defaultTempDir +/+ "supersampler" ++ UniqueID.next ++ ".aiff";
		// 	fileName.write(filename, completionMessage: {cond.test=true;cond.signal;});
		// }
		// {filename = fileName; cond.test=true; cond.signal;};//if it is not a buffer
		//
		// cond.wait;
		//
		// ("Buffer rendered to" + filename).postln;


		//Check if the file exist
		if(File.exists(fileName).not)
		{Error("File % could not be found".format(fileName)).throw}
		{filename = fileName;};

		file = SS_SCMIRAudioFile(filename, [[\Tartini, 0], \SpecCentroid, \SpecFlatness, [\MFCC, 13], \RMS], normtype, start, dur);

		file.extractFeatures(normalize);
		file.extractOnsets();

		soundFile = SoundFile.openRead(filename);
		//soundFile = SS_SCMIR.soundfile;
		soundFileArray = FloatArray.newClear(soundFile.numFrames * soundFile.numChannels);
		soundFile.readData(soundFileArray);
		sampleRate = soundFile.sampleRate;
		numChannels = soundFile.numChannels;
		numSamples = soundFile.numFrames;
		hoptime = SS_SCMIR.hoptime;
		globalPeakAmp = soundFileArray.abs.maxItem;


		//get data from SCMIR
		duration = file.duration;
		mirDataByFeatures = file.featuredata.clump(file.numfeatures).flop;


		/*
		rmsData = mirDataByFeatures[0];
		pitchData = [mirDataByFeatures[1], mirDataByFeatures[2]].flop;
		centroidData = mirDataByFeatures[3];
		dissonanceData = mirDataByFeatures[4];
		*/

		//rmsData = soundFileArray.rms(hoptime * soundFile.numChannels); //average multiple channel sounds
		pitchData = [mirDataByFeatures[0], mirDataByFeatures[1]].flop;
		centroidData = mirDataByFeatures[2];
		noiseData = mirDataByFeatures[3];
		mfccData = mirDataByFeatures[4..16].flop;
		rmsData = mirDataByFeatures[17];

		frameTimes = file.frameTimes;// (* SS_SCMIR.samplingrate / sampleRate);
		numFrames = frameTimes.size;


		this.getOnsetTime(groupingThresh);
		this.getOnsetIndex;
		this.findBreakPointByOnsets;
		this.cleanBreakPointByOnsets(0.2); // clear unnecessary breakpoints
		this.sectionRmsDataByBreakPoint;
		this.findPeaksByOnsets;

		this.arEnv(startThresh, endThresh);
		this.getActiveEnv;
		this.getActiveData;
		this.getKeynum(filenameAsNote);
		this.getMFCC;

		if(loadToBuffer)
		{
			server.waitForBoot{
				this.loadToBuffer(bufferServer, action: action)};
		}
		{
			action.value;
		};

	}

	free {
		file = nil;
		sampleRate = nil;
		bufferServer = nil;
		keynumFromFileName = nil;
		keynumFromPitchFound = nil;
		frameTimes = nil;
		mirDataByFeatures = nil;
		rmsData = nil;
		rmsDataBySection = nil;
		centroidData = nil;
		noiseData = nil;
		duration = nil;
		sectionBreakPoint = nil;
		globalPeakIndex = nil;
		globalPeakAmp = nil;
		globalPeakTime = nil;
		keynum = nil;
		activeDuration = nil;
		onsetTime = nil;
		onsetIndex = nil;
		startIndex = nil;
		startTime = nil;
		endIndex = nil;
		endTime = nil;
		peakIndex = nil;
		peakTime = nil;
		peakAmp = nil;
		attackDur = nil;
		releaseDur = nil;
		temporalCentroid = nil;
		pitchData = nil;
		activeCentroidData = nil;
		activeNoiseData = nil;
		activeSpecFlatness = nil;

		buffer.free;
		activeBuffer.do({|thisBuffer, index|
			thisBuffer.free;
		});
		buffer = nil;
		activeBuffer = [];
		if(PathName(filename).pathOnly == Platform.defaultTempDir)
		{
			filename.asString + "has been deleted".postln;
			File.delete(filename)
		};
	}

/*
	//load sound file into buffers, and subsections into activeBuffer
	loadToBuffer {arg server = Server.default, action;
		var buf, startSample = 0, durSample;
		var cond = Condition(false);
		bufferServer = server;
		buffer.free;
		//free buffers if they were loaded before.
		activeBuffer.do({|buffer| buffer.free;});
		activeBuffer = [];
		"Loading soundfile into Buffer".postln;
		server.waitForBoot{
			Routine.run{
				//load the sound file into a master buffer
				buffer = Buffer.read(server, filename, action: {cond.test = true; cond.signal;});
				cond.wait;

				//load each sections into sub buffers
				startIndex.do{|thisSectionStartIndex, sectionIndex|
					startSample = thisSectionStartIndex * SCMIR.framehop;
					durSample = (activeRMSData[sectionIndex].size - 1) * SCMIR.framehop;
					activeBuffer = activeBuffer.add(Buffer.read(server, filename, startSample, durSample, {cond.test = true; cond.signal;}));
					cond.wait;
					activeBuffer[sectionIndex].path = (PathName(filename).pathOnly ++ PathName(filename).fileNameWithoutExtension ++ "_" ++ sectionIndex ++ "." ++ PathName(filename).extension);

				};
				action.value;
			}
		}
	}
*/


	//load sound file into arrays of mono buffers
	//for global sound file [channel 0, channel 1]
	//for activeBuffer [[channel 0, channel 1], [channel 0, channel 1], ....]
	loadToBuffer {arg server = Server.default, action;
		var buf, startSample = 0, durSample;
		var cond = Condition(false);
		bufferServer = server;
		if(buffer.isEmpty.not){
			numChannels.do{|chan| buffer[chan].free;};
			buffer = [];

			//free buffers if they were loaded before.
			activeBuffer.do({|buff| numChannels.do{|chan| buff[chan].free;}});
			activeBuffer = [];
		};

			"Loading soundfile into Buffer".postln;

		server.waitForBoot{
			Routine.run{
				//load the sound file into a master buffer
				numChannels.do{|channel|
					buffer = buffer.add(Buffer.readChannel(server, filename, channels: channel, action: {cond.test = true; cond.signal;}));
					cond.wait;
				};

				//load each sections into sub buffers
				startIndex.do{|thisSectionStartIndex, sectionIndex|
					var monoBufArray = [];
					startSample = thisSectionStartIndex * SS_SCMIR.framehop;
					durSample = (activeRMSData[sectionIndex].size - 1) * SS_SCMIR.framehop;

					numChannels.do{|channel|
						var monoBuf;
						monoBuf = Buffer.readChannel(server, filename, startSample, durSample, channel, {cond.test = true; cond.signal});
						monoBuf.path = (PathName(filename).pathOnly ++ PathName(filename).fileNameWithoutExtension ++ "_" ++ sectionIndex ++ "_[" ++ channel ++ "]." ++ PathName(filename).extension);
						monoBufArray = monoBufArray.add(monoBuf);
						cond.wait;
					};

					activeBuffer = activeBuffer.add(monoBufArray);
				};
				action.value;
			}
		}
	}


	freeBuffer{
		buffer.free;
		Routine{
			activeBuffer.do
			{
				|multipleChannelBuffer, index|
				multipleChannelBuffer.do
				{
					|monoBuffer|
					monoBuffer.free;
				};
				if(index == (activeBuffer.size - 1)){activeBuffer = []};
			};
		}.play;
	}

	getKeynum {arg filenameAsNote = false, pitchShift = 0;
		var str=PathName(filename).fileNameWithoutExtension;
		var l=str.size-1;
		var i=l, j, c;
		var deg=0, acc=0, oct=0;
		var foundPitches = [];
		keynumFromFileName = nil;
		keynumFromPitchFound = [];
		keynum = [];




		//retrive the key number from file name, if there is one.
		//From VKey by Prof. Heinrich Taube
		while ({i>=0 && "0123456789".includes(str.at(i))},{i=i-1});
		if (i<l) { // had digit at end, i now before all digits
			if (l - i >= 2){// had more than 2 digits at end
				if(l - i <=3){//had no more than 3 digits, make it key number;
					keynumFromFileName = str.copyRange(i+1,l).asInteger;
				}
				{// if there are more than 3 digits at the end of file
					//that is probably not a key number.
					keynumFromFileName = nil}
			}
			{//had only one digits at end
				j=i;
				while ({i>=0 && "AaBbCcDdEeFfGg#Ss".includes(str.at(i))},{i=i-1});
				if (i<j) { // had note and accidentals, i now before letter
					i=i+1;
					c=str.at(i);
					case
					{"Cc".includes(c)} {i=i+1; deg=0;}
					{"Dd".includes(c)} {i=i+1; deg=2;}
					{"Ee".includes(c)} {i=i+1; deg=4;}
					{"Ff".includes(c)} {i=i+1; deg=5;}
					{"Gg".includes(c)} {i=i+1; deg=7;}
					{"Aa".includes(c)} {i=i+1; deg=9;}
					{"Bb".includes(c)} {i=i+1; deg=11;}
					{true} {keynumFromFileName = nil};
					case
					{"Ss#".includes(str.at(i))} {i=i+1; acc=1;}
					{"Ffb".includes(str.at(i))} {i=i+1; acc= -1;};
					c=str.at(i);
					case
					{($0==c) && (i==(l-1)) && ($0==str.at(i+1))} {oct=0;}
					{($0==c) && (i==l)} {oct=1;}
					{($1==c) && (i==l)} {oct=2;}
					{($2==c) && (i==l)} {oct=3;}
					{($3==c) && (i==l)} {oct=4;}
					{($4==c) && (i==l)} {oct=5;}
					{($5==c) && (i==l)} {oct=6;}
					{($6==c) && (i==l)} {oct=7;}
					{($7==c) && (i==l)} {oct=8;}
					{($8==c) && (i==l)} {oct=9;}
					{($9==c) && (i==l)} {oct=10;}
					{true} {keynumFromFileName = nil};
					keynumFromFileName = (deg+acc+(oct*12));
				}
				// else
				{keynumFromFileName = nil};
		}}
		// else, no digit at end of file name, use pitch material for keynumber
		{keynumFromFileName = nil};




		//get keynum from pitch material, keynum will not necessarily be an integer.
		//The pitch data is gathered by the pitch data after the peak frame, if it has pitch.
		//Return nil if there is no pitch after 20 frames until the end of the section.
		peakIndex.do({|thisPeakIndex, sectionIndex|
			var pitch, hasPitch;
			var pitchCollection = [];

			thisPeakIndex = thisPeakIndex.asInteger;

			if(endTime[sectionIndex]-peakTime[sectionIndex] <= (hoptime * 20))
			{
				var pitchArray = pitchData[thisPeakIndex..endIndex[sectionIndex]].flop;
				pitch = pitchArray[0];
				hasPitch = pitchArray[1];
			}
			{
				pitch = pitchData[thisPeakIndex..thisPeakIndex + 20].flop[0];
				hasPitch = pitchData[thisPeakIndex..thisPeakIndex + 20].flop[1];
			};


			//find collections of data with pitch
			pitch.do{|thisPitch, index|
				if(hasPitch[index] >= pitchThresh)
				{
				pitchCollection = pitchCollection.add(thisPitch)
				};
			};


			//if no picth data is collected, find pitch before peak time
			if(pitchCollection.size == 0)
			{
				if(peakTime[sectionIndex]-startTime[sectionIndex] <= (hoptime * 20))
				{
					var pitchArray = pitchData[startIndex[sectionIndex]..thisPeakIndex[sectionIndex]].flop;
					pitch = pitchArray[0];
					hasPitch = pitchArray[1];
				}
				{
					var pitchArray = pitchData[(thisPeakIndex - 20)..thisPeakIndex].flop;
					pitch = pitchArray[0];
					hasPitch = pitchArray[1];
				};

				pitch.do{|thisPitch, index|
					if(hasPitch[index] >= pitchThresh)
					{
						pitchCollection = pitchCollection.add(thisPitch)
					};
				};
			};


			//if no pitch data is collected, than use centroid data for pitch
			//if there are pitch data collected, get the most occurred data for keynum
			if((pitchCollection.size == 0) || (pitchCollection.occurrencesArray(0.5).maxItem == 1))
			{keynumFromPitchFound = keynumFromPitchFound.add((centroidData[thisPeakIndex] ? centroidData[centroidData.size-1]).explin(20, 20000, 28, 103) - 12); // an octave lower to map to the range of my keyboard :p
			"no pitch detected, using centorid".postln;}
			{keynumFromPitchFound = keynumFromPitchFound.add(pitchCollection.mostOccurredItems(0.5).mean)};
		});


		//Now determine which answer to use
		if(filenameAsNote && keynumFromFileName.isNil.not)
		{keynum = Array.fill(peakIndex.size, keynumFromFileName) + pitchShift;}
		{keynum = keynumFromPitchFound + pitchShift;}


	}//end of getKeyNum



	//********************************************************************************
	//Separate sound file into recognizable sound sections if exist.
	//Here is how it works:
	//1. Find out onsets in this sound file using SCMIR onset data.
	//2. Find section breaking point based on the RMS nadir point inbetween each onsets.
	//3. Find peak point inbetween each sections.
	//4. Find attact and ending point from the peak point using fixed threshold method.


	//get onset time, if an onset is too close to previous one, it will be abandoned
	getOnsetTime {|groupingThresh = 0.32|
		var onsets = [file.onsetdata[0]];
		file.onsetdata.doAdjacentPairs({|thisOnset, nextOnset|
			//filter onsets if they are too close
			if((nextOnset - thisOnset) > groupingThresh, {onsets = onsets.add(nextOnset)});
		});
		if(onsets[0].isNil){onsets[0] = 0};
		onsetTime = onsets;
	}


	//get frame index at the onset time
	getOnsetIndex {
		onsetIndex = (onsetTime / hoptime).asInteger;
	}



	// Find breakpoint of a sample file by onsets.
	// using lowest point in between onsets to be section breakpoints.
	findBreakPointByOnsets {
		var nadirArray = [];
		sectionBreakPoint = [];
		onsetIndex.do{|thisOnset, index|
			var previousOnset = onsetIndex[index - 1];
			nadirArray = nadirArray.add(rmsData[previousOnset..thisOnset].lastMinIndex + (previousOnset ?? 0));
		};
		sectionBreakPoint = nadirArray;
	}

	// Remove breakpoints if the peak amplitude inside the section does not reach the threshold
	// preset to 0.2 of global peak amplitude
	cleanBreakPointByOnsets{|thresh = 0.2|
		var sectionSampleIndex = (sectionBreakPoint * hoptime * sampleRate * numChannels).asInteger;
		sectionBreakPoint = sectionBreakPoint.select({|thisSection, index|
			var thisSectionSampleIndex = sectionSampleIndex[index];
			var nextSectionSampleIndex = sectionSampleIndex[index+1];
			soundFileArray[thisSectionSampleIndex..nextSectionSampleIndex].abs.maxItem > (globalPeakAmp * thresh);
		})
	}


	//Separate rmsData into subsections by breakpoints.
	sectionRmsDataByBreakPoint {
		var output = [];
		rmsDataBySection = [];
		sectionBreakPoint.do{|thisSection, index|
			var nextSection = sectionBreakPoint[index + 1];
			output = output.add(rmsData[thisSection..nextSection]);
		};
		rmsDataBySection = output;
	}


	//find local peaks in the section breakpoint
	findPeaksByOnsets {
		var peakArray = [], peakAmpArray = [];
		var soundArrayByChannels;
		peakIndex = [];
		peakTime = [];
		peakAmp = [];
		soundArrayByChannels = soundFileArray.clump(numChannels).flop; //[[channel 1], [channel 2],....]
		sectionBreakPoint.do{|thisSection, index|
			var nextSection = sectionBreakPoint[index + 1];
			var peakhop = rmsDataBySection[index].maxIndex + thisSection; //find the biggest rms session
			var peaksInTheHop = [];
			//find detailed peak time and level
			numChannels.do{|channel|
				var channelPeakPoint = (soundArrayByChannels[channel].abs[peakhop*SS_SCMIR.framehop..(peakhop+1)*SS_SCMIR.framehop].maxIndex)/SS_SCMIR.framehop;
				var channelPeakLevel = soundArrayByChannels[channel].abs[peakhop*SS_SCMIR.framehop..(peakhop+1)*SS_SCMIR.framehop].maxItem;
				peaksInTheHop = peaksInTheHop.add([channelPeakPoint, channelPeakLevel]);
			};
			peaksInTheHop = peaksInTheHop.flop; //[peakPoints, peakLevels]
			peakAmpArray = peakAmpArray.add(peaksInTheHop[1].maxItem);
			peaksInTheHop = peaksInTheHop[0][peaksInTheHop[1].maxIndex];
			peakArray = peakArray.add(peakhop + peaksInTheHop);
		};


		//globalPeakAmp = peakAmpArray.maxItem;
/*
		// remove sections if the section peak amplitude is below 10% to the global peak
		peakIndex = peakArray.select({|item, i| peakAmpArray[i] > (globalPeakAmp * 0.1)});
		sectionBreakPoint = sectionBreakPoint.select({|item, i| peakAmpArray[i] > (globalPeakAmp * 0.1)});
		peakAmp = peakAmpArray.select({|item, i| item > (globalPeakAmp * 0.1)});
*/

		peakIndex = peakArray;
		peakAmp = peakAmpArray;
		globalPeakIndex = peakIndex[peakAmpArray.maxIndex];
		globalPeakTime = peakTime[peakAmpArray.maxIndex];

		peakTime = peakIndex * hoptime;

	}





	//Dictate attact/release time
	arEnv {|startThresh, endThresh|
		var startAmp;
		var endAmp;
		var thisSectionGlobalIndex = 0;
		startIndex = [];
		endIndex = [];
		startTime = [];
		endTime = [];
		attackDur = [];
		releaseDur = [];
		activeDuration = [];

		//for each onset section, find peaks
		rmsDataBySection.do{|thisSection, sectionIndex|
			var thisSectionStartIndex = sectionBreakPoint[sectionIndex];
			var startAmp = startThresh * peakAmp[sectionIndex];
			var endAmp = endThresh * peakAmp[sectionIndex];


			//startTime:
			//search for the first point pass above threshold.
			//MAYBE: Use adaptative threshold method (weakest effort method) for start time detection.
			//Peeters, Geoffroy. “A Large Set of Audio Features for Sound Description (Similarity and Description) in the Cuidado Project.” IRCAM, Paris, France (2004).
			block{|break|
				thisSection.do({|rmsenergy, index|
					if(rmsenergy >= startAmp)
					{
						startIndex = startIndex.add(thisSectionStartIndex + index);
						startTime = startTime.add(startIndex.last * hoptime);
						break.value(0);
					};
				})
			};

			//search for the last point pass below threshold.
			block{|break|
				var reversePeak = thisSection.reverse.maxIndex;
				thisSection.reverseDo({|rmsenergy, index|
					if(rmsenergy >= endAmp)
					{
						endIndex = endIndex.add(sectionBreakPoint[sectionIndex] + thisSection.size - index);
						endTime = endTime.add(endIndex.last * hoptime);
						break.value(0);
					};
				})
			};


		};


		attackDur = peakTime - startTime;
		releaseDur = endTime - peakTime;
		activeDuration = endTime - startTime;
	}


	//Separate Datas into subsections by active envelopes.
	//includes:
	//activeRMSData
	//activeCentroid
	//activeDissonanace
	//temporalCentorid
	getActiveData {

		//Datas to retrive
		activeCentroidData = [];
		activeNoiseData = [];
		temporalCentroid = [];
		activeRMSData = [];

		startIndex.do({|thisStartIndex, sectionIndex|
			var thisRMSData = rmsData[thisStartIndex..endIndex[sectionIndex]];
			var activeTime = frameTimes[thisStartIndex..endIndex[sectionIndex]] - frameTimes[thisStartIndex];

			activeRMSData=activeRMSData.add(thisRMSData);

			activeCentroidData = activeCentroidData.add(centroidData[thisStartIndex..endIndex[sectionIndex]]);

			activeNoiseData = activeNoiseData.add(noiseData[thisStartIndex..endIndex[sectionIndex]]);

			temporalCentroid = temporalCentroid.add(
				(thisRMSData*activeTime).sum / thisRMSData.sum;
			)
		})
	}


	//MFCC of a sound is decided arbitrarily to be the average MFCC data of 20 frames around the peak point
	getMFCC {
		var start, end;
		mfcc = [];
		peakIndex.do({|thisPeakIndex, sectionIndex|
			thisPeakIndex = thisPeakIndex.asInteger;
			if(thisPeakIndex-startIndex[sectionIndex] <= 10){start = startIndex[sectionIndex]}{start = thisPeakIndex - 10};
			if(endIndex[sectionIndex]-thisPeakIndex <= 10){end = endIndex[sectionIndex]}{end = thisPeakIndex - 10};
			mfcc = mfcc.add(mfccData[start..end].flop.collect({|data| data.sum/data.size}));
		});
		^mfcc;
	}


	//play the sound file, using(at) to play each onset.  If (at) is larger than the last onset index, it plays a random onset.
	play {arg at = nil, out = 0, server, detune = 0, pan = 0, level = 1;
		var buf, rate, cond = Condition.new(false);
		server = server ? Server.default;
		rate = 2 ** (detune / 12);
		if(buffer == [])
		{server.waitForBoot{this.loadToBuffer(server, action: {cond.test = true; cond.signal;})}}
		{cond.test = true; cond.signal;};

		Routine.run{
			cond.wait;
			if(at==nil)
			{buf = buffer}
			{//if the index number is not in the range of section numbers, play a random section
				if(at.asInteger >= 0 && at.asInteger < activeBuffer.size)
				{buf = activeBuffer[at.asInteger]}
				{buf = activeBuffer[activeBuffer.size.rand]}
			};

			if(buf.size == 1)
			{{Pan2.ar(PlayBuf.ar(1, buf[0], doneAction: 2, rate: BufRateScale.ir(buf[0]) * rate), pan, level)}.play(outbus: out)}
			{{Balance2.ar(PlayBuf.ar(1, buf[0], doneAction: 2, rate: BufRateScale.ir(buf[0]) * rate), PlayBuf.ar(1, buf[1], doneAction: 2, rate: BufRateScale.kr(buf[1]) * rate), pan, level)}.play(outbus: out)};
		}
	}


		//return an envelope to represent the whole sound file
	env {
		var frametimes;
		frametimes = Array.series(rmsData.size, 0, SS_SCMIR.framehop / sampleRate);
		^Env.pairs([frametimes, rmsData].flop, \lin).duration_(duration);  // Why is duration adjustment necessary?
		//^Env.pairs([frameTimes, rmsData].flop, \lin);
	}


	//return an array of envelopes to represent each onsets.
	getActiveEnv {
		var envArray = [];
		rmsDataBySection.do{|thisSection, sectionIndex|
			var activeFrameTimes;
			var activeRmsData;
			activeFrameTimes = frameTimes[startIndex[sectionIndex]..endIndex[sectionIndex]] - frameTimes[startIndex[sectionIndex]];
			activeRmsData = rmsData[startIndex[sectionIndex]..endIndex[sectionIndex]];
			envArray = envArray.add(Env.pairs([activeFrameTimes, activeRmsData].flop, \lin));
		};
		activeEnv = envArray;
	}

	plot {
		this.buffer.plot;
	}

}
