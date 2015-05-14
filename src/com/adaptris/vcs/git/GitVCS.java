package com.adaptris.vcs.git;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_LOCAL_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REMOTE_REPO_URL_KEY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_REVISION_KEY;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.fs.FsHelper;
import com.adaptris.core.management.BootstrapProperties;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.core.management.vcs.VersionControlSystem;
import com.adaptris.vcs.git.api.JGitApi;
import com.adaptris.vcs.git.api.VersionControlSystemApi;

public class GitVCS implements VersionControlSystem {
  
  protected transient Logger log = LoggerFactory.getLogger(this.getClass());
  
  private static final String VCS_NAME = "GIT";
  
  private BootstrapProperties bootstrapProperties;
  
  private transient VersionControlSystemApi api;

  @Override
  public String getImplementationName() {
    return VCS_NAME;
  }

  @Override
  public void update() throws VcsException {
    String localRepoUrl = this.getBootstrapProperties().getProperty(VCS_LOCAL_URL_KEY);
    if(localRepoUrl == null)
      log.info("Subversion: " + VCS_LOCAL_URL_KEY + " not configured skipping repository update.");
    else {
      File localRepo = null;
      localRepo = this.urlToFile(localRepoUrl);
      log.info("GIT: Checking local repository; " + this.urlToFile(localRepoUrl).getAbsolutePath());
      
      if(!localRepo.exists()) {
        log.info("GIT: " + this.urlToFile(localRepoUrl).getAbsolutePath() + " does not exist, performing fresh checkout.");
        this.checkout();
        return ;
      }
      
      String revisionValue = this.getBootstrapProperties().getProperty(VCS_REVISION_KEY);
      String checkoutRevision = null;
      if(isEmpty(revisionValue)) {
        checkoutRevision = this.api().update(localRepo);
      } else
        checkoutRevision = this.api().update(localRepo, revisionValue);
      
      log.info("GIT: Updated configuration to revision: {}", checkoutRevision);
    }
  }

  @Override
  public void checkout() throws VcsException {
    String workingCopyUrl = this.getBootstrapProperties().getProperty(VCS_LOCAL_URL_KEY);
    String remoteRepoUrl = this.getBootstrapProperties().getProperty(VCS_REMOTE_REPO_URL_KEY);
    if((workingCopyUrl == null) || (remoteRepoUrl == null))
      log.info("GIT: " + VCS_LOCAL_URL_KEY + " or " + VCS_REMOTE_REPO_URL_KEY + " not configured, skipping checkout.");
    else {
      log.info("GIT: Performing checkout to; " + this.urlToFile(workingCopyUrl).getAbsolutePath()); 

      String revisionValue = this.getBootstrapProperties().getProperty(VCS_REVISION_KEY);
      String checkoutRevision = null;
      if(isEmpty(revisionValue)) {
        checkoutRevision = this.api().checkout(remoteRepoUrl, this.urlToFile(workingCopyUrl));
      } else
        checkoutRevision = this.api().checkout(remoteRepoUrl, this.urlToFile(workingCopyUrl), revisionValue);
      
      log.info("GIT: Checked out configuration to revision: {}", checkoutRevision);
    }
  }
  
  private File urlToFile(String url) throws VcsException {
    try {
      return FsHelper.createFileReference(FsHelper.createUrlFromString(url, true));
    } catch (Exception e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void setBootstrapProperties(BootstrapProperties bootstrapProperties) {
    this.bootstrapProperties = bootstrapProperties;
  }
  
  public BootstrapProperties getBootstrapProperties() {
    return this.bootstrapProperties;
  }

  protected VersionControlSystemApi api() {
    if(this.getApi() == null)
      this.setApi(new JGitApi());
    
    return this.getApi();
  }
  
  public VersionControlSystemApi getApi() {
    return api;
  }

  public void setApi(VersionControlSystemApi api) {
    this.api = api;
  }

}
