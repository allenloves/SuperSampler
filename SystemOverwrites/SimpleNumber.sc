+ SimpleNumber {

	noGreaterThan { arg num;
		if(this >= num, {^num}, {^this});
	}

	noLessThan { arg num;
		if(this <= num, {^num}, {^this});
	}


	//Taken from the SuperCollider forum
	midirq {
    var x = 2**(this / 24.0);
    ^(x*x - 1) / x;
	}

	at {
		^this;
	}

	ifStatic {
		^this;
	}

    envLowHigh {
        ^this;
    }

	envLevels {
		^this;
	}

	envTimes {
		^0;
	}

    validateParam {
        arg func, ifFalse = nil;
        if(func.value(this).not)
            {^ifFalse};
        ^this;
    }
}
