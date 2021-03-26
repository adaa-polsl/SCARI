package adaa.analytics.rules.logic.actions;

import adaa.analytics.rules.logic.representation.*;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.jetbrains.annotations.NotNull;
import org.renjin.repackaged.guava.collect.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MetaAnalysisResult {
    public MetaExample primeMetaExample;
    public MetaExample contraMetaExample;
    public Example example;
    private int from;
    private int to;
    private ExampleSet sourceExamples;

    public MetaAnalysisResult(Example ex, MetaExample prime, MetaExample contre, int fromClass, int toClass, ExampleSet set) {
        example = ex;
        primeMetaExample = prime;
        contraMetaExample = contre;
        from = fromClass;
        to = toClass;
        sourceExamples = set;
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        if (this == o) return true;
        if (o.getClass() != getClass()) return false;

        MetaAnalysisResult that = (MetaAnalysisResult)o;

        EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.example, that.example);
        builder.append(this.primeMetaExample, that.primeMetaExample);
        builder.append(this.contraMetaExample, that.contraMetaExample);
        builder.append(this.sourceExamples, that.sourceExamples);
        builder.append(this.from, that.from);
        builder.append(this.to, that.to);
        return builder.isEquals();



    }

    public ActionRule getActionRule() {

        ActionRule rule = new ActionRule();
        rule.setPremise(new CompoundCondition());

        Map<String, ElementaryCondition> premiseLeft = primeMetaExample.toPremise();
        Map<String, ElementaryCondition> premiseRight = contraMetaExample.toPremise();

        //full actions
        Sets.intersection(premiseLeft.keySet(), premiseRight.keySet())
            .stream()
            .map(x -> new Action(premiseLeft.get(x), premiseRight.get(x)))
            .forEach(x -> rule.getPremise().addSubcondition(x));

        //handle cases when right (contra meta example) does not contain meta-value for given attribute
        Sets.difference(premiseLeft.keySet(), premiseRight.keySet())
            .stream()
            .map(x -> {Action a = new Action(premiseLeft.get(x), premiseRight.get(x)); return a;})
            .forEach(x -> rule.getPremise().addSubcondition(x));


        Attribute classAtr = sourceExamples.getAttributes().getLabel();

        IValueSet sourceClass = new SingletonSet((double)from, classAtr.getMapping().getValues());
        if (from == -1.0) {
            sourceClass = new AnyValueSet();
        }
        IValueSet targetClass = new SingletonSet((double)to, classAtr.getMapping().getValues());

        rule.setConsequence(new Action(classAtr.getName(), sourceClass, targetClass));

        return rule;
    }

    public List<Rule> getRuleCoverage(@NotNull Set<Rule> rules) {


        List<Rule> ret = new CopyOnWriteArrayList<>();
        for (Rule rule : rules) {
            if (contraMetaExample.isCoveredBy(rule)) {
                ret.add(rule);
            }
        }

        return ret;
    }
}
