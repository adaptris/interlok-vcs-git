package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_AUTHENTICATION_IMPL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_PASSWORD_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REMOTE_REPO_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_KEYFILE_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_USERNAME_KEY;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.management.vcs.VcsException;

public class AuthenticationProviderFactory {

  private static transient Logger log = LoggerFactory.getLogger(AuthenticationProviderFactory.class);

  private enum Auth {
    None {
      @Override
      AuthenticationProvider create(Properties p) throws Exception {
        return new NullAuthenticationProvider(p);
      }

    },
    UsernamePassword {
      @Override
      AuthenticationProvider create(Properties p) throws Exception {
        return new UserPassAuthenticationProvider(p);
      }
    },

    SSH {
      @Override
      AuthenticationProvider create(Properties p) throws Exception {
        return new SSHAuthenticationProvider(p);
      }
    };

    abstract AuthenticationProvider create(Properties properties) throws Exception;
  }

  private enum ProtocolAuthMapper {

    // http://github + username / password
    HTTP(Auth.UsernamePassword) {
      @Override
      boolean matches(Properties p) {
        String remoteRepo = p.getProperty(VCS_REMOTE_REPO_URL_KEY, "");
        return remoteRepo.startsWith("http://") && containsAllKeys(p, VCS_USERNAME_KEY, VCS_PASSWORD_KEY);
      }
    },
    // https://github + username / password
    HTTPS(Auth.UsernamePassword) {
      @Override
      boolean matches(Properties p) {
        String remoteRepo = p.getProperty(VCS_REMOTE_REPO_URL_KEY, "");
        return remoteRepo.startsWith("https://") && containsAllKeys(p, VCS_USERNAME_KEY, VCS_PASSWORD_KEY);
      }
    },
    // git:// has no auth (port 9418).
    GIT(Auth.None) {
      @Override
      boolean matches(Properties p) {
        String remoteRepo = p.getProperty(VCS_REMOTE_REPO_URL_KEY, "");
        return remoteRepo.startsWith("git://");
      }
    },
    // git@github.com uses ssh.
    GIT_SSH(Auth.SSH) {
      @Override
      boolean matches(Properties p) {
        String remoteRepo = p.getProperty(VCS_REMOTE_REPO_URL_KEY, "");
        return remoteRepo.startsWith("git@") && containsAllKeys(p, VCS_SSH_KEYFILE_URL_KEY);
      }
    },
    // ssh://git@github.com uses ssh.
    SSH(Auth.SSH) {
      @Override
      boolean matches(Properties p) {
        String remoteRepo = p.getProperty(VCS_REMOTE_REPO_URL_KEY, "");
        return remoteRepo.startsWith("ssh://") && containsAllKeys(p, VCS_SSH_KEYFILE_URL_KEY);
      }
      
    };
    private Auth auth;

    ProtocolAuthMapper(Auth a) {
      this.auth = a;
    }

    Auth auth() {
      return auth;
    }

    abstract boolean matches(Properties p);
  }

  public AuthenticationProvider createAuthenticationProvider(Properties bootstrapProperties) throws VcsException {
    AuthenticationProvider result = null;
    try {
      String authImpl = bootstrapProperties.getProperty(VCS_AUTHENTICATION_IMPL_KEY, "");
      if (!isEmpty(authImpl)) {
        log.trace("Explicit authentication implementation requested : {}", authImpl);
        result = Auth.valueOf(authImpl).create(bootstrapProperties);
      }
      else {
        log.trace("Attempting to derive authentication implementation");
        result = derive(bootstrapProperties);
      }
    }
    catch (Exception ex) {
      throw new VcsException("Authentication provider may be misconfigured;", ex);
    }
    log.trace("Authentication implementation : {}", result);
    return result;
  }

  private AuthenticationProvider derive(Properties props) throws Exception {
    AuthenticationProvider result = Auth.None.create(props);
    for (ProtocolAuthMapper pa : ProtocolAuthMapper.values()) {
      if (pa.matches(props)) {
        result = pa.auth().create(props);
        break;
      }
    }
    return result;
  }

  private static boolean containsAllKeys(Properties p, String... keys) {
    int rc = 0;
    for (String k : keys) {
      rc += p.containsKey(k) ? 1 : 0;
    }
    return rc == keys.length;
  }
}
