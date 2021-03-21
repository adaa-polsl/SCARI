package experiments;

import adaa.analytics.rules.logic.actions.ActionMetaTable;
import adaa.analytics.rules.logic.actions.ActionRangeDistribution;
import adaa.analytics.rules.logic.actions.MetaAnalysisResult;
import adaa.analytics.rules.logic.actions.OptimizedActionMetaTable;
import adaa.analytics.rules.logic.induction.*;
import adaa.analytics.rules.logic.quality.ClassificationMeasure;
import adaa.analytics.rules.logic.representation.*;
import adaa.analytics.rules.utils.RapidMiner5;
import com.rapidminer.RapidMiner;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.*;
import com.rapidminer.example.table.NominalMapping;
import com.rapidminer.gui.tools.dialogs.wizards.dataimport.csv.CSVFileReader;
import com.rapidminer.operator.OperatorCreationException;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.performance.PerformanceEvaluator;
import com.rapidminer.operator.preprocessing.IdTagging;
import com.rapidminer.operator.preprocessing.MaterializeDataInMemory;
import com.rapidminer.operator.preprocessing.filter.AttributeAdd;
import com.rapidminer.operator.tools.ExpressionEvaluationException;
import com.rapidminer.tools.LineParser;
import com.rapidminer.tools.OperatorService;
import org.apache.commons.math.stat.descriptive.rank.Median;
import org.junit.Test;
import org.renjin.invoke.codegen.ArgumentException;
import org.renjin.repackaged.guava.base.Strings;
import org.renjin.repackaged.guava.collect.Lists;
import org.renjin.repackaged.guava.io.Files;
import utils.ArffFileLoader;
import utils.ArffFileWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.*;
import java.util.stream.Collectors;

public class VerificationExperiment {

    private static String dataDirectory = "C:/Users/pawel/desktop/action-rules/datasets/mixed/";
    private static String resultDir = "C:/Users/pawel/desktop/action-rules/results/";
    private ActionInductionParameters params;
    private List<FileDescription> dataFiles;
    private ClassificationMeasure measure;
    private double trainSetPercentage = 0.8;
    private int number_of_folds = 10;
    MaterializeDataInMemory materializer;

    class FileDescription {
        private Path path_to_file;
        private String source_class;
        private String target_class;
        private String file_name;
        private ExampleSet[] trainSets;
        private ExampleSet[] testSets;
        int nFolds;

        FileDescription(String fileName, String source, String target, int folds) throws OperatorException, OperatorCreationException {
            file_name = fileName;
            path_to_file = Paths.get(dataDirectory, fileName);
            if (!(new File(path_to_file.toString())).exists()) {
                throw new RuntimeException(fileName + "doesn't exists in " + dataDirectory);
            }
            source_class = source;
            target_class = target;
            trainSets = new ExampleSet[folds];
            testSets = new ExampleSet[folds];
            nFolds = folds;
            ExampleSet wholeData = ArffFileLoader.load(Paths.get(this.getFilePath()), "class");

            StratifiedPartitionBuilder partitionBuilder = new StratifiedPartitionBuilder(wholeData, true, 42);

            final double[] ratios = new double[]{trainSetPercentage, 1-trainSetPercentage};
            final int TRAIN_IDX = 0;
            final int TEST_IDX = 1;
            for (int i = 0; i < nFolds; i++) {
                Partition partition = new Partition(ratios, wholeData.size(), partitionBuilder);
                SplittedExampleSet splitted = new SplittedExampleSet(wholeData, partition);


                splitted.selectSingleSubset(TRAIN_IDX);
                trainSets[i] = materializer.apply(splitted);
                splitted.clearSelection();
                splitted.selectSingleSubset(TEST_IDX);
                testSets[i] = materializer.apply(splitted);
            }
        }

        String getFileName() { return file_name; }
        String getFileNameWithoutExtension() { return Files.getNameWithoutExtension(file_name); }
        String getFilePath() { return path_to_file.toString(); }
        String getSourceClass() { return source_class; }
        String getTargetClass() { return target_class; }

        String getResultFileName(String qName) {
            return Files.getNameWithoutExtension(path_to_file.toString()) + "-" + qName + ".log";
        }

        ExampleSet getTestSetForFold(int fold) {
            return testSets[fold];
        }
        ExampleSet getTrainSetForFold(int fold) {
            return trainSets[fold];
        }
    }


    private void prepareParams(ClassificationMeasure qualityFunction){
        ActionFindingParameters findingParams = new ActionFindingParameters();
        findingParams.setUseNotIntersectingRangesOnly(ActionFindingParameters.RangeUsageStrategy.NOT_INTERSECTING);

        measure = qualityFunction;

        params = new ActionInductionParameters(findingParams);
        params.setInductionMeasure(measure);
        params.setPruningMeasure(measure);
        params.setVotingMeasure(measure);
        params.setEnablePruning(true);
        params.setIgnoreMissing(true);
        params.setMinimumCovered(5);
        params.setMaximumUncoveredFraction(0.05);
        params.setMaxGrowingConditions(0);
    }
    @Test
    public void tets() throws OperatorException, OperatorCreationException, IOException {
        RapidMiner.init();
        prepareDataSets();
        experiment(new FileDescription("credit-g.arff", "bad", "good", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.C2));
     /*   experiment(new FileDescription("iris-reduced.arff", "Iris-setosa", "Iris-versicolor", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.Correlation));
        experiment(new FileDescription("iris-reduced.arff", "Iris-setosa", "Iris-versicolor", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.C2));
        experiment(new FileDescription("iris-reduced.arff", "Iris-setosa", "Iris-versicolor", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.InformationGain));
        experiment(new FileDescription("iris-reduced.arff", "Iris-setosa", "Iris-versicolor", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.WeightedLaplace));
        experiment(new FileDescription("iris-reduced.arff", "Iris-setosa", "Iris-versicolor", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.Precision));

      //  Logger.getInstance().addStream(System.out, Level.FINE);
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.C2));
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.WeightedLaplace));
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.InformationGain));
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.Precision));
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.Correlation));
        experiment(new FileDescription("hepatitis.arff", "DIE", "LIVE", number_of_folds),number_of_folds,  new ClassificationMeasure(ClassificationMeasure.RSS));
  /*      experiment(new FileDescription("wine-reduced.arff", "1", "2", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.C2));
        experiment(new FileDescription("wine-reduced.arff", "1", "2", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.InformationGain));
        experiment(new FileDescription("wine-reduced.arff", "1", "2", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.WeightedLaplace));
        experiment(new FileDescription("wine-reduced.arff", "1", "2", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.Precision));

        experiment(new FileDescription("labor.arff", "bad", "good", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.Correlation));
        experiment(new FileDescription("labor.arff", "bad", "good", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.C2));
        experiment(new FileDescription("labor.arff", "bad", "good", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.InformationGain));
        experiment(new FileDescription("labor.arff", "bad", "good", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.WeightedLaplace));
        experiment(new FileDescription("labor.arff", "bad", "good", number_of_folds),10,  new ClassificationMeasure(ClassificationMeasure.Precision));
*/
    }


    @Test
    public void testMany() throws OperatorException, OperatorCreationException, IOException {
        prepareDataSets();
        ClassificationMeasure[] measures =
                {
                        new ClassificationMeasure(ClassificationMeasure.C2),
                        new ClassificationMeasure(ClassificationMeasure.RSS),
                        new ClassificationMeasure(ClassificationMeasure.InformationGain),
                        new ClassificationMeasure(ClassificationMeasure.WeightedLaplace),
                        new ClassificationMeasure(ClassificationMeasure.Correlation),
                        new ClassificationMeasure(ClassificationMeasure.Precision)
                };

        FileWriter fw = new FileWriter("C:\\Users\\pawel\\Desktop\\action-rules\\analysis\\r-package\\action.rules\\results\\discretized_rules\\preResults5.csv");
        fw.write("dataset;fold_id;train_forest_acc;test_forest_acc;test_target_class_acc;test_source_class_acc;source_examples_in_test;target_examples_in_test;predicted_source_in_test;predicted_target_in_test;n_predicted_as_source;n_predicted_as_target;n_predicted_as_target_recom;n_predicted_as_source_recom;source_class;target_class;measure;direction;covered_by_rules;covered_by_recom;examples_in_mutated;classifier");
        fw.write(System.lineSeparator());

        for (FileDescription desc : dataFiles) {
            for (ClassificationMeasure m : measures) {
                System.out.println("Starting experiment for file " + desc.getFilePath() + " for function " + m.getName());
                List<Map<String, Double>> results = experiment(desc, number_of_folds,  m);
                for (Map<String,Double> entry : results) {

                    //forward
                    fw.write(desc.getFileNameWithoutExtension());fw.write(';');
                    writeCommonCSVPart(fw, entry);
                    fw.write(entry.get("n_predicted_as_source").toString()); fw.write(";");
                    fw.write(entry.get("n_predicted_as_target").toString()); fw.write(";");
                    fw.write(entry.get("n_predicted_as_target_recom").toString());fw.write(";");
                    fw.write(entry.get("n_predicted_as_source_recom").toString());fw.write(";");
                    fw.write(desc.getSourceClass());fw.write(";");
                    fw.write(desc.getTargetClass());fw.write(";");
                    fw.write(m.getName());fw.write(";");
                    fw.write("forward");fw.write(";");
                    fw.write(entry.get("covered_by_rules_f").toString());fw.write(";");
                    fw.write(entry.get("covered_by_recom_f").toString());fw.write(";");
                    fw.write(entry.get("examples_in_mutated").toString());fw.write(";");
                    fw.write("rules");
                    fw.write(System.lineSeparator());
                    //backward
                    fw.write(desc.getFileNameWithoutExtension());fw.write(';');
                    writeCommonCSVPart(fw, entry);
                    fw.write(entry.get("n_predicted_as_source_b").toString());fw.write(";");
                    fw.write(entry.get("n_predicted_as_target_b").toString());fw.write(";");
                    fw.write(entry.get("n_predicted_as_target_recom_b").toString());fw.write(";");
                    fw.write(entry.get("n_predicted_as_source_recom_b").toString());fw.write(";");
                    fw.write(desc.getSourceClass());fw.write(";");
                    fw.write(desc.getTargetClass());fw.write(";");
                    fw.write(m.getName());fw.write(";");
                    fw.write("backward");fw.write(";");
                    fw.write(entry.get("covered_by_rules_b").toString());fw.write(";");
                    fw.write(entry.get("covered_by_recom_b").toString());fw.write(";");
                    fw.write(entry.get("examples_in_mutated").toString());fw.write(";");
                    fw.write("rules");
                    fw.write(System.lineSeparator());
                }

            }
        }
        fw.close();
    }

    private void writeCommonCSVPart(FileWriter fw, Map<String, Double> entry) throws IOException {
        fw.write(entry.get("fold_id").toString());fw.write(";");
        fw.write(entry.get("train_forest_acc").toString());fw.write(";");
        fw.write(entry.get("test_forest_acc").toString());fw.write(";");
        fw.write(entry.get("test_target_class_acc").toString());fw.write(";");
        fw.write(entry.get("test_source_class_acc").toString());fw.write(";");
        fw.write(entry.get("source_examples_in_test").toString());fw.write(";");
        fw.write(entry.get("target_examples_in_test").toString());fw.write(";");
        fw.write(entry.get("predicted_source_in_test").toString());fw.write(";");
        fw.write(entry.get("predicted_target_in_test").toString());fw.write(";");
    }

    private void prepareDataSets() throws OperatorException, OperatorCreationException {
        if (!RapidMiner.isInitialized()) {
            RapidMiner.init();
        }
        materializer = OperatorService.createOperator(MaterializeDataInMemory.class);
        File datasetsFile = new File(dataDirectory + "_datasets.csv");
        LineParser parser = new LineParser();
        parser.setUseQuotes(true);
        parser.setSplitExpression(LineParser.SPLIT_BY_SEMICOLON_EXPRESSION);
        NumberFormat nf = NumberFormat.getInstance();
        CSVFileReader reader = new CSVFileReader(datasetsFile, true, parser, nf);
        List<String[]> datasets;
        try {
             datasets = reader.readData(200);
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't load dataset description. Reason: " + ex.getMessage());
        }

        dataFiles = new ArrayList<>();

        for(String[] row : datasets){
            if (row.length < 11)
                continue;
            if (Strings.isNullOrEmpty(row[9]) && Strings.isNullOrEmpty(row[10]))
                continue;
            dataFiles.add(new FileDescription(row[0] + ".arff", row[9], row[10], number_of_folds));
        }

        dataFiles.removeIf(x -> x.getSourceClass().isEmpty() && x.getTargetClass().isEmpty());
    }

    private void mutateNominalAttribute(Example toBeMutated, Attribute mutatedAttribute, Action suggestedMutation) {
        if (suggestedMutation.getActionNil()) return;
        double newValue = ((SingletonSet) suggestedMutation.getRightValue()).getValue();

        toBeMutated.setValue(mutatedAttribute, newValue);
    }

    private void mutateNumericalAttribute(Example toBeMutated, Attribute mutatedAttribute, Action suggestedMutation, ExampleSet trainSet, String targetClassName) {
        Interval proposedInterval = ((Interval) suggestedMutation.getRightValue());
        if (proposedInterval == null)
            return;
        Attribute classAtr = trainSet.getAttributes().getLabel();
        ConditionedExampleSet filtered = null;

        AttributeValueFilter condition = new AttributeValueFilter(
                trainSet,
                mutatedAttribute.getName() + " " + proposedInterval.getLeftSign() + " " + proposedInterval.getLeft() + " && " + mutatedAttribute.getName() + " " + proposedInterval.getRightSign() + " " + proposedInterval.getRight() + "&&" + classAtr.getName() + "=" + targetClassName);
        try {
            filtered = new ConditionedExampleSet(trainSet, condition);
        } catch( Exception ex) {
            System.out.println("Wrong expression in example filtering");
        }
        List<Double> vals = new LinkedList<>();
        if (filtered != null) {
            for (Example ex : filtered) {
                Double currentValue = ex.getValue(mutatedAttribute);
                vals.add(currentValue);
            }
        }

        Median median = new Median();
        double newValue = median.evaluate(vals.stream().mapToDouble(Double::doubleValue).toArray());

        toBeMutated.setValue(mutatedAttribute, newValue);
    }


    private List<Map<String, Double>> experiment(FileDescription dataFileDesc, int kFolds, ClassificationMeasure qualityFunction) throws OperatorException, OperatorCreationException, IOException {
        if (!RapidMiner.isInitialized()) {
            RapidMiner.init();
        }
        List<Map<String,Double>> allResults = new ArrayList<>();
        if (trainSetPercentage > 0.99 || trainSetPercentage < 0.01)
            throw new ArgumentException("The trainSetPercentage must be betwen 0.01 and 0.99");

        if (kFolds < 1)
            throw new ArgumentException("Number of folds must be positive");

        prepareParams(qualityFunction);

        for (int iteration = 0; iteration < kFolds; iteration++) {

            ExampleSet train = dataFileDesc.getTrainSetForFold(iteration);

            String trainDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-train"
                    + ".arff");
            ArffFileWriter.write(train, trainDumpName);

            //induce the action rules
            params.addClasswiseTransition(dataFileDesc.getSourceClass(), dataFileDesc.getTargetClass());
            params.setStableAttributes(Arrays.asList("credit_amount", "duration", "age"));
            ActionSnC snc = new ActionSnC(new ActionFinder(params), params);
            BackwardActionSnC bsnc = new BackwardActionSnC(new ActionFinder(params), params);
            ClassificationSnC ruleSnC = new ClassificationSnC(new ClassificationFinder(params), params);

            Long timeBefore = System.currentTimeMillis();

            ActionRuleSet ruleSet = (ActionRuleSet)snc.run(train);
            System.out.println("Action rules took " + (System.currentTimeMillis() - timeBefore));

            ActionRuleSet backwardRuleSet = (ActionRuleSet)bsnc.run(train);
            ClassificationRuleSet cRuleSet = (ClassificationRuleSet) ruleSnC.run(train);

            ExampleSet classifiedTrain = cRuleSet.apply(train);
            //result of classification in "prediction" attribute
            //need to get: train accuracy, test source class acc, test target class acc, test accuracy
            //predicted sources in test, predicted targets in test
            int correctPredictions = 0;
            Attribute clazz = classifiedTrain.getAttributes().get("class");
            Attribute pred = classifiedTrain.getAttributes().get("prediction");
            int sourceId = clazz.getMapping().getIndex(dataFileDesc.getSourceClass());
            int targetId = clazz.getMapping().getIndex(dataFileDesc.getTargetClass());
            int correctTarget = 0;
            int correctSource = 0;
            int sourceInTrain = 0;
            for (Example ex : classifiedTrain) {

                if (Double.compare(ex.getValue(clazz), (double)sourceId) == 0) {
                    sourceInTrain += 1;
                }

                if (Double.compare(ex.getValue(clazz), ex.getValue(pred)) == 0) {
                    correctPredictions += 1;
                    if (Double.compare(ex.getValue(clazz), (double)sourceId) == 0) {
                        correctSource += 1;
                    } else if (Double.compare(ex.getValue(clazz), (double)targetId) == 0) {
                        correctTarget += 1;
                    }
                }
            }
            double ruleTrainAcc = (double)correctPredictions / (double)classifiedTrain.size();
            //use BAcc instead
            int targetInTrain = classifiedTrain.size() - sourceInTrain;
            double trainSourceBacc = correctSource / sourceInTrain;
            double trainTargetBacc = correctTarget / targetInTrain;

            ruleTrainAcc = (trainSourceBacc + trainTargetBacc) / 2.0;


            File rulesFile = new File(resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-rules-" + measure.getName() + ".rules" );
            File actionRulesFile = new File(resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-action-rules-" + measure.getName() + ".rules" );
            File backwardActionRulesFile = new File(resultDir
                    +dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "action-backward-rules-" + measure.getName() + ".rules");

            RuleSerializer ser = new RuleSerializer(train, ';', "nil");
            Files.write(cRuleSet.toString().getBytes(), rulesFile);

            RuleSetBase.Significance sigFLFDR = ruleSet.getSourceRuleSet().calculateSignificanceFDR(0.05);
            RuleSetBase.Significance sigFRFDR = ruleSet.getTargetRuleSet().calculateSignificanceFDR(0.05);
            RuleSetBase.Significance sigFLFWER = ruleSet.getSourceRuleSet().calculateSignificanceFWER(0.05);
            RuleSetBase.Significance sigFRFWER = ruleSet.getTargetRuleSet().calculateSignificanceFWER(0.05);

            RuleSetBase.Significance sigBLFDR = backwardRuleSet.getSourceRuleSet().calculateSignificanceFDR(.05);
            RuleSetBase.Significance sigBRFDR = backwardRuleSet.getTargetRuleSet().calculateSignificanceFDR(.05);
            RuleSetBase.Significance sigBLFWER = backwardRuleSet.getSourceRuleSet().calculateSignificanceFWER(.05);
            RuleSetBase.Significance sigBRFWER = backwardRuleSet.getTargetRuleSet().calculateSignificanceFWER(.05);

            RuleSetBase.Significance sigFL = ruleSet.getSourceRuleSet().calculateSignificance(0.05);
            RuleSetBase.Significance sigFR = ruleSet.getTargetRuleSet().calculateSignificance(0.05);
            RuleSetBase.Significance sigBL = backwardRuleSet.getSourceRuleSet().calculateSignificance(0.05);
            RuleSetBase.Significance sigBR = backwardRuleSet.getTargetRuleSet().calculateSignificance(0.05);

            Files.write((ruleSet.toString() + formatSignificance(sigFL, sigFR, sigFLFDR, sigFRFDR, sigFLFWER, sigFRFWER) + System.lineSeparator()  + ser.serializeToCsv(ruleSet)).getBytes(), actionRulesFile);
            Files.write((backwardRuleSet.toString() + formatSignificance(sigBL, sigBR, sigBLFDR, sigBRFDR, sigBLFWER, sigBRFWER) + System.lineSeparator() +ser.serializeToCsv(backwardRuleSet)).getBytes(), backwardActionRulesFile);

            //to deep copy
            ExampleSet testSet = dataFileDesc.getTestSetForFold(iteration);
            ExampleSet test_no_id = materializer.apply(testSet);

            IdTagging id = OperatorService.createOperator(IdTagging.class);
            ExampleSet test = id.apply(test_no_id);

            ExampleSet predictedByClassificationRules = cRuleSet.apply(test);

            int testCorrectPredictions = 0;
            int correctTargets = 0;
            int correctSources = 0;
            int sourcesInTest = 0;
            int targetsInTest = 0;
            clazz = predictedByClassificationRules.getAttributes().get("class");
            pred = predictedByClassificationRules.getAttributes().get("prediction");
            int predictedSources = 0, predictedTargets = 0;
            NominalMapping mapping = clazz.getMapping();
            for (Example ex : predictedByClassificationRules) {
                boolean predictionOK = Double.compare(ex.getValue(clazz), ex.getValue(pred)) == 0;
                if (predictionOK) testCorrectPredictions++;
                if (Double.compare(ex.getValue(clazz), sourceId) == 0) {
                    predictedSources++;
                }
                if (Double.compare(ex.getValue(clazz), targetId) == 0) {
                    predictedTargets++;
                }
                if (Double.compare(ex.getValue(clazz), (double)mapping.getIndex(dataFileDesc.getSourceClass())) == 0) {
                    sourcesInTest++;
                    if (predictionOK) {
                        correctSources++;
                    }
                }

                if (Double.compare(ex.getValue(clazz), (double)mapping.getIndex(dataFileDesc.getTargetClass())) == 0) {
                    targetsInTest++;
                    if (predictionOK) {
                        correctTargets++;
                    }
                }
            }
            double testAcc = (double)testCorrectPredictions / (double)test.size();
            double testSourceAcc = (double)correctSources / (double)sourcesInTest;
            double testTargetAcc = (double)correctTargets / (double)targetsInTest;

            testAcc = (testSourceAcc + testTargetAcc) / 2.0;

            String testDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-test"
                    + ".arff");
            ArffFileWriter.write(predictedByClassificationRules, testDumpName);
            Condition cnd = new AttributeValueFilterSingleCondition(test.getAttributes().getLabel(), AttributeValueFilterSingleCondition.EQUALS, dataFileDesc.getSourceClass());
            ExampleSet sourceExamplesInTestSet  = materializer.apply(new ConditionedExampleSet(test, cnd));

            AttributeAdd adder = OperatorService.createOperator(AttributeAdd.class);
            adder.setParameter(AttributeAdd.PARAMETER_NAME, "mutated_rule_id");
            adder.setParameter(AttributeAdd.PARAMETER_VALUE_TYPE, "integer");

            sourceExamplesInTestSet = adder.apply(sourceExamplesInTestSet);

            timeBefore = System.currentTimeMillis();
            ActionRangeDistribution dist = new ActionRangeDistribution(ruleSet, train);
            dist.calculateActionDistribution();
            ActionMetaTable optimized = new OptimizedActionMetaTable(dist, this.measure, snc.getStableAttributes());
            System.out.println("Wyliczenia rozkładu forward: " + (System.currentTimeMillis() - timeBefore));

            timeBefore = System.currentTimeMillis();
            ActionRangeDistribution backwardDistribution = new ActionRangeDistribution(backwardRuleSet, train);
            backwardDistribution.calculateActionDistribution();
            ActionMetaTable backwardMetaTable = new OptimizedActionMetaTable(backwardDistribution, this.measure, bsnc.getStableAttributes());
            System.out.println("Wyliczenia rozkładu backward: " + (System.currentTimeMillis() - timeBefore));

            ActionRuleSet recommendations = new ActionRuleSet(sourceExamplesInTestSet, true, params, null);
            ActionRuleSet backwardRecommendations = new ActionRuleSet(sourceExamplesInTestSet, true, params, null);
            timeBefore = System.currentTimeMillis();
            ExampleSet mutatedBackwardRecom = mutateExamples(sourceExamplesInTestSet, backwardMetaTable, dataFileDesc.getSourceClass(), dataFileDesc.getTargetClass(), backwardRecommendations, train);
            System.out.println("Mutacja rekomendacjami backward: " + (System.currentTimeMillis() - timeBefore));

            timeBefore = System.currentTimeMillis();
            ExampleSet mutatedBackwardRules = mutateExamples(sourceExamplesInTestSet, backwardRuleSet, train, dataFileDesc.getTargetClass());
            System.out.println("Mutacja regułami backward: " + (System.currentTimeMillis() - timeBefore));
            //  backwardRecommendations.apply(sourceExamplesInTestSet)

            timeBefore = System.currentTimeMillis();
            ExampleSet mutatedRecom = mutateExamples(sourceExamplesInTestSet, optimized, dataFileDesc.getSourceClass(), dataFileDesc.getTargetClass(), recommendations, train);
            System.out.println("Mutacja rekomendacjami forward: " + (System.currentTimeMillis() - timeBefore));

            timeBefore = System.currentTimeMillis();
            ExampleSet mutated = mutateExamples(sourceExamplesInTestSet, ruleSet, train, dataFileDesc.getTargetClass());
            System.out.println("Mutacja regułami forward: " + (System.currentTimeMillis() - timeBefore));

            String mutatedDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-mutated"
                    + measure.getName()
                    + ".arff");

            String mutatedBackwardDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-mutatedBackward-"
                    + measure.getName()
                    + ".arff");

            String mutatedRecomDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-mutated-recom"
                    + measure.getName()
                    + ".arff");

            String mutatedRecomBackwardDumpName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + "-mutated-backward-recom-"
                    + measure.getName()
                    + ".arff");
            String recommendationsFileName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + measure.getName()
                    + ".recommendations");

            String recommendationsBackwardFileName = (resultDir
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "/"
                    + dataFileDesc.getFileNameWithoutExtension()
                    + "fold-" + iteration
                    + measure.getName()
                    + "-backward"
                    + ".recommendations");

            ArffFileWriter.write(mutated, mutatedDumpName);
            ArffFileWriter.write(mutatedRecom, mutatedRecomDumpName);
            ArffFileWriter.write(mutatedBackwardRules, mutatedBackwardDumpName);
            ArffFileWriter.write(mutatedBackwardRecom, mutatedRecomBackwardDumpName);


            Files.write((recommendations.toString() +  System.lineSeparator() + ser.serializeToCsv(recommendations)).getBytes(), new File(recommendationsFileName));
            Files.write((backwardRecommendations.toString()  + System.lineSeparator() + ser.serializeToCsv(backwardRecommendations)).getBytes(), new File(recommendationsBackwardFileName));

            //for the verification we take only examples that were covered by action rules or recommendations

            ExampleSet mutatedAndCoveredByRules = getCoveredByRules(mutated);
            ExampleSet mutatedAndCoveredByBackwardRules = getCoveredByRules(mutatedBackwardRules);
            ExampleSet mutatedAndCoveredByRecom = getCoveredByRecom(mutatedRecom);
            ExampleSet mutatedAndCoveredByBackwardRecom = getCoveredByRecom(mutatedBackwardRecom);

            ExampleSet rules_mutated = cRuleSet.apply(mutatedAndCoveredByRules);
            ExampleSet rules_mutatedBackwardRules = cRuleSet.apply(mutatedAndCoveredByBackwardRules);
            ExampleSet rules_mutatedRecom = cRuleSet.apply(mutatedAndCoveredByRecom);
            ExampleSet rules_mutatedBackwardRecom = cRuleSet.apply(mutatedAndCoveredByBackwardRecom);

            int n_predicted_as_source_f = getNumberOfPredictedExamples(rules_mutated, dataFileDesc.getSourceClass());
            int n_predicted_as_target_f = getNumberOfPredictedExamples(rules_mutated, dataFileDesc.getTargetClass());
            int n_predicted_as_source_b = getNumberOfPredictedExamples(rules_mutatedBackwardRules, dataFileDesc.getSourceClass());
            int n_predicted_as_target_b = getNumberOfPredictedExamples(rules_mutatedBackwardRules, dataFileDesc.getTargetClass());

            int n_predicted_as_source_recom_f = getNumberOfPredictedExamples(rules_mutatedRecom, dataFileDesc.getSourceClass());
            int n_predicted_as_target_recom_f = getNumberOfPredictedExamples(rules_mutatedRecom, dataFileDesc.getTargetClass());
            int n_predicted_as_source_recom_b = getNumberOfPredictedExamples(rules_mutatedBackwardRecom, dataFileDesc.getSourceClass());
            int n_predicted_as_target_recom_b = getNumberOfPredictedExamples(rules_mutatedBackwardRecom, dataFileDesc.getTargetClass());



            Map<String, Double> results = new HashMap<>();
            //results.put("dataset", dataFileDesc.getFileNameWithoutExtension())
            results.put("fold_id", (double)iteration);
            results.put("train_forest_acc", ruleTrainAcc);
            results.put("test_forest_acc", testAcc);
            results.put("test_target_class_acc", testTargetAcc);
            results.put("test_source_class_acc", testSourceAcc);
            results.put("source_examples_in_test", (double)sourceExamplesInTestSet.size());
            results.put("target_examples_in_test", (double)(test.size() - sourceExamplesInTestSet.size()));
            results.put("predicted_source_in_test", (double)sourcesInTest);
            results.put("predicted_target_in_test", (double)targetsInTest);
            results.put("n_predicted_as_source", (double)n_predicted_as_source_f);
            results.put("n_predicted_as_target", (double)n_predicted_as_target_f);
            results.put("n_predicted_as_target_recom", (double)n_predicted_as_target_recom_f);
            results.put("n_predicted_as_source_recom", (double)n_predicted_as_source_recom_f);
            results.put("examples_in_mutated", (double)mutated.size());



            results.put("n_predicted_as_source_b", (double)n_predicted_as_source_b);
            results.put("n_predicted_as_target_b", (double)n_predicted_as_target_b);
            results.put("n_predicted_as_target_recom_b", (double)n_predicted_as_target_recom_b);
            results.put("n_predicted_as_source_recom_b", (double)n_predicted_as_source_recom_b);

            results.put("covered_by_rules_f", (double)mutatedAndCoveredByRules.size());
            results.put("covered_by_recom_f", (double)mutatedAndCoveredByRecom.size());
            results.put("covered_by_rules_b", (double)mutatedAndCoveredByBackwardRules.size());
            results.put("covered_by_recom_b", (double)mutatedAndCoveredByBackwardRecom.size());

            allResults.add(results);
        }
        return allResults;
    }

    private String formatSignificance(RuleSetBase.Significance sigFL, RuleSetBase.Significance sigFR, RuleSetBase.Significance sigFLFDR, RuleSetBase.Significance sigFRFDR, RuleSetBase.Significance sigFLFWER, RuleSetBase.Significance sigFRFWER) {
        StringBuilder sb = new StringBuilder();
        sb.append("Significant source rules: ").append(sigFL.fraction).append(System.lineSeparator());
        sb.append("Significant target rules: ").append(sigFR.fraction).append(System.lineSeparator());
        sb.append("Significant source rules FDR: ").append(sigFLFDR.fraction).append(System.lineSeparator());
        sb.append("Significant target rules FDR: ").append(sigFRFDR.fraction).append(System.lineSeparator());
        sb.append("Significant source rules FWER: ").append(sigFLFWER.fraction).append(System.lineSeparator());
        sb.append("Significant target rules FWER: ").append(sigFRFWER.fraction).append(System.lineSeparator());
        return  sb.toString();
    }

    private ExampleSet getCoveredByRules(ExampleSet mutated) throws ExpressionEvaluationException {
        Attribute attribute = mutated.getAttributes().get("mutated_rule_id");
        Condition cnd = new AttributeValueFilterSingleCondition(attribute, AttributeValueFilterSingleCondition.NEQ1, "?");
        ExampleSet covered = new ConditionedExampleSet(mutated, cnd);
        return covered;
    }

    private ExampleSet getCoveredByRecom(ExampleSet mutated) throws ExpressionEvaluationException {
        Attribute attribute = mutated.getAttributes().get("mutated_rule_id");
        Condition cnd = new AttributeValueFilterSingleCondition(attribute, AttributeValueFilterSingleCondition.EQUALS, "-1.0");
        return new ConditionedExampleSet(mutated, cnd);
    }

    private int getNumberOfPredictedExamples(ExampleSet set, String className){
        Attribute clazz = set.getAttributes().get("class");
        Attribute pred = set.getAttributes().get("prediction");
        NominalMapping mapping = clazz.getMapping();
        double classIdx = (double)mapping.getIndex(className);
        int cnt = 0;
        for (Example ex : set) {
            if (Double.compare(ex.getValue(pred), classIdx) == 0){
                cnt++;
            }
        }
        return cnt;
    }

    private ExampleSet mutateExamples(ExampleSet splitted, ActionMetaTable metaTable, String sourceClass, String targetClass, ActionRuleSet usedRecommendations, ExampleSet trainSet) throws OperatorException {

        ExampleSet result = materializer.apply(splitted);
        if (result.getAttributes().getPredictedLabel() != null) {
            result.getAttributes().remove(result.getAttributes().getPredictedLabel());
        }
        int failedCount = 0;
        int sourceId = result.getAttributes().getLabel().getMapping().getIndex(sourceClass);
        int targetId = result.getAttributes().getLabel().getMapping().getIndex(targetClass);

        for (Example current : result) {
            List<MetaAnalysisResult> recoms = metaTable.analyze(current, sourceId, targetId, true, false);
            if (recoms.isEmpty()) {
                failedCount++;
                current.setValue(current.getAttributes().get("mutated_rule_id"), -1964);
                continue;
            }
            MetaAnalysisResult golden = recoms.get(0);
            ActionRule asRule = golden.getActionRule();
            asRule.setCoveringInformation(asRule.covers(trainSet));

            usedRecommendations.addRule(asRule);
            for (ConditionBase cond : asRule.getPremise().getSubconditions()) {
                Action action = (Action) cond;

                Attribute attributeToMutate = result.getAttributes().get(action.getAttribute());
                if (attributeToMutate.isNominal()) {
                    mutateNominalAttribute(current, attributeToMutate, action);
                } else {
                    mutateNumericalAttribute(current, attributeToMutate, action, trainSet, targetClass);
                }
            }
            current.setValue(current.getAttributes().get("mutated_rule_id"), -1);

        }
        System.out.println("Failed to mutate by recoms " + failedCount + " examples");
        return result;
    }

    private ExampleSet mutateExamples(ExampleSet splitted, ActionRuleSet ruleSet, ExampleSet trainSet, String targetClassName) throws OperatorException {
        //ExampleSet result = new ExampleSet();
        ExampleSet result = materializer.apply(splitted);

        if (result.getAttributes().getPredictedLabel() != null) {
            result.getAttributes().remove(result.getAttributes().getPredictedLabel());
        }
        int failedCount = 0;
        for (Example current : result) {

            List<ActionRule> applicableRules = ruleSet.getRules()
                    .stream()
                    .map(ActionRule.class::cast)
                    .filter(x -> x.getPremise().evaluate(current))
                    .collect(Collectors.toList());

            if (applicableRules.size() < 1) {
                failedCount++;
                continue;
            }

            applicableRules.sort(Comparator.comparingDouble(Rule::getWeight));
            ActionRule toApply = applicableRules.get(0);

            for (ConditionBase cond : toApply.getPremise().getSubconditions()) {
                Action action = (Action) cond;

                Attribute attributeToMutate = result.getAttributes().get(action.getAttribute());
                if (attributeToMutate.isNominal()) {
                   mutateNominalAttribute(current, attributeToMutate, action);
                } else {
                   mutateNumericalAttribute(current, attributeToMutate, action, trainSet, targetClassName);
                }
            }
            current.setValue(current.getAttributes().get("mutated_rule_id"), ruleSet.getRules().indexOf(toApply) + 1);

        }
        System.out.println("Failed to mutate " + failedCount + " examples");
        return result;
    }
}
