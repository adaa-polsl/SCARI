# Sequential Covering Action Rule Induction

This repository is a fork of [RuleKit](https://github.com/adaa-polsl/RuleKit) - comprehensive package with Rule-based models generation capabilities.
This repository extends RuleKit with Action Rules induction algorithms. 

## Getting started

For details on running the RuleKit please refer to the RuleKit repository. This package was extended with ActionRulesConsole class, which allows to easily run the algorithms of Action Rule induction by specyfing only few parameters. This is sufficient to get familiar with the method and analyze results achieved on custom datasets. Whether one would like to include the method in other software, the ActionRulesConsole class showcases how the code should be used to integrate it.

## Running ActionRulesConsole

There are few parameters to be specified, when running the code:
* `--train <filename>` - relative path to train dataset, in ARFF format
* `--test <filename>` - relative path to test dataset, in ARFF formar
* `--measure <name>` - one of strings: RSS, C2, Precision, InformationGain, WeightedLaplace, which depicts which measure will be used to guide the rule growing and pruning phase
* `--mincov <value>` - value of minimum coverage used when extending premise of the rule, typically equals 5
* `--source <value>` - the name of source class
* `--target <value>` - the name of target class

There are utility scripts included in the repository to make running the code easier. The binary is hosted here, on github in the Releases section, and the repository contains Dockerfile that allows to download the binary and embed it into container capable of running it, which means it can be run on any platform that supports running Docker.

To build the docker image, navigate to 'adaa.analytics.rules' directory and run 'build_image' script, depending on your platform or setup, this will be either 'build_image.sh' or 'build_image.ps1' file. It requires Docker installation to be present in your system. The invocation of that script will build actionrules:latest docker image.

After the image is constructed, one can use `run-action-rules.sh` script which will boot up the image, and pass the command line arguments to the binary embed in the image.

For example:
```
./run-action-rules.sh --test monk1_test --train monk1_train --source 0 --target 1 --mincov 4 --measure RSS
```

As a result, the binary will generate few files:
* Action rules generated on train dataset
* Recommendations generated for source examples in test dataset
* Set of source examples from test dataset, that were modified according to action rules generated on train dataset
* Set of source examples from test dataset, that were modified using the recommendation engine

Feel free to inspect the code of the scripts and library provided in this repostory to get to know how to use the code for more advanced scenarios. It contains some unit tests which might be helpful in understanding how the code is structured.
