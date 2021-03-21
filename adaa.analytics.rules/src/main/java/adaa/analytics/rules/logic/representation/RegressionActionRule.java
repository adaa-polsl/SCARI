package adaa.analytics.rules.logic.representation;

import adaa.analytics.rules.logic.induction.ActionCovering;
import adaa.analytics.rules.logic.induction.ContingencyTable;
import adaa.analytics.rules.logic.induction.Covering;
import adaa.analytics.rules.logic.quality.ClassificationMeasure;
import adaa.analytics.rules.logic.quality.Hypergeometric;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.tools.container.Pair;

public class RegressionActionRule extends ActionRule {

    double testStatisticValue;

    public RegressionActionRule() {
        super();
    }

    public RegressionActionRule(CompoundCondition premise, Action conclusion) {
        super(premise, conclusion);
    }

    public void calculatePValue(ExampleSet trainSet, ClassificationMeasure measure){
        Hypergeometric test = new Hypergeometric();

        Rule left  = this.getLeftRule();
        Rule right = this.getRightRule();

        ContingencyTable ctLeft = left.covers(trainSet);
        ContingencyTable ctRight = right.covers(trainSet);

        Pair<Double, Double> statAndPValueLeft = test.calculate(ctLeft);
        Pair<Double, Double> statAndPValueRight = test.calculate(ctRight);

        this.weight = measure.calculate(ctLeft);
        this.pvalue = statAndPValueLeft.getSecond();
        this.weightRight = measure.calculate(ctRight);
        this.pValueRight = statAndPValueRight.getSecond();
    }

    public Rule getLeftRule() {

        CompoundCondition premise = new CompoundCondition();
        for (ConditionBase a : this.getPremise().getSubconditions()) {
            if (a.isDisabled()) {
                continue;
            }
            Action ac = (Action)a;
            if (ac.getLeftValue() != null) {
                premise.addSubcondition(new ElementaryCondition(ac.getAttribute(), ac.getLeftValue()));
            }
        }

        Rule r = new RegressionRule(premise, new ElementaryCondition(actionConsequence.getAttribute(), actionConsequence.getLeftValue()));
        r.setWeighted_P(this.getWeighted_P());
        r.setWeighted_N(this.getWeighted_N());

        r.setCoveredPositives(this.getCoveredPositives());
        r.setCoveredNegatives(this.getCoveredNegatives());
        r.setPValue(this.pvalue);
        r.setWeight(this.weight);
        return r;
    }

    public Rule getRightRule() {

        CompoundCondition premise = new CompoundCondition();
        for (ConditionBase a : this.getPremise().getSubconditions()) {
            if (a.isDisabled()){
                continue;
            }
            Action ac = (Action)a;
            if (ac.getRightValue() != null && !ac.getActionNil()) {
                premise.addSubcondition(new ElementaryCondition(ac.getAttribute(), ac.getRightValue()));
            }
        }

        Rule r = new RegressionRule(premise, new ElementaryCondition(actionConsequence.getAttribute(), actionConsequence.getRightValue()));
        r.setWeighted_P(this.getWeighted_N());
        r.setWeighted_N(this.getWeighted_P());

        r.setWeight(this.weightRight);
        r.setPValue(this.pValueRight);
        return r;
    }

    @Override
    public void setCoveringInformation(Covering cov){
        ElementaryCondition cons = this.getConsequence();
        coveringInformation = (ActionCovering)cov;
        if (cons instanceof Action) {
            Action conclusion = (Action)cons;
            ((SingletonSet)conclusion.getLeftValue()).setValue(cov.mean_y);
            ((SingletonSet)conclusion.getRightValue()).setValue(((ActionCovering) cov).mean_y_right);
        }
    }

    @Override
    public String toString() {
        double sourceVal = ((SingletonSet)actionConsequence.getLeftValue()).value;
        double targetVal = ((SingletonSet)actionConsequence.getRightValue()).value;
        double loSource = sourceVal - coveringInformation.stddev_y;
        double hiSource = sourceVal + coveringInformation.stddev_y;
        double loTarget = targetVal - coveringInformation.stddev_y_right;
        double hiTarget = targetVal + coveringInformation.stddev_y_right;
        String s = "IF " + premise.toString() + " THEN " + actionConsequence.toString() + " [" + DoubleFormatter.format(loSource) + "," + DoubleFormatter.format(hiSource) + "]"
                + " [" + DoubleFormatter.format(loTarget) + "," + DoubleFormatter.format(hiTarget) + "]";
        return s;
    }

    @Override
    public Covering covers(ExampleSet set) {
        ActionCovering aCov = new ActionCovering();
        Rule source = this.getLeftRule();
        Rule target = this.getRightRule();

        Covering sCovering = source.covers(set);
        Covering tCovering = target.covers(set);

        aCov.weighted_p = sCovering.weighted_p;
        aCov.weighted_n = sCovering.weighted_n;
        this.weighted_P = sCovering.weighted_P;
        aCov.weighted_P = sCovering.weighted_P;
        this.weighted_N = sCovering.weighted_N;
        aCov.weighted_N = sCovering.weighted_N;
        aCov.positives.addAll(sCovering.positives);
        aCov.negatives.addAll(sCovering.negatives);
        aCov.mean_y = sCovering.mean_y;
        aCov.median_y = sCovering.median_y;
        aCov.stddev_y = sCovering.stddev_y;


        aCov.weighted_pRight = tCovering.weighted_p;
        aCov.weighted_nRight = tCovering.weighted_n;
        aCov.weighted_P_right = tCovering.weighted_P;
        this.weighted_P_right = tCovering.weighted_P;
        aCov.weighted_N_right = tCovering.weighted_N;
        this.weighted_N_right = tCovering.weighted_N;
        aCov.mean_y_right = tCovering.mean_y;
        aCov.median_y_right = tCovering.median_y;
        aCov.stddev_y_right = tCovering.stddev_y;

        return aCov;
    }


}
