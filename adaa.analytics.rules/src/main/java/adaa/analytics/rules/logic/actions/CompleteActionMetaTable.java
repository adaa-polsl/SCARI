package adaa.analytics.rules.logic.actions;

import adaa.analytics.rules.logic.quality.ClassificationMeasure;
import adaa.analytics.rules.logic.representation.IntegerBitSet;
import adaa.analytics.rules.logic.representation.Logger;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class CompleteActionMetaTable extends ActionMetaTable {

	private Set<MetaExample> metaExamples;

	public CompleteActionMetaTable(ActionRangeDistribution distribution, ClassificationMeasure qualityFunction) {
		super(distribution, qualityFunction);
		metaExamples = cartesianProduct(distribution.getMetaValuesByAttribute());
	}

	//For given example returns respective meta-example and contre-meta-example of opposite class
	@Override
	public List<MetaAnalysisResult> analyze(Example ex, int fromClass, int toClass, boolean pruningEnabled, boolean generateMultipleRecommendations) {
		MetaExample contraMe = new MetaExample();
		MetaExample primeMe = new MetaExample();
		List<MetaAnalysisResult> ret = new ArrayList<>();

		List<MetaValue> proposedPrime = new CopyOnWriteArrayList<>();

		metaValuesList
				.parallelStream()
				.forEach(metas -> metas.stream().filter(mv -> mv.contains(ex)).findFirst().ifPresent(proposedPrime::add));

		proposedPrime.forEach(primeMe::add);

		if (primeMe.getAllValues().isEmpty()) {
			throw new RuntimeException("The example ex was not covered by any metaexample");
		}
		
		Set<MetaExample> toSearch = new HashSet<>(metaExamples);
		toSearch.remove(primeMe);
		//don't need to bother with examples not supported by any rule ???
	//	toSearch.removeIf(x -> x.getCountOfSupportingRules() == 0);
		

		
		HashSet<String> allowedAttributes = new HashSet<String>();
		
		Iterator<Attribute> it = trainSet.getAttributes().allAttributes();
		
		it.forEachRemaining(x -> {
				if (x.equals(trainSet.getAttributes().getLabel())) { return;}
				allowedAttributes.add(x.getName());
			}
		);
		Logger.log("Looking for recommendation for example: " + ex + "\r\n", Level.FINE);
		
		
		boolean grown = true;
		double bestQ = rankMetaPremise(contraMe, fromClass, toClass, trainSet);
		Logger.log("Initial contre-meta-example is " + contraMe + " at quality " + bestQ + "\r\n", Level.FINE);
		while (grown) {
			
			MetaValue best = getBestMetaValue(allowedAttributes,
					contraMe,
					toSearch,
					ex, trainSet,
					fromClass, toClass);
		
			if (best == null) {
				break;
			}
			contraMe.add(best);
			double currQ = rankMetaPremise(contraMe, fromClass, toClass, trainSet);
			
			if (currQ >= bestQ) {
				allowedAttributes.remove(best.getAttribute());
				
				bestQ = currQ;
				grown = true;
				Logger.log("Found best meta-value: " + best + " at quality " + bestQ + "\r\n", Level.FINE);
			} else {
				contraMe.remove(best);
				grown = false;
			}
			if (Double.compare(currQ, 1.0) == 0) {
				grown = false;
			}
		}
		/////pruning
		
		Set<String> attributes = new HashSet<>(contraMe.getAttributeNames());
		boolean pruned = pruningEnabled;

		while (pruned) {
			MetaValue candidateToRemoval = null;
			double currQ = 0.0;
			for(String atr : attributes) {

				MetaValue cand = contraMe.get(atr);
				if (cand == null) {
					continue;
				}

				contraMe.remove(cand);

				double q = rankMetaPremise(contraMe, fromClass, toClass, trainSet);

				if (q >= currQ) {
					currQ = q;
					candidateToRemoval = cand;
				}

				contraMe.add(cand);
			}

			if (candidateToRemoval != null && currQ >= bestQ) {
				contraMe.remove(candidateToRemoval);
				bestQ = currQ;
			} else {
				pruned = false;
			}

		}
		ret.add(new MetaAnalysisResult(ex, primeMe, contraMe, fromClass, toClass, trainSet));


		return ret;
	}


	private MetaValue getBestMetaValue(Set<String> allowedAttributes,
									   MetaExample contra,
									   Set<MetaExample> metas,
									   Example example,
									   ExampleSet examples,
									   int fromClass,
									   int toClass) {
		
		//jaki zbi�r bierzemy do oceny nowego meta-warunku?
		//powinien on chyba by� ograniczony tlyko do przyk�ad�w, kt�re s� ju� pokrywane przez 
		//rozwijan� w�a�nie kontra-metaprzes�ank� (poniewa� w innym wypadku to b�dzie bez sensu,
		//wk�ad do jako�ci potencjalnie b�d� mie� rozdzielne grupy przyk�ad�w
		//w efekcie precyzja mo�e nie wzrasta�
		//jak i gdzie to filtrowa� dok�adnie ?
		//trzeba to rozrysowa� i rozpisa� i zastanowi� si� na sucho.
		
		//trzeba du�o przerob� niestety, bo metaexample musz� sta� si� bardziej jak regu�y (jesli chodzi o liczenie pokrycia)
		MetaValue candidate = null;
		double Q = Double.NEGATIVE_INFINITY;		
		
		for (MetaExample meta : metas) {
			
			for (String attribute : allowedAttributes) {
				
				MetaValue cand = meta.get(attribute);
				
				if (cand == null || cand.contains(example)) {
					continue;
				}
				if (cand.equals(candidate)) {
					continue;
				}

				contra.add(cand);
				
				double quality = rankMetaPremise(contra, fromClass, toClass, examples);
				
	
				Logger.log("Quality " + quality + " recorded for metaexample " + meta + "\r\n", Level.FINEST);
				if (quality >= Q) {
					Q = quality;
					candidate = cand;
				}
				
				contra.remove(cand);
			}
		}
	
		return candidate;
	}


	private Set<MetaExample> cartesianProduct(List<Set<MetaValue>> sets) {
		if (sets.size() < 2)
			throw new IllegalArgumentException(
					"Can't have a product of fewer than two sets (got " +
							sets.size() + ")");

		return _cartesianProduct(0, sets);
	}

	private Set<MetaExample> _cartesianProduct(int index, List<Set<MetaValue>> sets) {
		Set<MetaExample> ret = new HashSet<>();
		if (index == sets.size()) {
			ret.add(new MetaExample());
		} else {
			for (MetaValue metaValue : sets.get(index)) {
				for (MetaExample metaExample : _cartesianProduct(index+1, sets)) {
					metaExample.add(metaValue);
					ret.add(metaExample);
				}
			}
		}
		return ret;
	}

}
