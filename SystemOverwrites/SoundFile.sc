+ SoundFile {

	//Split a multichannel sound file into several mono sound files.
	//The splitted mono files will be stored in the same file folder and same file type
	//with the filename attached with channel number such as "filename_1.wav" and "filename_2.wav".
	split {
		var rawArray = FloatArray.newClear(this.numFrames*this.numChannels);
		var path = PathName(this.path);
		this.readData(rawArray);
		rawArray = rawArray.clump(this.numChannels).flop; //[[channel 1], [channel 2], ...]
		rawArray.do{|chanArray, index|
			var file = SoundFile.new.headerFormat_(this.headerFormat).sampleFormat_(this.sampleFormat).numChannels_(1);
			file.openWrite(path.pathOnly +/+ path.fileNameWithoutExtension ++ "_" ++ (index+1).asString ++ "." ++ path.extension);
			file.writeData(chanArray.flatAsFloatArray);
			file.close;
		}
	}

}
