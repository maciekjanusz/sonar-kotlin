# sonar-kotlin

[![Join the chat at https://kotlinlang.slack.com/messages/C88E12QH4/convo/C0BQ5GZ0S-1511956674.000289/](https://img.shields.io/badge/chat-on_slack-red.svg?style=flat-square)](https://kotlinlang.slack.com/messages/C88E12QH4/convo/C0BQ5GZ0S-1511956674.000289/)
[![build status](https://travis-ci.org/arturbosch/sonar-kotlin.svg?branch=master)](https://travis-ci.org/arturbosch/sonar-kotlin)
[![build status windows](https://ci.appveyor.com/api/projects/status/bn2vto5dnkenxeg5?svg=true
)](https://ci.appveyor.com/project/arturbosch/sonar-kotlin)

![sonar-kotlin-in-action](img/sonar-kotlin.png)

### Features

- Integrates [detekt](https://github.com/arturbosch/detekt) for code analysis
- Default quality profile `Detekt way`
- Syntax highlighting
- Supports SonarQube 5.6.7 and 6.7
- Supports detekt's `yaml config` and `baseline.xml` (also `path filters`)

### Usage

Sonar-kotlin is not yet uploaded to the `Update Center`

- `git clone https://github.com/arturbosch/sonar-kotlin`
- `cd sonar-kotlin`
- `./gradlew build`
- `cp build/libs/sonar-kotlin-[enter_version].jar $SONAR_HOME/extensions/plugins`
- `cd $SONAR_HOME/bin/[your_os]`
- `./sonar.sh restart`

### Configurations and Baselines (and Filters)

Read about detekt configuration files [here](https://github.com/arturbosch/detekt#ruleset-configuration)
and about detekt baseline formats [here](https://github.com/arturbosch/detekt#code-smell-baseline-and-ignore-list).

Best ways to get started is to configure the `detekt-gradle-plugin` in your project and use its `detektBaseline` and 
`detektGenerateConfig` tasks.
- `detektGenerateConfig` copies the default configuration file which you can use to turn on and off rules and set 
appropriate threshold values for some rules.
- `detektBaseline` analyzes your project and generates a baseline xml file with all your current findings. This 
findings won't get reported anymore in sonar.

##### Usage

To make use of this features, you have to set up some property files:

![configs](img/config.png)


__Detekt path filters__ support multiple regex entries by adding a `,` for separating.
__Detekt yaml configuration path__ also supports multiple configuration files where the first entered override some 
values of the later added config files.

##### Limitations


