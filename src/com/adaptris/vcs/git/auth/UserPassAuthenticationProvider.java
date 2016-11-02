package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_PASSWORD_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_USERNAME_KEY;

import java.util.Properties;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

class UserPassAuthenticationProvider extends AuthenticationProviderImpl {

  private String user;
  
  private String password;
  
  public UserPassAuthenticationProvider(Properties p) throws Exception {
    super(p);
    setUser(p.getProperty(VCS_USERNAME_KEY));
    setPassword(getPasswordProperty(p, VCS_PASSWORD_KEY));
  }
  
  
  @Override
  public CredentialsProvider getCredentialsProvider() {
    return new UsernamePasswordCredentialsProvider(getUser(), getPassword());
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
