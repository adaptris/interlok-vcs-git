# interlok-vcs-git

[![GitHub tag](https://img.shields.io/github/tag/adaptris/interlok-vcs-git.svg)](https://github.com/adaptris/interlok-vcs-git/tags)
[![license](https://img.shields.io/github/license/adaptris/interlok-vcs-git.svg)](https://github.com/adaptris/interlok-vcs-git/blob/develop/LICENSE)
[![Actions Status](https://github.com/adaptris/interlok-vcs-git/actions/workflows/gradle-publish.yml/badge.svg)](https://github.com/adaptris/interlok-vcs-git/actions)
[![codecov](https://codecov.io/gh/adaptris/interlok-vcs-git/branch/develop/graph/badge.svg)](https://codecov.io/gh/adaptris/interlok-vcs-git)
[![CodeQL](https://github.com/adaptris/interlok-vcs-git/workflows/CodeQL/badge.svg)](https://github.com/adaptris/interlok-vcs-git/security/code-scanning)
[![Known Vulnerabilities](https://snyk.io/test/github/adaptris/interlok-vcs-git/badge.svg?targetFile=build.gradle)](https://snyk.io/test/github/adaptris/interlok-vcs-git?targetFile=build.gradle)
[![Closed PRs](https://img.shields.io/github/issues-pr-closed/adaptris/interlok-vcs-git)](https://github.com/adaptris/interlok-vcs-git/pulls?q=is%3Apr+is%3Aclosed)

Checking out interlok configuration from git on startup.

## Canonical Reference Documentation

[https://interlok.adaptris.net/interlok-docs/#/pages/advanced/advanced-vcs-git](https://interlok.adaptris.net/interlok-docs/#/pages/advanced/advanced-vcs-git)

## Quickstart

In your bootstrap.properties (assuming that you have your ssh keys setup....):

```
# The adapter configuration file is VCS managed; so we refer to the local working copy.
adapterConfigUrl=file://localhost/./config/interlok-config-example/adapter.xml

# Our Log4j is VCS managed; so we can refer to the local working copy.
loggingConfigUrl=file://localhost/./config/interlok-config-example/log4j2.xml

# Again, the jetty.xml is checked in, so let's refer to the local working copy.
webServerConfigUrl=./config/interlok-config-example/jetty.xml

vcs.workingcopy.url=file://localhost/./config/interlok-config-example
vcs.remote.repo.url=git@github.com:adaptris/interlok-config-example.git
vcs.branch=master
vcs.always.reset=true

# vcs.username=my-user-name
# vcs.password={password}PW:My-encoded-password
```