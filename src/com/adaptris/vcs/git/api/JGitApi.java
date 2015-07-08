package com.adaptris.vcs.git.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.management.vcs.RevisionHistoryItem;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.core.management.vcs.VersionControlSystem;
import com.adaptris.vcs.git.auth.AuthenticationProvider;

public class JGitApi implements VersionControlSystem {

  protected transient Logger log = LoggerFactory.getLogger(this.getClass());

  private static final String IMPL_NAME = "Git";

  private static final String COPY_CLONE_POSTFIX = "_copy_doNotEdit";

  private static final String ALL_FILE_PATTERN = ".";

  private AuthenticationProvider authenticationProvider;

  public JGitApi() {
  }

  public JGitApi(AuthenticationProvider authenticationProvider) {
    setAuthenticationProvider(authenticationProvider);
  }

  @Override
  public String testConnection(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    try {
      String revision = null;
      LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository().setHeads(false).setTags(false).setRemote(remoteRepoUrl);
      configureAuthentication(lsRemoteCommand);
      Collection<Ref> refs = lsRemoteCommand.call();
      for (Ref ref : refs) {
        if (Constants.HEAD.equals(ref.getName())) {
          revision = ref.getObjectId().getName();
          break;
        }
      }
      return revision;
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    // Now make a copy for our querying purposes.  We will also use this to update from.
    // This copy will always be up to date, regardless of the tag the user may have chosen.
    checkoutCopy(remoteRepoUrl, workingCopyUrl);

    Git localRepository = null;
    try {
      CloneCommand cloneCommand = Git.cloneRepository().setURI(getLocalCloneCopy(workingCopyUrl).getAbsolutePath())
          .setDirectory(workingCopyUrl);
      configureAuthentication(cloneCommand);
      localRepository = cloneCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      if (localRepository != null) {
        localRepository.close();
      }
    }
    return getLocalRevision(workingCopyUrl);
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl, String revision) throws VcsException {
    this.checkout(remoteRepoUrl, workingCopyUrl);
    return this.update(workingCopyUrl, revision);
  }

  @Override
  public String update(File workingCopyUrl, String tagName) throws VcsException {
    updateCopy(workingCopyUrl); // get all branches and tags first in our copy
    try {
      CheckoutCommand checkoutCommand = getLocalRepository(workingCopyUrl).checkout().setName(tagName);
      checkoutCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
    return getLocalRevision(workingCopyUrl);
  }

  @Override
  public String update(File workingCopyUrl) throws VcsException {
    updateCopy(workingCopyUrl); // get all branches and tags first in our copy
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      PullCommand pullCommand = localRepository.pull();
      configureAuthentication(pullCommand);
      pullCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      localRepository.close();
    }
    return getLocalRevision(workingCopyUrl);
  }

  @Override
  public void commit(File workingCopyUrl, String commitMessage) throws VcsException {
    // We update the copy so it reflects the remote repository
    // Committing to the copy will return the same result as committing to the remote repository
    updateCopy(workingCopyUrl);
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      RevCommit revCommit = localRepository.commit().setAll(true).setMessage(commitMessage).call();
      push(localRepository, revCommit);

      // Now push to the actual GIT server from our copy of the clone.
      pushCopy(workingCopyUrl);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      localRepository.close();
    }
  }

  @Override
  public void addAndCommit(File workingCopyUrl, String commitMessage, String... fileNames) throws VcsException {
    // We update the copy so it reflects the remote repository
    // Committing to the copy will return the same result as committing to the remote repository
    updateCopy(workingCopyUrl);
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      if (fileNames.length > 0) {
        for (String fileName : fileNames) {
          localRepository.add().addFilepattern(fileName).call();
        }

        RevCommit revCommit = localRepository.commit().setMessage(commitMessage).call();
        push(localRepository, revCommit);

        // Now push to the actual GIT server from our copy of the clone.
        pushCopy(workingCopyUrl);
      }
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      localRepository.close();
    }
  }

  private void push(Git localRepository, RevCommit revCommit) throws GitAPIException, CheckoutConflictException, VcsException {
    try {
      // This will push to our copy of the clone.
      pushCommand(localRepository);
    } catch (VcsException vcse) {
      resetCommit(localRepository, revCommit);
      throw vcse;
    }
  }

  private void resetCommit(Git localRepository, RevCommit revCommit) throws GitAPIException, CheckoutConflictException {
    String resetRevision = Constants.HEAD;
    if (revCommit.getParentCount() > 0) {
      resetRevision = revCommit.getParent(0).getName();
    }
    ResetCommand resetCommand = localRepository.reset();
    resetCommand.setMode(ResetType.MIXED).setRef(resetRevision).call();
  }

  @Override
  public void recursiveAdd(File workingCopyUrl) throws VcsException {
    try {
      getLocalRepository(workingCopyUrl).add().addFilepattern(ALL_FILE_PATTERN).call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public String getImplementationName() {
    return IMPL_NAME;
  }

  @Override
  public String getLocalRevision(File workingCopyUrl) throws VcsException {
    return currentLocalRevision(getLocalRepository(workingCopyUrl));
  }

  @Override
  public String getRemoteRevision(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    updateCopy(workingCopyUrl);
    return currentLocalRevision(getBareRepository(getLocalCloneCopy(workingCopyUrl)));
  }

  @Override
  public List<RevisionHistoryItem> getRemoteRevisionHistory(String remoteRepoUrl, File workingCopyUrl, int limit) throws VcsException {
    List<RevisionHistoryItem> returnedsRevisions = new ArrayList<>();
    updateCopy(workingCopyUrl); // get all commits etc into our copy, then querty that, without modifying the actual working copy.

    Git bareRepository = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    try {
      ObjectId head = bareRepository.getRepository().resolve(Constants.HEAD);
      Iterable<RevCommit> commits = bareRepository.log().add(head).setMaxCount(limit).call();

      for(RevCommit commit : commits) {
        RevisionHistoryItem item = new RevisionHistoryItem();
        item.setComment(commit.getFullMessage());
        item.setRevision(commit.getId().getName());
        returnedsRevisions.add(item);
      }
    } catch (IOException | GitAPIException ex) {
      throw new VcsException(ex);
    } finally {
      bareRepository.close();
    }
    return returnedsRevisions;
  }

  @SuppressWarnings("rawtypes")
  private void configureAuthentication(TransportCommand command) {
    if(getAuthenticationProvider() != null) {
      if(getAuthenticationProvider().getCredentialsProvider() != null) {
        command.setCredentialsProvider(getAuthenticationProvider().getCredentialsProvider());
      }

      if(getAuthenticationProvider().getTransportInterceptor() != null) {
        command.setTransportConfigCallback(getAuthenticationProvider().getTransportInterceptor());
      }
    }
  }

  private Git getLocalRepository(File localRepoDir) throws VcsException {
    return getRepository(localRepoDir, false);
  }

  private Git getBareRepository(File localRepoDir) throws VcsException {
    return getRepository(localRepoDir, true);
  }

  private Git getRepository(File localRepoDir, boolean bare) throws VcsException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    Repository repository;
    try {
      if (bare) {
        builder.setBare();
        builder.setGitDir(localRepoDir);
      } else {
        builder.setWorkTree(localRepoDir);
      }
      repository = builder.setup().build();
    } catch (IOException e) {
      throw new VcsException(e);
    }

    return new Git(repository);
  }

  private String currentLocalRevision(Git repository) throws VcsException {
    ObjectId resolvedRevision;
    try {
      String fullBranch = repository.getRepository().getFullBranch();
      resolvedRevision = repository.getRepository().resolve(fullBranch);
    } catch (RevisionSyntaxException | IOException e) {
      throw new VcsException(e);
    }
    return resolvedRevision.getName();
  }

  /**
   * This creates a copy of the local working repository.
   * It's used to pull all tags, commits etc so we can query it without actually modifying the
   * real working copy url.  We do it this way, because we cannot query the server side, we
   * have to keep a local working copy completely up to date.
   * @param remoteRepoUrl
   * @param workingCopyUrl
   * @throws VcsException
   */
  private void checkoutCopy(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    Git bareRepository = null;
    try {
      CloneCommand cloneCommand = Git.cloneRepository().setURI(remoteRepoUrl).setDirectory(getLocalCloneCopy(workingCopyUrl)).setBare(true);
      configureAuthentication(cloneCommand);
      bareRepository = cloneCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      if (bareRepository != null) {
        bareRepository.close();
      }
    }
  }

  private void updateCopy(File workingCopyUrl) throws VcsException {
    Git bareRepository = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    try {
      // The copy repo is a bare repo so we can only do fetch and not pull
      FetchCommand fetchCommand = bareRepository.fetch();
      configureAuthentication(fetchCommand);
      fetchCommand.call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      bareRepository.close();
    }
  }

  private File getLocalCloneCopy(File workingCopyFile) {
    File copyCloneFile = null;
    String workingCopyDirName = workingCopyFile.getName();
    File workingCopyFileParent = workingCopyFile.getParentFile();

    copyCloneFile = new File(workingCopyFileParent, workingCopyDirName + COPY_CLONE_POSTFIX);
    return copyCloneFile;
  }

  private void pushCopy(File workingCopyUrl) throws VcsException {
    // This will push our copy of the clone to the GIT server
    Git bareRepository = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    try {
      pushCommand(bareRepository);
    } finally {
      bareRepository.close();
    }
  }

  private void pushCommand(Git repository) throws VcsException {
    PushCommand pushCommand = repository.push();
    configureAuthentication(pushCommand);
    try {
      Iterable<PushResult> pushResults = pushCommand.call();
      processPushResults(pushResults);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    }
  }

  private void processPushResults(Iterable<PushResult> results) throws VcsException {
    for (PushResult pushResult : results) {
      processPushResult(pushResult);
    }
  }

  private void processPushResult(PushResult pushResult) throws VcsException {
    Collection<RemoteRefUpdate> remoteUpdates = pushResult.getRemoteUpdates();
    for (RemoteRefUpdate remoteRefUpdate : remoteUpdates) {
      Status status = remoteRefUpdate.getStatus();
      // If at least one result is not OK or UP_TO_DATE we throw an exception
      if (!status.equals(RemoteRefUpdate.Status.OK) && !status.equals(RemoteRefUpdate.Status.UP_TO_DATE)) {
        String message = "The push failed with status [" + status + "] and message [" + remoteRefUpdate.getMessage() + "]";
        log.debug(message);
        throw new VcsException(message);
      }
    }
  }

  AuthenticationProvider getAuthenticationProvider() {
    return authenticationProvider;
  }

  void setAuthenticationProvider(
      AuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

}
