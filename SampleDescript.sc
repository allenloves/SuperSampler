//Sample Descripter By Allen Wu
//Extra classes requirements: wslib Quark.
//SampleAnalyse is using SCMIR.  Make sure you have SCMIR installed in your SuperCollider extensions.  http://composerprogrammer.com/code.html



SampleDescript{
	//General information
	var <file;  //An SCMIRAudioFile.
	var <filename;
	var <sampleRate;
	var <bufferServer;
	var <buffer;
	var <activeBuffer;  //Array of buffers with each section (is there a way to play one part of buffer without making a copy?)
	var <keynumFromFileName;  //get key number from file name, nil if there is none
	var <keynumFromPitchFound;  //get key number from pitch detection. not necessarily an integer.
	var <frameTimes;  //Time stamp of each frame
	var <mirDataByFeatures; //[[RMS], [Pitch], [hasPitch], [centroid], [SensoryDissonance], [SpecFlatness]]
	var <rmsData;
	var <rmsDataBySection;
	var <centroidData;
	var <dissonanceData;

	//****** global description *****
	// time domain
	var <duration;  //Total duration of sound file.
	var <sectionBreakPoint;  //Trough (lowest) point in between onsets.
	var <globalPeakIndex;
	var <globalPeakAmp;  //Amplitude at RMS peak point.
	var <globalPeakTime;  //Time on the peak point.


	// **** local information by Onsets *****
	// Temporal information
	var <>keynum;
	var <activeDuration;
	var <activeRMSData;
	var <onsetTime;
	var <onsetIndex; //frame index at onset
	var <startIndex;
	var <startTime;  //begging time of start frame
	var <endIndex;
	var <endTime;
	var <peakIndex;
	var <peakTime;  //global time point of each peak
	var <peakAmp;
	var <attackDur;  // Attack Time
	var <releaseDur;  // Release Time
	var <temporalCentroid; // Shorter value indicates percussive sounds, longer value indicates sustained sounds.

	// Frequency information
	var <pitchData; //[[pitch, hasPitch]....]
	var <activeCentroidData;  //frequency centroid
	var <activeDissonanceData;  // higher value indicates dissonance

	var <activeSpecFlatness;  // 0-1, 0 is single sinesoid wave, 1 is white noise.  This indicates the degree of noiseness.



	//parameters
	//File Name, Normalize, Start Tine, Duration, Threshold for attack point, Threshold for end point, Threshold for onset, Time threshold for onset grouping.
	*new {arg filename, normtype=0, start=0, dur=0, startThresh=0.01, endThresh=0.01, onsetThresh=0.5, groupingThresh = 0.32, filenameAsNote = true, loadToBuffer = true, server = Server.default, action;

		^super.new.init(filename, normtype, start, dur, startThresh, endThresh, onsetThresh, groupingThresh, filenameAsNote, loadToBuffer, server, action);
	}

	init {|fileName, normtype, start, dur, startThresh, endThresh, onsetThresh, groupingThresh, filenameAsNote, loadToBuffer, server, action|
		var cond = Condition.new(false);
		//SCMIR.setFrameHop(512);
		server.postln;
		bufferServer = server;

		/*
		//Write Buffer into a file if the input is a buffer.
		if(fileName.class == Buffer)
		{//if input is a buffer, save the buffer into a file before loading to SCMIR
			buffer = fileName;
			if(buffer.path != nil)
			{
				if(PathName(buffer.path).extension != "")
				{filename = buffer.path;}//use buffer path provided already
				{//give an extension of buffer path and safe to a file
					filename = Platform.defaultTempDir +/+ PathName(buffer.path).fileName ++ ".aiff";
					fileName.write(filename, completionMessage: {cond.test=true;cond.signal;});
				};
			}
			{//provide a tempbuffer filename and save to a file.
				filename = Platform.defaultTempDir +/+ "tempbuffer" +/+ UniqueID +/+ ".aiff";
				fileName.write(filename, completionMessage: {cond.test=true;cond.signal;});
			};
		}
		{filename = fileName; cond.test=true; cond.signal;};//if it is not a buffer
		*/

		filename = fileName;
		file = SCMIRAudioFile(fileName, [\RMS, [\Tartini, 0], \SpecCentroid, \SensoryDissonance, \SpecFlatness], normtype, start, dur);
			file.extractFeatures(false);
			file.extractOnsets();
			//get data from SCMIR
			duration = file.duration;
			mirDataByFeatures = file.featuredata.clump(file.numfeatures).flop;
			rmsData = mirDataByFeatures[0];
			pitchData = [mirDataByFeatures[1], mirDataByFeatures[2]].flop;
			centroidData = mirDataByFeatures[3];
			dissonanceData = mirDataByFeatures[4];
			globalPeakIndex = rmsData.maxIndex;
			globalPeakAmp = rmsData.maxItem;
			frameTimes = file.frameTimes;
			globalPeakTime = frameTimes.at(globalPeakIndex);
			this.getOnsetTime(groupingThresh);
			this.getOnsetIndex;
			this.findPeaksByOnsets;
			this.findBreakPointByOnset;
			this.sectionRmsDataByOnset;
		this.arEnv(startThresh, endThresh);
			this.getActiveData;
		this.getKeynum(filenameAsNote);
		if(loadToBuffer){
			server.waitForBoot{
				this.loadToBuffer(bufferServer)};
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
		dissonanceData = nil;
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
		activeDissonanceData = nil;
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


	loadToBuffer{arg server = Server.default, action;
		var buf, startSample = 0, durSample;
		var cond = Condition(false);
		bufferServer = server;
		buffer.free;
		activeBuffer.do({|buffer| buffer.free;});
		activeBuffer = [];
		"Loading soundfile into Buffer".postln;
		server.waitForBoot{
			Routine.run{
				buffer = Buffer.read(server, filename, action: {cond.test = true; cond.signal;});
				cond.wait;
				sampleRate = buffer.sampleRate;
				//"master buffer loaded".postln;


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

	getKeynum {arg filenameAsNote, pitchShift = 0;
		var str=PathName(filename).fileNameWithoutExtension;
		var l=str.size-1;
		var i=l, j, c;
		var deg=0, acc=0, oct=0;
		var foundPitches = [];
		keynumFromFileName = nil;
		keynumFromPitchFound = [];
		keynum = [];

		//if there is a key number or note name at the end of file name, retrive the key number
		while ({i>=0 && "0123456789".includes(str.at(i))},{i=i-1});
		if (i<l) { // had digit at end, i now before all digits
			if (l - i >= 2){// had more than 2 digits at end
				if(l = i <=3){//had no more than 3 digits, make it key number;
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
		// else, no digit at end, use pitch material for keynumber
		{keynumFromFileName = nil};

		//get keynum from pitch material, keynum is not necessarily an integer.
		//The pitch is determined by the pitch data at the peak frame, if it has pitch.
		//If pitch is not found at the peak frame, take the first frame that has pitch.
		//Return nil if there is no pitch after 20 frames until the end of the section.
		peakIndex.do({|thisPeakIndex, sectionIndex|
			var pitch, hasPitch;
			var pitchCollection = [];

			if(endTime[sectionIndex]-peakTime[sectionIndex] <= 0.5)
			{pitch = pitchData[thisPeakIndex..endIndex[sectionIndex]].flop[0];
				hasPitch = pitchData[thisPeakIndex..endIndex[sectionIndex]].flop[1]}
			{pitch = pitchData[thisPeakIndex..thisPeakIndex + 20].flop[0];
				hasPitch = pitchData[thisPeakIndex..thisPeakIndex + 20].flop[1];};
			//find collections of data with pitch
			pitch.do{|thisPitch, index|
				if(hasPitch[index] >= 0.9)
				{//pitchCollection = pitchCollection.add(69 + (12 * log2(freq/440)))
				pitchCollection = pitchCollection.add(thisPitch)
				};
			};
			//if no pitch data is collected, than use centroid data for pitch
			//if there are pitch data collected, get the most occurred data for keynum
			if(pitchCollection.size == 0 || pitchCollection.occurrencesArray(0.5).maxItem == 1)
			{keynumFromPitchFound = keynumFromPitchFound.add(centroidData[thisPeakIndex].explin(20, 20000, 28, 103) - 12); // an octave lower to map to the range of my keyboard :p
			"no pitch detected, using centorid".postln;}
			{keynumFromPitchFound = keynumFromPitchFound.add(pitchCollection.mostOccurredItems(0.5).mean)};
		});

		//Now determine which answer to use
		if(filenameAsNote && keynumFromFileName.isNil.not)
		{keynum = Array.fill(peakIndex.size, keynumFromFileName) + pitchShift;}
		{keynum = keynumFromPitchFound + pitchShift;}

	}

	//get onset time, if an onset is too close to previous one, it will be abandoned
	getOnsetTime{|groupingThresh = 0.32|
		var onsets = [file.onsetdata[0]];
		file.onsetdata.doAdjacentPairs({|thisOnset, nextOnset|
			//filter onsets too close
			if((nextOnset - thisOnset) > groupingThresh, {onsets = onsets.add(nextOnset)});
		});
		onsetTime = onsets;
	}


	//get frame index at the onset time
	getOnsetIndex{
		var onsetIndexTemp = [];
		var franeGrid = file.frameStartTimes;
		onsetTime.do{|thisOnsetTime, index|
			onsetIndexTemp = onsetIndexTemp.add((file.frameStartTimes.indexOfGreaterThan(thisOnsetTime)) - 1);
		};
		onsetIndex = onsetIndexTemp;
	}

	//find local peaks by onsets
	findPeaksByOnsets{
		var peakArray = [];
		onsetIndex.do{|thisOnset, index|
			var nextOnset = onsetIndex[index + 1];
			peakArray = peakArray.add(rmsData[thisOnset..nextOnset].maxIndex + thisOnset);
		};
		peakIndex = peakArray;
	}


	//Find breakpoint of a sample file by onsets.
	//using lowest point in between local peaks to be section breakpoints.
	findBreakPointByOnset {
		var troughArray = [];
		peakIndex.doAdjacentPairs{|thisPeak, nextPeak|
			troughArray = troughArray.add(rmsData[thisPeak..nextPeak].minIndex + thisPeak);
		};
		sectionBreakPoint = troughArray;
	}

	//Separate rmsData into subsections by breakpoints.
	sectionRmsDataByOnset {
		rmsDataBySection = rmsData.chop(sectionBreakPoint);
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
		peakTime = [];
		peakAmp = [];

		//for each onset section, find peaks
		rmsDataBySection.do{|thisSection, sectionIndex|
			var thisSectionStartIndex = sectionBreakPoint.addFirst(0)[sectionIndex];
			var localPeakFrame = thisSection.maxIndex;
			var localPeakTime = (thisSection.maxIndex + 0.5) * SCMIR.hoptime;
			var localPeakAmp = thisSection.maxItem;

			startAmp = startThresh * localPeakAmp;
			endAmp = endThresh * localPeakAmp;

			peakAmp = peakAmp.add(localPeakAmp);
			peakTime = peakTime.add(localPeakTime + (thisSectionStartIndex * SCMIR.hoptime)) ;

			//search for the first point pass above threshold.
			block{|break|
				thisSection.do({|rmsenergy, index|
					if(rmsenergy >= startAmp ,
						{
							startIndex = startIndex.add(thisSectionStartIndex + index);
							startTime = startTime.add(startIndex.last * SCMIR.hoptime);
							break.value(0);
					})
				})
			};



			//search for the last point pass below threshold.
			block{|break|
				thisSection.reverseDo({|rmsenergy, index|
					if(rmsenergy >= endAmp ,
						{
							endIndex = endIndex.add(sectionBreakPoint.addFirst(0)[sectionIndex] + thisSection.size - index);
							endTime = endTime.add(endIndex.last * SCMIR.hoptime);
							break.value(0);
					})
				})
			};
		};

		attackDur = peakTime - startTime;
		releaseDur = endTime - peakTime;
		activeDuration = endTime - startTime;
	}


	//Separate Datas into subsections by breakpoints.
	//includes:
	//activeRMSData
	//activeCentroid
	//activeDissonanace
	//temporalCentorid
	getActiveData {

		//Datas to retrive
		activeCentroidData = [];
		activeDissonanceData = [];
		temporalCentroid = [];
		activeRMSData = [];

		startIndex.do({|thisStartIndex, sectionIndex|
			var thisRMSData = rmsData[thisStartIndex..endIndex[sectionIndex]];
			var activeTime = frameTimes[thisStartIndex..endIndex[sectionIndex]] - frameTimes[thisStartIndex];

			activeRMSData=activeRMSData.add(thisRMSData);

			activeCentroidData = activeCentroidData.add(centroidData[thisStartIndex..endIndex[sectionIndex]]);

			activeDissonanceData = activeDissonanceData.add(dissonanceData[thisStartIndex..endIndex[sectionIndex]]);

			temporalCentroid = temporalCentroid.add(
				(thisRMSData*activeTime).sum / thisRMSData.sum;
			)
		})
	}



	//play the sound file, using(at) to play each onset.  If (at) is larger than the last onset index, it plays a random onset.
	play {|at = nil, outbus = 0, server|
		var buf, cond = Condition.new(false);
		server = server ? Server.default;
		if(buffer == nil)
		{this.loadToBuffer(server, action: {cond.test = true; cond.signal;})}
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
			{PlayBuf.ar(file.numChannels, buf, doneAction: 2, rate: BufRateScale.kr(buf))}.play(outbus: outbus);
		}
	}


		//return an envelope to represent the whole sound file
	env {
		^Env.pairs([frameTimes, rmsData].flop, \lin);
	}


	//return an array of envelopes to represent each onsets.
	activeEnv {|startThresh=0.1, endThresh=0.01|
		var envArray = [];
		this.arEnv(startThresh, endThresh);
		rmsDataBySection.do{|thisSection, sectionIndex|
			var activeFrameTimes;
			var activeRmsData;
			activeFrameTimes = frameTimes[startIndex[sectionIndex]..endIndex[sectionIndex]] - frameTimes[startIndex[sectionIndex]];
			activeRmsData = rmsData[startIndex[sectionIndex]..endIndex[sectionIndex]];
			envArray = envArray.add(Env.pairs([activeFrameTimes, activeRmsData].flop, \lin));
		};
		^envArray;
	}



}
