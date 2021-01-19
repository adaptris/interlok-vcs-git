# interlok-vcs-git

[![GitHub tag](https://img.shields.io/github/tag/adaptris/interlok-vcs-git.svg)](https://github.com/adaptris/interlok-vcs-git/tags) [![codecov](https://codecov.io/gh/adaptris/interlok-vcs-git/branch/develop/graph/badge.svg)](https://codecov.io/gh/adaptris/interlok-vcs-git) [![Total alerts](https://img.shields.io/lgtm/alerts/g/adaptris/interlok-vcs-git.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-vcs-git/alerts/) [![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/adaptris/interlok-vcs-git.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-vcs-git/context:java)

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