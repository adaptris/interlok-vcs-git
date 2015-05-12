package com.adaptris.vcs.git.auth;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class UserPassAuthenticationProvider implements AuthenticationProvider {

  private String user;
  
  private String password;
  
  public UserPassAuthenticationProvider() {
  }
  
  public UserPassAuthenticationProvider(String user, String password) {
    this.setUser(user);
    this.setPassword(password);
  }
  
  @Override
  public CredentialsProvider getCredentialsProvider() {
    return new UsernamePasswordCredentialsProvider(getUser(), getPassword());
  }

  @Override
  public TransportConfigCallback getTransportInterceptor() {
    return null;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

}
