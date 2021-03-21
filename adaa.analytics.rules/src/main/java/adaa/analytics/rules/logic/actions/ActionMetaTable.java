package adaa.analytics.rules.logic.actions;

import adaa.analytics.rules.logic.induction.Covering;
import adaa.analytics.rules.logic.quality.ClassificationMeasure;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class ActionMetaTable {

    protected ExampleSet trainSet;

    List<Set<MetaValue>> metaValuesList;
    ClassificationMeasure qualityMeasure;

    public ActionMetaTable(@NotNull ActionRangeDistribution distribution, ClassificationMeasure qualityFunction) {
        trainSet = (ExampleSet) distribution.set.clone();
        metaValuesList = distribution.getMetaValuesByAttribute();
        qualityMeasure = qualityFunction;
    }

    //For given example returns respective meta-example and contre-meta-example of opposite class
    public abstract List<MetaAnalysisResult> analyze(Example ex, int fromClass, int toClass, boolean pruningEnabled, boolean generateMultipleRecommendations);

    //fill in the fittness function (quality measure for hill climbing)
    //maybe should be configurable by user
    double rankMetaPremise(MetaExample metaPremise, int fromClass,
                           int toClass, ExampleSet examples) {
        Set<Integer> pos = new HashSet<>();
        Set<Integer> neg = new HashSet<>();
        Covering covering = metaPremise.getCoverageForClass(examples, toClass, pos, neg);

        if (covering.weighted_p < 1.0) {
            return 0.0;}

        double measure = qualityMeasure.calculate(covering);
        return Double.isNaN(measure) ? Double.MIN_VALUE : measure;
    }
}
