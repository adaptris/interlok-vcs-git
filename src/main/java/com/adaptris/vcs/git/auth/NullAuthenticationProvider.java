package com.adaptris.vcs.git.auth;

import java.util.Properties;

import org.eclipse.jgit.transport.CredentialsProvider;

class NullAuthenticationProvider extends AuthenticationProviderImpl {

  public NullAuthenticationProvider(Properties p) throws Exception {
    super(p);
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return null;
  }

  @Override
  public String toString() {
    return "Auth:NONE";
  }

}
