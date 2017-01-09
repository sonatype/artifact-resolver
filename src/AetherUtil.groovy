/*
 * Copyright (c) 2017-present Sonatype, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.repository.AuthenticationBuilder


class AetherUtil
{

  static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, LocalRepository localRepo )
  {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()

    session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) )

    session.setTransferListener( new ConsoleTransferListener() )
    session.setRepositoryListener( new ConsoleRepositoryListener() )

    // uncomment to generate dirty trees
    // session.setDependencyGraphTransformer( null );

    return session
  }

  static List<RemoteRepository> newRepositories(RemoteRepository remoteRepository)
  {
    return new ArrayList<RemoteRepository>( Arrays.asList( remoteRepository != null ? remoteRepository : newCentralRepository() ) )
  }

  static RemoteRepository newCentralRepository()
  {
    return new RemoteRepository.Builder( "central", "default", "https://repo1.maven.org/maven2/" ).build()
  }

  static RemoteRepository newRemoteRepository(String url, String user, String pass){

    RemoteRepository.Builder builder = new RemoteRepository.Builder( "custom", "default", url )

    if(user != null && pass != null){
      Authentication auth = new AuthenticationBuilder().addUsername(user).addPassword(pass).build()
      builder.setAuthentication(auth)
    }

    return builder.build()

  }

  static RepositorySystem newRepositorySystem()
  {
    /*
     * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
     * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
     * factories.
     */
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class )
    locator.addService( TransporterFactory.class, FileTransporterFactory.class )
    locator.addService( TransporterFactory.class, HttpTransporterFactory.class )

    locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
    {
      @Override
      void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception )
      {
        exception.printStackTrace()
      }
    } )

    return locator.getService( RepositorySystem.class )
  }

}
