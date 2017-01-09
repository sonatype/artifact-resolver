#!/usr/bin/env groovy

/*
 * Copyright (c) 2017-present Sonatype, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

@Grapes([
    @Grab('org.slf4j:jcl-over-slf4j:1.7.22'),
    @Grab('org.slf4j:slf4j-api:1.7.22'),
    @Grab('org.slf4j:slf4j-simple:1.7.22'),
    @GrabConfig(systemClassLoader = true)
])

@Grab('org.apache.maven:maven-aether-provider:3.3.9')
@Grab('org.eclipse.aether:aether-impl:1.1.0;force=true')
@Grab('org.eclipse.aether:aether-transport-http:1.1.0')
@Grab('org.eclipse.aether:aether-transport-file:1.1.0')
@Grab('org.eclipse.aether:aether-connector-basic:1.1.0')
// commons cli is Grabbed to help scriptjar.groovy to bundle it, otherwise normally picked up from
@Grab('commons-cli:commons-cli:1.2')

import groovy.util.logging.Slf4j
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.version.Version

import static AetherUtil.*

@Slf4j
class ArtifactResolver
{

  private String artifactName

  private RemoteRepository remote

  private LocalRepository local

  final static String OPEN_RANGE = ':[0,)'

  ArtifactResolver(String artifactName, RemoteRepository remote, LocalRepository local) {
    this.artifactName = artifactName
    this.remote = remote
    this.local = local
  }

  void resolve(boolean download) {

    // missing version == highest version, default classifier, default extension
    if (artifactName.split(':').length == 2) {
      artifactName = "$artifactName:$OPEN_RANGE"
    }

    // create artifact first to verify artifact coordinates
    Artifact artifact = new DefaultArtifact(artifactName)

    boolean releaseWanted = 'RELEASE' == artifact.getVersion()
    if (['LATEST', 'RELEASE'].contains(artifact.getVersion())) {
      artifact.setVersion(OPEN_RANGE)
    }

    RepositorySystem system = newRepositorySystem()
    RepositorySystemSession session = newRepositorySystemSession(system, local)
    List<RemoteRepository> remotes = newRepositories(remote)

    VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setArtifact(artifact)
    rangeRequest.setRepositories(remotes)

    VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)


    Version highestVersion
    if (releaseWanted) {
      // a 'release' means anything but SNAPSHOT - a version that is not temporary
      // this may include versions such as -alpha, -beta, etc, which some people may find is not semantically a release
      highestVersion = rangeResult.getVersions().reverse().find { v -> !v.toString().endsWith('-SNAPSHOT') }
    }
    else {
      highestVersion = rangeResult.getHighestVersion()
    }

    println("Highest version " + highestVersion + " from repository "
        + rangeResult.getRepository(highestVersion))


    if (download) {
      ArtifactRequest artifactRequest = new ArtifactRequest()

      Artifact toDownload = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
          artifact.getClassifier(), artifact.getExtension(), highestVersion.toString())
      artifactRequest.setArtifact(toDownload)
      artifactRequest.setRepositories(remotes)

      ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest)

      artifact = artifactResult.getArtifact()

      println("$artifact resolved to ${artifact.getFile()}")
    }

  }
}


// =========== ARGS ==============
// args processing
def cli = new CliBuilder(usage: "java -jar ${artifactResolver.class.name}.jar [options] <coordinates>")
cli.with {
  u longOpt: 'user', args: 1, required: false, 'remote repo username:password'
  r longOpt: 'repo.remote', args: 1, required: false, 'remote repo url (default: central)'
  l longOpt: 'repo.local', args: 1, required: false, 'local repo directory (default ./target/local-repo)'
  n longOpt: 'no-download', required: false, 'resolve version only, do not download artifact ( default: false )'
}
cli.setFooter('''
You can specify one or more artifact coordinates of the form:

  <groupId>:<artifactId>[:<extension>[:<classifier>]][:<version>]

The default repo.remote url is: https://repo1.maven.org/maven2/

''')


def options = cli.parse(args)
if (options == null || options.arguments().size() == 0) {
  cli.usage()
  System.exit(1)
}

RemoteRepository remoteRepo = null
String username = null
String password = null
if (options.r) {
  if (options.u) {
    String[] up = options.u.split(':')
    if (up.size() < 2) {
      System.err.println('colon missing from user option. Could not determine password')
      System.exit(1)
    }
    else if (up.size() > 2) {
      System.err.println('more than one colon detected.')
      System.exit(1)
    }
    username = up[0]
    password = up[1]
  }

  remoteRepo = newRemoteRepository(options.r as String, username, password)
}
else {
  remoteRepo = newCentralRepository()
}

String localRepoPath = options.l ? options.l as String : 'target/local-repo'
LocalRepository localRepo = new LocalRepository(localRepoPath)

println "Local Repo:  $localRepo"
println "Remote Repo: $remoteRepo"
if (remoteRepo.authentication) {
  println "Auth:        $remoteRepo.authentication"
}
println "Download?:   ${!options.n}"
options.arguments().each {
  println "Artifact:    $it"
  new ArtifactResolver(it, remoteRepo, localRepo).resolve(!options.n as boolean)
}





