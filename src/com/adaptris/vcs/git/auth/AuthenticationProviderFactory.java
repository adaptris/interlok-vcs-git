package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_AUTHENTICATION_IMPL_KEY;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.util.Properties;

import com.adaptris.core.management.vcs.VcsException;

public class AuthenticationProviderFactory {

  private enum authenticationImpl {
    UsernamePassword {
      @Override
      AuthenticationProvider create(Properties properties) throws VcsException {
        try {
          AuthenticationProvider authenticationProvider = new UserPassAuthenticationProvider(properties);
          return authenticationProvider;
        }
        catch (Exception ex) {
          throw new VcsException(ex);
        }
      }
    },

    SSH {
      @Override
      AuthenticationProvider create(Properties properties) throws VcsException {
        try {
          AuthenticationProvider authenticationProvider = new SSHAuthenticationProvider(properties);
          return authenticationProvider;
        }
        catch (Exception ex) {
          throw new VcsException(ex);
        }
      }
    };

    abstract AuthenticationProvider create(Properties properties) throws VcsException;
  }

  public AuthenticationProvider createAuthenticationProvider(Properties bootstrapProperties) throws VcsException {
    AuthenticationProvider authenticationProvider = null;
    try {
      authenticationProvider = new NullAuthenticationProvider(bootstrapProperties);
      String authImpl = bootstrapProperties.getProperty(VCS_AUTHENTICATION_IMPL_KEY);
      if (!isEmpty(authImpl)) {
        authenticationImpl impl = authenticationImpl.valueOf(authImpl);
        authenticationProvider = impl.create(bootstrapProperties);
      }
    }
    catch (Exception ex) {
      throw new VcsException("Authentication provider may be misconfigured;");
    }
    return authenticationProvider;
  }

}
