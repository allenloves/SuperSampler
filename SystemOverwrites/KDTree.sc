+ KDTree {
	//Search within a circle (/hypersphere) defined by a point and a radius, and sort the outcome by it's distance from the point by decending order
	radiusSort { |point, radius=1|
		var outcome;
		outcome = this.radiusSearch(point, radius);
		outcome = outcome.sort({|a, b| a.location.distance(point) > b.location.distance(point)});
		^outcome;
	}

	//calculate distance from a node to a point
	distance {|other|
		if(other.isArray)
		{^this.location.distance(other)}
		{
			if(other.class == KDTree)
			{^this.location.distance(other.location)}
			{Error("other node point should be an Array or KDTree").throw};
		};
	}
}