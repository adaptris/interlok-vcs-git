package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_AUTHENTICATION_IMPL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_PASSWORD_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REMOTE_REPO_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_KEYFILE_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_USERNAME_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.adaptris.core.management.vcs.VcsException;

public class AuthenticationProviderFactoryTest {

  @Test
  public void testCreateAuthProviderNoneDefault() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(new Properties());

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderNone() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "git://host");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderWithImplementationKey() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_AUTHENTICATION_IMPL_KEY, "UsernamePassword");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:Username+Password", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderUsernamePasswordHttp() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "http://host");
    bootstrapProperties.put(VCS_USERNAME_KEY, "username");
    bootstrapProperties.put(VCS_PASSWORD_KEY, "password");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:Username+Password", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderUsernamePasswordHttpNoCredentials() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "http://host");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderUsernamePasswordHttps() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "https://host");
    bootstrapProperties.put(VCS_USERNAME_KEY, "username");
    bootstrapProperties.put(VCS_PASSWORD_KEY, "password");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:Username+Password", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderUsernamePasswordHttpsNoCredentials() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "https://host");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderSshKey() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "git@host");
    bootstrapProperties.put(VCS_SSH_KEYFILE_URL_KEY, "file://path/to/key");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:SSH+KEY", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderSshKeyNoKeyFile() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "git@host");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderSshKeySshProtocol() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "ssh://host");
    bootstrapProperties.put(VCS_SSH_KEYFILE_URL_KEY, "file://path/to/key");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:SSH+KEY", authenticationProvider.toString());
  }

  @Test
  public void testCreateAuthProviderSshKeySshProtocolNoKeyFile() throws VcsException {
    AuthenticationProviderFactory factory = new AuthenticationProviderFactory();

    Properties bootstrapProperties = new Properties();
    bootstrapProperties.put(VCS_REMOTE_REPO_URL_KEY, "ssh://host");

    AuthenticationProvider authenticationProvider = factory.createAuthenticationProvider(bootstrapProperties);

    assertEquals("Auth:NONE", authenticationProvider.toString());
  }

}
