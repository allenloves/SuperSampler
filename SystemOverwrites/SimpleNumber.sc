+ SimpleNumber {

	noGreaterThan { arg num;
		if(this >= num, {^num}, {^this});
	}

	noLessThan { arg num;
		if(this <= num, {^num}, {^this});
	}

}

