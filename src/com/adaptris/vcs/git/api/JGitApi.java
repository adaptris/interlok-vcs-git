package com.adaptris.vcs.git.api;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.vcs.git.auth.AuthenticationProvider;

public class JGitApi implements VersionControlSystemApi {
  
  private AuthenticationProvider authenticationProvider;
  
  public JGitApi() {
  }
  
  public JGitApi(AuthenticationProvider authenticationProvider) {
    this.setAuthenticationProvider(authenticationProvider);
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    try {
      CloneCommand cloneCommand = Git.cloneRepository().setURI(remoteRepoUrl).setDirectory(workingCopyUrl);
      configureAuthentication(cloneCommand);
      cloneCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
    return this.currentLocalRevision(workingCopyUrl);
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl, String revision) throws VcsException {
    this.checkout(remoteRepoUrl, workingCopyUrl);
    return this.update(workingCopyUrl, revision);
  }

  @Override
  public String update(File workingCopyUrl, String tagName) throws VcsException {
    try {
      CheckoutCommand checkoutCommand = this.getLocalRepository(workingCopyUrl).checkout().setName(tagName);
      checkoutCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
    return this.currentLocalRevision(workingCopyUrl);
  }

  @Override
  public String update(File workingCopyUrl) throws VcsException {
    try {
      PullCommand pullCommand = this.getLocalRepository(workingCopyUrl).pull();
      this.configureAuthentication(pullCommand);
      pullCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
    return this.currentLocalRevision(workingCopyUrl);
  }

  @Override
  public void commit(File workingCopyUrl, String commitMessage) throws VcsException {
    try {
      Git localRepository = this.getLocalRepository(workingCopyUrl);
      localRepository.commit().setAll(true).setMessage(commitMessage).call();
      
      PushCommand pushCommand = localRepository.push();
      this.configureAuthentication(pushCommand);
      pushCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void recursiveAdd(File workingCopyUrl) throws VcsException {
    try {
      this.getLocalRepository(workingCopyUrl).add().call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
  }
  
  @SuppressWarnings("rawtypes")
  private void configureAuthentication(TransportCommand command) {
    if(this.getAuthenticationProvider() != null) {
      if(this.getAuthenticationProvider().getCredentialsProvider() != null)
        command.setCredentialsProvider(this.getAuthenticationProvider().getCredentialsProvider());

      if(this.getAuthenticationProvider().getTransportInterceptor() != null)
        command.setTransportConfigCallback(this.getAuthenticationProvider().getTransportInterceptor());
    }
  }
  
  private Git getLocalRepository(File localRepoDir) throws VcsException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    Repository repository;
    try {
      repository = builder.setGitDir(localRepoDir)
        .readEnvironment()
        .findGitDir()
        .build();
    } catch (IOException e) {
      throw new VcsException(e);
    }
    
    return new Git(repository);
  }
  
  private String currentLocalRevision(File workingCopyUrl) throws VcsException {
    Git localRepository = this.getLocalRepository(workingCopyUrl);
    ObjectId resolvedRevision;
    try {
      resolvedRevision = localRepository.getRepository().resolve(localRepository.getRepository().getFullBranch());
    } catch (RevisionSyntaxException | IOException e) {
      throw new VcsException(e);
    }
    return resolvedRevision.getName();
  }

  public AuthenticationProvider getAuthenticationProvider() {
    return authenticationProvider;
  }

  public void setAuthenticationProvider(
      AuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

}
