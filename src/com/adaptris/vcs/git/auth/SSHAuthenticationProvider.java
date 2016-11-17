package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_KEYFILE_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_PASSPHRASE_KEY;

import java.io.File;
import java.util.Properties;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FS;

import com.adaptris.core.fs.FsHelper;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

class SSHAuthenticationProvider extends AuthenticationProviderImpl {

  private String passPhrase;
  
  private File privateKeyFile;

  public SSHAuthenticationProvider(Properties p) throws Exception {
    super(p);
    setPassPhrase(getPasswordProperty(p, VCS_SSH_PASSPHRASE_KEY));
    setPrivateKeyFile(FsHelper
        .createFileReference(FsHelper.createUrlFromString(p.getProperty(VCS_SSH_KEYFILE_URL_KEY), true)));
  }
  
  @Override
  public CredentialsProvider getCredentialsProvider() {
    return null;
  }

  @Override
  ProxySupport createSessionFactory() {
    return new ProxySupport() {
      @Override
      protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch defaultJSch = super.createDefaultJSch(fs);
        try {
          defaultJSch.addIdentity(getPrivateKeyFile().getAbsolutePath(), getPassPhrase());
        }
        catch (JSchException e) {
          throw e;
        }
        catch (Exception e) {
          throw new JSchException(e.getMessage(), e);
        }
        return defaultJSch;
      }
    };
  }

  String getPassPhrase() {
    return passPhrase;
  }

  void setPassPhrase(String passPhrase) {
    this.passPhrase = passPhrase;
  }

  File getPrivateKeyFile() {
    return privateKeyFile;
  }

  void setPrivateKeyFile(File privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }
}
