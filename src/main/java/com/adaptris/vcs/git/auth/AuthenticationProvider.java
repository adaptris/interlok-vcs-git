package com.adaptris.vcs.git.auth;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;

public interface AuthenticationProvider {

  public CredentialsProvider getCredentialsProvider();
  
  public TransportConfigCallback getTransportInterceptor();
  
}
