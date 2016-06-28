package com.adaptris.vcs.git.auth;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToEmpty;

import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import com.adaptris.core.fs.FsHelper;
import com.adaptris.core.management.properties.PropertyResolver;
import com.adaptris.core.management.vcs.VcsConstants;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.security.password.Password;

public class AuthenticationProviderFactory {

  private enum authenticationImpl {
    UsernamePassword {
      @Override
      AuthenticationProvider create(Properties properties) throws VcsException {
        try {

          AuthenticationProvider authenticationProvider = new UserPassAuthenticationProvider(
              properties.getProperty(VcsConstants.VCS_USERNAME_KEY),
              getPasswordProperty(properties, VcsConstants.VCS_PASSWORD_KEY));
          return authenticationProvider;
        } catch (Exception ex) {
          throw new VcsException(ex);
        }
      }
    },

    SSH {
      @Override
      AuthenticationProvider create(Properties properties) throws VcsException {
        try {
          AuthenticationProvider authenticationProvider = new SSHAuthenticationProvider(
              getPasswordProperty(properties, VcsConstants.VCS_SSH_PASSPHRASE_KEY),
              FsHelper.createFileReference(
                  FsHelper.createUrlFromString(properties.getProperty(VcsConstants.VCS_SSH_KEYFILE_URL_KEY), true)));
          return authenticationProvider;
        } catch (Exception ex) {
          throw new VcsException(ex);
        }
      }
    };

    private static String getPasswordProperty(Properties properties, String name) throws Exception {
      String passwordProp = PropertyResolver.getDefaultInstance().resolve(trimToEmpty(properties.getProperty(name)));
      if (!isEmpty(passwordProp)) {
        return Password.decode(passwordProp);
      }
      return passwordProp;
    }

    abstract AuthenticationProvider create(Properties properties) throws VcsException;
  }

  public AuthenticationProvider createAuthenticationProvider(Properties bootstrapProperties) throws VcsException {
    AuthenticationProvider authenticationProvider = null;

    String authImpl = bootstrapProperties.getProperty(VcsConstants.VCS_AUTHENTICATION_IMPL_KEY);
    if (!StringUtils.isEmpty(authImpl)) {
      try {
        authenticationImpl impl = authenticationImpl.valueOf(authImpl);
        authenticationProvider = impl.create(bootstrapProperties);
      } catch (Exception ex) {
        throw new VcsException("Authentication provider may be misconfigured; '" + authImpl + "'");
      }
    }

    return authenticationProvider;
  }


}
