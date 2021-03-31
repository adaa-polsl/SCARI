# Sequential Covering Action Rule Induction

This repository is a fork of [RuleKit](https://github.com/adaa-polsl/RuleKit) - comprehensive package with Rule-based models generation capabilities.
This repository extends RuleKit with Action Rules induction algorithms. 

## Getting started

For details on running the RuleKit please refer to the RuleKit repository. This package was extended with ActionRulesConsole class, which allows to easily run the algorithms of Action Rule induction by specyfing only few parameters. This is sufficient to get familiar with the method and analyze results achieved on custom datasets. Whether one would like to include the method in other software, the ActionRulesConsole class showcases how the code should be used to integrate it.

If one whishes not to use provided utility script, please keep in mind, that the project is buildable and runnable only with JDK8.

# Parameters of ActionRulesConsole

There are few parameters to be specified, when running the code:
* '--train <filename>' - relative path to train dataset, in ARFF format. Mandatory parameter.
* '--test <filename>' - relative path to test dataset, in ARFF format. Optional. If not provided, train file will be used for test.
* '--source <value>' - the name of source class. Mandatory parameter.
* '--target <value>' - the name of target class. Optional, if not provided, second available class will be used as target class. If there are more then two classes in the train and test datasets, this parameter must be specified.
* '--measure <name>' - one of strings: RSS, C2, Precision, InformationGain, WeightedLaplace, Correlation, which depicts which measure will be used to guide the rule growing and pruning phase. Optional, if not provided - defaults to C2.
* '--mincov <value>' - value of minimum coverage used when extending premise of the rule, Optional, defaults to 5.
* '--label <value>' - name of the label attribute in the dataset. If not provided, the name "class" will be assumed.
* '--backward' - if this switch is specified, the algorithm will run in Backward more, while without this specification in runs in Forward mode. 


# Running ActionRulesConsole with Docker (no need for local Java installation)

There are utility scripts included in the repository to make running the code easier. The binary is hosted here, on github in the Releases section, and the repository contains Dockerfile that allows to download the binary and embed it into container capable of running it, which means it can be run on any platform that supports running Docker. That way, the end-user doesn't need to bother with installation of Java version capable of running the binary.

To build the docker image, navigate to 'adaa.analytics.rules' directory and run 'build_image' script, depending on your platform or setup, this will be either 'build_image.sh' or 'build_image.ps1' file. It requires Docker installation to be present in your system. The invocation of that script will build actionrules:latest docker image.

After the image is constructed, one can use `run-action-rules.sh` script which will boot up the image, and pass the command line arguments to the binary embed in the image. The script will automatically share the directory, in which it resides, with the underlying docker container. It is best to place the dataset files in the very same directory for easiness of running the binary.

For example, the minimum command line needed to be run to achieve some results (all the optional parameters left to their defaults):
```
./build_image.sh
./run-action-rules.sh --train monk1_train.arff --source 0 
```

After building the image ones, subsequent calls don't require to build it again.

# Running and building ActionRulesConsole locally

Another way to use ActionRulesConsole is to build the binary locally, after checking out the content of this repository. This way requires JDK8 to be installed and configured locally to build java binaries, as well as gradle installation, since gradle system is used to orchestrate the compilation of the binary.

To build and run the code, one could issue following commands, starting from the root directory of this repository:

```
cd adaa.analytics.rules
gradlew -b build.gradle acrules
java -jar build/lib/ac-rules-1.3.6-all.jar --train deals-train --source no --label "Future Customer"
``` 

Here, again, the datafile is located in the 'adaa.analytics.rules' directory for easiness of accessing it via command line.

The example above shows also how to approach datasets, in which label attribute is named differently then "class". We can use optional `--label` parameter and provide the custom name. If the name contains whitespaces, don't forget to enclose them with quote marks. 

# Output files

As a result, the binary will generate few files:
* Action rules generated on train dataset
* Recommendations generated for source examples in test dataset
* Set of source examples from test dataset, that were modified according to action rules generated on train dataset
* Set of source examples from test dataset, that were modified using the recommendation engine

Feel free to inspect the code of the scripts and library provided in this repository to get to know how to use the code for more advanced scenarios. It contains some unit tests which might be helpful in understanding how the code is structured.
