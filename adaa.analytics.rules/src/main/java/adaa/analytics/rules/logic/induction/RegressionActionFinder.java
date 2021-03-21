package adaa.analytics.rules.logic.induction;

import adaa.analytics.rules.logic.representation.*;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import org.apache.commons.math.stat.inference.TTest;
import org.apache.commons.math.stat.inference.TTestImpl;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RegressionActionFinder extends ActionFinder {

    public RegressionActionFinder (ActionInductionParameters params) { super(new ActionInductionParameters(params)); }

  List<ElementaryCondition> getAllNominalConditions(Attribute attribute) {
        List<ElementaryCondition> ret = new ArrayList<>(attribute.getMapping().size());
        //consider all values
        for (int i = 0; i < attribute.getMapping().size(); i++) {

            ElementaryCondition proposal = new ElementaryCondition(attribute.getName(), new SingletonSet((double)i, attribute.getMapping().getValues()));
            ret.add(proposal);
        }
        return ret;
    }

    List<ElementaryCondition> getAllNumericalConditions(Attribute attribute, ExampleSet trainSet, Set<Integer> coveredByRule) {
        Map<Double, List<Integer>> values2ids = new TreeMap<Double, List<Integer>>();

        // get all distinctive values of attribute
        for (int id : coveredByRule) {
            Example ex = trainSet.getExample(id);
            double val = ex.getValue(attribute);

            if (!values2ids.containsKey(val)) {
                values2ids.put(val, new ArrayList<Integer>());
            }
            values2ids.get(val).add(id);
        }

        Double [] keys = values2ids.keySet().toArray(new Double[values2ids.size()]);
        ArrayList toBeChecked = new ArrayList<>(keys.length * 2);
        // check all possible midpoints
        for (int keyId = 0; keyId < keys.length - 1; ++keyId) {
            double key = keys[keyId];
            double next = keys[keyId + 1];
            double midpoint = (key + next) / 2;

            // evaluate left-side condition a < v
            ElementaryCondition candidate = new ElementaryCondition(attribute.getName(), Interval.create_le(midpoint));
            toBeChecked.add(candidate);

            // evaluate right-side condition v <= a
            candidate = new ElementaryCondition(attribute.getName(), Interval.create_geq(midpoint));
            toBeChecked.add(candidate);
        }

        return toBeChecked;
    }


    @Override
    protected ElementaryCondition induceCondition(Rule rule, ExampleSet trainSet, Set<Integer> uncoveredByRuleset, Set<Integer> coveredByRule, Set<Attribute> allowedAttributes, Object... extraParams) {

        if (allowedAttributes.size() == 0)
            return null;

        RegressionActionRule rRule = (RegressionActionRule)rule;

        if (rRule == null) throw new RuntimeException("RegressionFinder cannot extend non regression rule!");

        RegressionRule sourceRule = (RegressionRule)rRule.getLeftRule();
        RegressionRule targetRule = (RegressionRule)rRule.getRightRule();

        RegressionFinder regressionFinder = new RegressionFinder(this.params);
        ElementaryCondition bestForSource = regressionFinder.induceCondition(sourceRule, trainSet, uncoveredByRuleset, coveredByRule, allowedAttributes, extraParams);

        Attribute bestAttr = null;
        if (bestForSource != null) {
            bestAttr = trainSet.getAttributes().get(bestForSource.getAttribute());
            if (bestAttr.isNominal()) {
                allowedAttributes.remove(bestAttr);
            }
        }

        if (bestAttr == null) {
            return null;
        }

        //now let's find counter condition that assures best seperation!
        List<ElementaryCondition> toBeCheckedForTarget;

        //tak co by zwrocilo indeksy
        Covering coveredByTargetRule = targetRule.covers(trainSet);

        sourceRule.getPremise().addSubcondition(bestForSource);

        Covering sourceRuleCovering = sourceRule.covers(trainSet);
        sourceRule.setCoveringInformation(sourceRuleCovering);
        double[] sourceValues = new double[sourceRuleCovering.negatives.size()];

        List<Double> l = sourceRuleCovering.negatives.stream()
                .map(x -> trainSet.getExample(x).getLabel())
                .collect(Collectors.toList());

        for (int i = 0; i < l.size(); i++) {
            sourceValues[i] = l.get(i);
        }


        if (bestAttr.isNominal()) {
            toBeCheckedForTarget = getAllNominalConditions(bestAttr);
        } else{
            toBeCheckedForTarget = getAllNumericalConditions(bestAttr, trainSet, coveredByTargetRule.negatives);
        }


        double prevTestStatisticValue = 0.0;
        ElementaryCondition bestCounterCondition = null;

        for (ElementaryCondition candidate : toBeCheckedForTarget) {
            CompoundCondition cc = new CompoundCondition();
            cc.getSubconditions().addAll(targetRule.getPremise().getSubconditions());
            RegressionRule testRule = new RegressionRule(cc, targetRule.getConsequence());

            //extend with proposed condition
            testRule.getPremise().addSubcondition(candidate);
            //test it
            Covering cov = testRule.covers(trainSet);
            testRule.setCoveringInformation(cov);

            if (testRule.getConsequence().getValueSet().intersects(sourceRule.getConsequence().getValueSet())) {
                continue;
            }

            double testStatisticValue = testRule.getConsequenceValue();

            /*
            //
            // test t studenta - musi byc maksymalizowana wartosc bezwzlgledna statystyki testowej
            // aby ja wyliczyc potrzebne info o licznosci przykladow po obu stronach

            TTest t = new TTestImpl();
            double[] targetValues = new double[cov.negatives.size()];

            l = cov.negatives.stream()
                    .map(x -> trainSet.getExample(x).getLabel())
                    .collect(Collectors.toList());

            for (int i = 0; i < l.size(); i++) {
                targetValues[i] = l.get(i);
            }

            if (targetValues.length < 2 || sourceValues.length < 2 ) continue;

            //test obustronny
            double testStatisticValue = t.t(sourceValues, targetValues);

            // MOŻNA zrobić testy jednostronne i wybierać czy wartość ma być większa czy mniejsza
            //wtedy np. walidujemy najpierw średnie z populacji
            //a alfa * 2... tak przynajmniej mówi manual do apache commons

            */
            if (Math.abs(testStatisticValue) > Math.abs(prevTestStatisticValue)) {
                prevTestStatisticValue = testStatisticValue;
                bestCounterCondition = candidate;
            }
        }

        return new Action(bestForSource, bestCounterCondition);
    }
}
