package adaa.analytics.rules.logic.actions;

import adaa.analytics.rules.logic.quality.ClassificationMeasure;
import adaa.analytics.rules.logic.representation.Logger;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Stream;

public class OptimizedActionMetaTable extends ActionMetaTable {

    private Map<String, Set<MetaValue>> metaValuesByAttributeLocal;
    private final Map<String, Set<MetaValue>> metaValuesByAttribute = new HashMap<>();
    private List<String> stableAttributes = new ArrayList<>();

    public OptimizedActionMetaTable(ActionRangeDistribution distribution, ClassificationMeasure qualityFunction, List<String> stableAttributes){
        super(distribution, qualityFunction);
        this.stableAttributes = stableAttributes;
        metaValuesList.forEach(metaValues -> {
            Set<MetaValue> toAdd = new HashSet<>();
            toAdd.addAll(metaValues);
            metaValuesByAttribute.put(metaValues.stream().findAny().get().getAttribute(), toAdd);
        });
    }

    @Override
    public List<MetaAnalysisResult> analyze(Example ex, int fromClass, int toClass, boolean pruningEnabled, boolean generateMultipleRecommendations) {

        List<MetaAnalysisResult> ret = new ArrayList<>();
        MetaExample primeMe = new MetaExample();
        HashSet<String> allowedAttributes = new HashSet<>();

        //Deep copy - it is enough that we copy the references of MetaValues, as we do not modify the meta-values later on
        metaValuesByAttributeLocal = new HashMap<>();
        for (Map.Entry<String, Set<MetaValue>> entry : metaValuesByAttribute.entrySet()) {
            metaValuesByAttributeLocal.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        List<MetaValue> proposedPrime = new CopyOnWriteArrayList<>();

        metaValuesByAttributeLocal
                .entrySet()
                .parallelStream()
                .forEach(metas -> metas.getValue().stream().filter(mv -> mv.contains(ex)).findFirst().ifPresent(proposedPrime::add));
        //can't add directly to primeMe, because it is not thread safe
        //TODO consider making it thread safe
        proposedPrime.forEach(primeMe::add);

        if (primeMe.getAllValues().isEmpty()) {
            throw new RuntimeException("The example ex was not covered by any metaexample");
        }


        metaValuesByAttributeLocal.forEach((String x, Set<MetaValue> y) -> y.removeIf(z -> z.contains(ex)));

        while(true) {
            Iterator<Attribute> it = trainSet.getAttributes().allAttributes();

            it.forEachRemaining(x -> {
                        if (x.equals(trainSet.getAttributes().getLabel())) {
                            return;
                        }
                        if (stableAttributes.contains(x.getName())){
                            return;
                        }
                        allowedAttributes.add(x.getName());
                    }
            );

            MetaExample contraMe = new MetaExample();
            double bestQ = rankMetaPremise(contraMe, fromClass, toClass, trainSet);
            boolean grown = true;
            boolean pruned = pruningEnabled;
            while (grown) {
                MetaValue best = getBestMetaValue(allowedAttributes, contraMe, trainSet, fromClass, toClass);

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



            while (pruned) {
                MetaValue candidateToRemoval = null;
                double currQ = 0.0;
                MetaExample currentValues = new MetaExample(contraMe);
                for (MetaValue mv : currentValues.getAllValues()) {
                    contraMe.remove(mv);

                    double q = rankMetaPremise(contraMe, fromClass, toClass, trainSet);

                    if (q >= currQ) {
                        currQ = q;
                        candidateToRemoval = mv;
                    }

                    contraMe.add(mv);
                }

                if (candidateToRemoval != null && currQ >= bestQ) {
                    contraMe.remove(candidateToRemoval);
                    bestQ = currQ;
                } else {
                    pruned = false;
                }
            }

            if (contraMe.getSize() == 0) {

                break;
            } else {

                for (MetaValue mv :  contraMe.getAllValues()) {
                    metaValuesByAttributeLocal.get(mv.getAttribute()).remove(mv);
                }
            }

            MetaAnalysisResult result = new MetaAnalysisResult(ex, primeMe, contraMe, fromClass, toClass, trainSet);
            ret.add(result);

            if (!generateMultipleRecommendations)
                break;
        }
        return ret;
    }


    private MetaValue getBestMetaValue(Set<String> allowedAttributes, MetaExample contra, ExampleSet examples, int fromClass, int toClass) {

        double best_quality = Double.NEGATIVE_INFINITY;

        Stream<Map.Entry<String, Set<MetaValue>>> allowed =
                metaValuesByAttributeLocal
                        .entrySet()
                        .stream()
                        .filter(x -> allowedAttributes.contains(x.getKey()));
        String[] attributes = allowedAttributes.toArray(new String[0]);
        Map<String, Integer> atrToInt = new HashMap<>();
        CopyOnWriteArrayList<MetaValue> candidates = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<Double> qualities = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attributes.length; i++) {

            atrToInt.put(attributes[i], i);
            qualities.add(best_quality);
            candidates.add(null);
        }



        allowed
                .parallel()
                .forEach(
                        (x) -> {
                            MetaExample localContra = new MetaExample(contra);
                            Integer index = atrToInt.get(x.getKey());
                            double Q = qualities.get(index);

                            for (MetaValue cand : x.getValue()) {

                                localContra.add(cand);

                                double q = rankMetaPremise(localContra, fromClass, toClass, examples);

                                localContra.remove(cand);

                                if (q >= Q) {

                                    candidates.set(index, cand);
                                    qualities.set(index, q);
                                    Q = q;
                                }
                            }
                        }
                );

        if (qualities.isEmpty())
            return null;
        Optional<Double> max = qualities.stream().max(Double::compareTo);
        int idx = qualities.indexOf(max.get());
        return candidates.get(idx);
    }
}
