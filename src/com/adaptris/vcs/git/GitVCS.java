package com.adaptris.vcs.git;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_CLEAN_UPDATE;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_LOCAL_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REMOTE_REPO_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REVISION_KEY;
import static org.apache.commons.lang.BooleanUtils.toBoolean;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.fs.FsHelper;
import com.adaptris.core.management.BootstrapProperties;
import com.adaptris.core.management.vcs.RuntimeVersionControl;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.core.management.vcs.VersionControlSystem;
import com.adaptris.vcs.git.api.JGitApi;
import com.adaptris.vcs.git.auth.AuthenticationProvider;
import com.adaptris.vcs.git.auth.AuthenticationProviderFactory;

/**
 * <p>
 * {@link RuntimeVersionControl} implementation specifically built for GIT.
 * </p>
 * 
 * <p>
 * This implementation will allow allow cloning and updating of a local repository. Interlok by
 * itself does not manage repository files, but simply checks them out so that we can start an
 * instance with configuration files that may be checked in.
 * </p>
 * 
 * <p>
 * By dropping this jar file into the classpath of Interlok you will have activated source control
 * cloning via GIT.<br/>
 * However if you do not configure the bootstrap.properties correctly we will skip attempting to
 * clone/update your local repository.
 * </p>
 * 
 * @author amcgrath
 * @since 3.0.3
 * 
 */
public class GitVCS implements RuntimeVersionControl {
  
  protected transient Logger log = LoggerFactory.getLogger(this.getClass());
  
  private static final String VCS_NAME = "Git";
  public static final String VCS_PROXY_TYPE = "vcs.ssh.proxy.type";

  private static final String HARD_RESET_DEFAULT = "false";
  private BootstrapProperties bootstrapProperties;
  
  private transient VersionControlSystem api;
  
  public GitVCS() {
  }
  
  public GitVCS(BootstrapProperties bootstrapProperties) {
    this.setBootstrapProperties(bootstrapProperties);
  }

  @Override
  public String getImplementationName() {
    return VCS_NAME;
  }

  @Override
  public void update() throws VcsException {
    GitConfig config = new GitConfig();
    if (!config.isConfigured()) {
      log.info("GIT: [{}] not configured skipping repository update.", VCS_LOCAL_URL_KEY);
      return;
    }
    log.info("GIT: Checking local repository [{}] ", fullpath(config.getLocalRepo()));
    if (!config.getLocalRepo().exists()) {
      log.info("GIT: [{}] does not exist, performing fresh checkout.", fullpath(config.getLocalRepo()));
      gitCheckout(config);
    }
    gitUpdate(config);
  }

  @Override
  public void checkout() throws VcsException {
    GitConfig config = new GitConfig();
    gitCheckout(config);
    gitUpdate(config);
  }
  
  private void gitCheckout(GitConfig config) throws VcsException {
    if (!config.isConfigured()) {
      log.info("GIT: [{}] or [{}] not configured, skipping checkout.", VCS_LOCAL_URL_KEY, VCS_REMOTE_REPO_URL_KEY);
      return;
    }
    log.info("GIT: Performing checkout to [{}] ", fullpath(config.getLocalRepo()));
    String checkoutRevision = null;
    if (!config.hasRevision()) {
      checkoutRevision = this.api().checkout(config.getRemoteRepo(), config.getLocalRepo());
    } else {
      checkoutRevision = this.api().checkout(config.getRemoteRepo(), config.getLocalRepo(), config.getRevision());
    }
    // log.info("GIT: Checked out configuration to revision: {}", checkoutRevision);
  }

  private void gitUpdate(GitConfig config) throws VcsException {
    if (!config.isConfigured()) {
      log.info("GIT: [{}] not configured skipping repository update.", VCS_LOCAL_URL_KEY);
      return;
    }
    String checkoutRevision = null;
    if (isEmpty(config.getRevision())) {
      checkoutRevision = this.api().update(config.getLocalRepo());
    } else {
      checkoutRevision = this.api().update(config.getLocalRepo(), config.getRevision());
    }
    log.info("GIT: Updated configuration to revision: {}", checkoutRevision);
  }

  private File urlToFile(String url) throws VcsException {
    try {
      return FsHelper.createFileReference(FsHelper.createUrlFromString(url, true));
    } catch (Exception e) {
      throw new VcsException(e);
    }
  }
  
  private String fullpath(File file) {
    String result = file.getAbsolutePath();
    try {
      result = file.getCanonicalPath();
    } catch(IOException e) {
      
    }
    return result;
  }
  
  
  @Override
  public JGitApi getApi(Properties properties) throws VcsException {
    AuthenticationProvider authenticationProvider = new AuthenticationProviderFactory().createAuthenticationProvider(properties);
    boolean force = toBoolean(properties.getProperty(VCS_CLEAN_UPDATE, HARD_RESET_DEFAULT));
    JGitApi api =  new JGitApi(authenticationProvider, force);
    return api;
  }

  @Override
  public void setBootstrapProperties(BootstrapProperties bootstrapProperties) {
    this.bootstrapProperties = bootstrapProperties;
  }
  
  public BootstrapProperties getBootstrapProperties() {
    return this.bootstrapProperties;
  }

  protected VersionControlSystem api() throws VcsException {
    if (this.getApi() == null) {
      this.setApi(this.getApi(getBootstrapProperties()));
    }
    return this.getApi();
  }
  
  VersionControlSystem getApi() {
    return api;
  }

  void setApi(VersionControlSystem api) {
    this.api = api;
  }

  private class GitConfig {
    private String localRepo;
    private String remoteRepo;
    private String revision;

    GitConfig() {
      localRepo = getBootstrapProperties().getProperty(VCS_LOCAL_URL_KEY);
      remoteRepo = getBootstrapProperties().getProperty(VCS_REMOTE_REPO_URL_KEY);
      revision = getBootstrapProperties().getProperty(VCS_REVISION_KEY);
    }

    boolean isConfigured() {
      return localRepo != null && remoteRepo != null;
    }

    boolean hasRevision() {
      return revision != null;
    }

    File getLocalRepo() throws VcsException {
      return urlToFile(localRepo);
    }

    String getRemoteRepo() {
      return remoteRepo;
    }

    String getRevision() {
      return revision;
    }
  }
}
