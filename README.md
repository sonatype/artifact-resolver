# Resolve a Maven 2 Artifact Using Only A Client Side Tool

## What is it?

A standalone executable jar which uses Eclipse Aether libraries to query and optionally download via HTTP(S) an artifact from a
remote Maven 2 format repository.

The program resolves artifacts with:

 - the highest versioned within a specified Maven 2 version range ie. LATEST
 - the highest timestamped SNAPSHOT within a specified base SNAPSHOT version ie. 1.0-SNAPSHOT
 - the highest release ( non-temporary, non-snapshot) version ie. RELEASE

## How to build the stand alone jar?

The build uses a modified https://github.com/dmitart/scriptjar to compile and generate a single uber jar executable.

So you need Groovy to build it.

The scriptjar modification I made was to optionally include resources in the jar, in this case some log configuration.

A simple bash script wraps the build process.

```
./build.sh
```

## Is this a Sonatype supported product?

No. The program is provided as-is, as an example. Feel free to fork it and adjust to your own tooling environment.

## Is this a replacement for the Nexus 2 REST APIs missing in Nexus 3?

This program is meant as an example alternative client side only replacement for the following
[Nexus Repository Manager 2.x REST resources](https://support.sonatype.com/hc/en-us/articles/213465488):

```
/service/local/artifact/maven/content
/service/local/artifact/maven/redirect
```

It demonstrates how you can use a standalone client to accomplish a similar result as the Nexus 2.x REST APIs against
any remote Maven 2 format repository accessible over HTTP including Nexus 2 or 3.

## Will Sonatype ever implement a REST API in Nexus 3?

Yes. It is expected a search and component REST API will eventually be available.

## What are the limitations of this program?

The main limitation of this is it only gets the highest version by comparing all known versions from the remote - which is not
necessarily the often wanted 'latest deployed' version from your CI tool.

This only works if the remote is properly implementing a Maven 2 repository format with proper maven-metadata.xml files.

If the remote repository has Maven metadata that is not giving you results you expect, this tool cannot fix that.

## Can I get latest SNAPSHOT wit hthis program?

Sure, the program doesn't mind. As long as you understand simple stuff like `1.7-SNAPSHOT` is higher than `1.6` release for example.

Example, to get the latest timestamped snapshot of all `1.7-SNAPSHOT` versions, just specify `1.7-SNAPSHOT` as the actual version.

## Can I specify LATEST or RELEASE as the version?

Does the script accept these? Grudgingly yes...but I feel dirty.

First, some short history.

These version names are pseudo values, no longer directly supported in Maven 3 or Nexus 3 - their use encourages non-repeatable builds.

The terms were defined as:

LATEST: get the highest version, snapshot or release. Some people expect this to match the value in the `<latest>` element inside maven-metadata.xml. [THIS IS BAD!](https://support.sonatype.com/hc/en-us/articles/213464638-Why-are-the-latest-and-release-tags-in-maven-metadata-xml-not-being-updated-after-deploying-artifacts-).

RELEASE ( using Apache Maven 2.x ): get the version inside the `<release>` element in the GA level maven-metadata.xml file on the remote. [THIS IS BAD!](https://support.sonatype.com/hc/en-us/articles/213464638-Why-are-the-latest-and-release-tags-in-maven-metadata-xml-not-being-updated-after-deploying-artifacts-).

RELEASE ( using Nexus 2 REST API and this script ): get the highest release version.

LATEST is easy to simulate - the program converts this to an open ended version range.

RELEASE is implemented as get the highest non-snapshot/non-temporary version from the entire list of available versions.

For a longer history, maybe [this helps](http://stackoverflow.com/questions/30571/how-do-i-tell-maven-to-use-the-latest-version-of-a-dependency).

## How are artifact versions compared?

Versions are compared lexicographically with [some special cases](https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java#L183).

A good resource to understand Apache Maven style versions and ranges in general is this doc:

https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#MAVEN8855

For specifics, understand the [GenericVersionScheme](https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersionScheme.java)
and the [GenericVersion](https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java)

Also become familiar with getting the highest version using a [VersionRange](https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersionRange.java) with this tool.

## How do I specify the artifact to get?

Unqualified program arguments are treated as long form artifact coordinates. You can specify more than one if you like. Surround those in quotes for the best results.

The expected artifact coordinate format is:

```
<groupId>:<artifactId>[:<extension>[:<classifier>]]:[<version>]
```

If you type an invalid artifact coordinate, you will get a message telling you the expected format.

Example:

```
java -jar artifactResolver.jar @script.args commons-dbutils
ERROR: Bad artifact coordinates commons-dbutils, expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
```

Note: There is one _special_ variation that does not require version - see next section.

## How do I ask for the highest version of a group and artifact id combination, without adding a version or range?

The program will accept a special artifact coordinate variation consisting of only groupid and artifactid without a version, extension or classifer value.

In that case, the program treats the request as if you specified an unbounded upper range on the version. In other words a Maven 2 version range
equal to `[0,)`, which means a version no lower than zero and as high a version as is available?

Also the extension and classifier are defaults in this case of jar and empty value respectively.

```
java -jar artifactResolver.jar @script.args commons-dbutils:commons-dbutils
```

## Can I get the highest version in a bounded range of versions?

Sure. All calculations of the range are done entirely on inside this client. The version value of the artifact coordinate can be any valid Maven 2 version range.

## How do I specify authentication for the remote repo?

Specify a username and password with the -u,--user argument. Example:

<pre>
java -jar artifactResolver.jar --user admin:admin123 --remote.repo "http://localhost:8081/nexus/content/groups/public/" commons-dbutils:commons:dbutils
</pre>

## How do I avoid typing my password in the clear?

The script uses [Groovy CliBuilder](http://docs.groovy-lang.org/next/html/gapi/groovy/util/CliBuilder.html) to process arguments. You can create a file and put arguments, like auth, in that file instead.

Example:

1. Create a file named `script.args` in the same directory as the jar
2. The contents of the file can contain an argument on each line, like this:
     <pre>
     --user
     admin:admin123
     --repo.remote
     "http://localhost:8081/nexus/content/groups/public/"
     --repo.local
     "./target/other-repo"
     </pre>
3. Use the special `@` prefix and pass the file name as an argument to the script. Each line of the file will be read as if
it was passed on the command line. Example:
    <pre>
    java -jar artifactResolver.jar @script.args commons-dbutils:commons-dbutils
    </pre>

## Well I tried it and something went horribly wrong!

This program is perfect. It's your fault.

At least you can turn on verbose logging using Java system properties.

The Jar responds to log levels set according to system properties a la SimpleLogger.

See http://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html

Example - turn on http client logging:

```
java -Dorg.slf4j.simpleLogger.log.org.apache.http=DEBUG -jar artifactResolver.jar @script.args commons-dbutils:commons-dbutils
```

## Well it worked. Once. Now it won't send anymore requests to the remote. What up?

The script caches metadata in the local repo, much like Apache Maven itself would, and it likely hasn't expired yet. Just delete the local repo and try again.

## Dude! I recognize some of this code!!

Sure - its almost a copy-pasta of bits inside Aether Demo project.

https://github.com/eclipse/aether-demo/tree/master/aether-demo-snippets

## Dude! This is awesome!!!

Well... keep in mind this program can help solve only one use case of getting the 'latest'
thing.

There are many other client side tools out there that do similar things ie. ivy cli, grape cli

Apparently though the burden of learning them or the overhead of installing them is too much.

And besides Nexus 2 has a REST API that does what you want.

..and Nexus 3 should get around to having a REST API that does this magic for you eventually.

## Dude! This sucks balls!!!

Yep. Its free as in Free Range Turkey...the rest is up to you.

## Dude! I've read this far. What do I win?

Wow. You could have downloaded your favorite artifact by now.