package com.adaptris.vcs.git.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
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
import com.adaptris.core.management.vcs.VcsConflictException;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.core.management.vcs.VersionControlSystem;
import com.adaptris.vcs.git.auth.AuthenticationProvider;

public class JGitApi implements VersionControlSystem {

  protected transient Logger log = LoggerFactory.getLogger(this.getClass());

  private static final String IMPL_NAME = "Git";

  private static final String LOCAL_COPY_FORMAT = ".%1s_cacheCopy_doNotEdit";

  private static final String ALL_FILE_PATTERN = ".";

  // Try and support the 2 styles of URL
  // git@bitbucket.org:adaptris/hgca-interlok?initial.branch=staging
  // https://lewinc@bitbucket.org/adaptris/hgca-interlok.git?initial.branch=staging
  private static final String INITIAL_BRANCH_REGEXP = "^(.*)\\?initial.branch=(.*)$";
  private static final int URI_GROUP = 1;
  private static final int BRANCH_GROUP = 2;

  private AuthenticationProvider authenticationProvider;
  private transient Pattern branchPattern;

  public JGitApi() {
    branchPattern = Pattern.compile(INITIAL_BRANCH_REGEXP);
  }

  public JGitApi(AuthenticationProvider authenticationProvider) {
    setAuthenticationProvider(authenticationProvider);
  }

  @Override
  public String testConnection(final String remoteRepoUrl, final File workingCopyUrl) throws VcsException {
    try {
      String revision = null;
      String actualUrl = actualUrl(remoteRepoUrl);
      LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository().setHeads(false).setTags(false).setRemote(actualUrl);
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
    String actualUrl = actualUrl(remoteRepoUrl);
    String initialBranch = initialBranch(remoteRepoUrl);
    log.trace("GIT: Check out [{}] with branch [{}]", actualUrl, initialBranch == null ? "master" : initialBranch);
    // Now make a copy for our querying purposes. We will also use this to update from.
    // This copy will always be up to date, regardless of the tag the user may have chosen.
    File cachedClone = checkoutCopy(actualUrl, workingCopyUrl);
    Git localRepository = null;
    String rev = null;
    try {
      localRepository = gitClone(cachedClone.getAbsolutePath(), workingCopyUrl, initialBranch);
      rev = currentLocalRevision(localRepository);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl, String revision) throws VcsException {
    String actualUrl = actualUrl(remoteRepoUrl);
    String initialBranch = initialBranch(remoteRepoUrl);
    log.trace("GIT: Check out [{}] with branch [{}]", actualUrl, initialBranch == null ? "master" : initialBranch);
    File cachedClone = checkoutCopy(actualUrl, workingCopyUrl);
    Git localRepository = null;
    String rev = null;
    try {
      localRepository = gitClone(cachedClone.getAbsolutePath(), workingCopyUrl, initialBranch);
      gitCheckoutCommand(localRepository, revision).call();
      rev = currentLocalRevision(localRepository);
    } catch (GitAPIException|IOException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
  }

  @Override
  public String update(File workingCopyUrl, String tagName) throws VcsException {
    updateCopy(workingCopyUrl); // get all branches and tags first in our copy
    Git localRepository = getLocalRepository(workingCopyUrl);
    String rev = null;
    try {
      gitPull(localRepository).call();
      gitCheckoutCommand(localRepository, tagName).call();
      rev = currentLocalRevision(localRepository);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
  }

  @Override
  public String update(File workingCopyUrl) throws VcsException {
    updateCopy(workingCopyUrl); // get all branches and tags first in our copy
    String rev = null;
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      gitPull(localRepository).call();
      gitCheckoutCommand(localRepository, null).call();
      rev = currentLocalRevision(localRepository);
    } catch (CheckoutConflictException cce) {
      throw new VcsConflictException(cce);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
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
    } catch (CheckoutConflictException cce) {
      throw new VcsConflictException(cce);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
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
    } catch (CheckoutConflictException cce) {
      throw new VcsConflictException(cce);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
  }

  private Git gitClone(String remoteRepo, File localRepo, String initialBranch)
      throws InvalidRemoteException, TransportException, GitAPIException {
    CloneCommand cloneCommand = Git.cloneRepository().setURI(remoteRepo).setDirectory(localRepo);
    if (initialBranch != null) {
      cloneCommand = cloneCommand.setBranch(initialBranch);
    }
    configureAuthentication(cloneCommand);
    return cloneCommand.call();
  }


  private PullCommand gitPull(Git localRepository) throws GitAPIException, IOException {
    String branch = localRepository.getRepository().getBranch();
    PullCommand pullCommand = localRepository.pull();
    if (branch != null) {
      pullCommand.setRemoteBranchName(branch);
    }
    log.trace("GIT: Pulling changes from origin for branch [{}]", branch);
    configureAuthentication(pullCommand);
    return pullCommand;
  }
  
  private CheckoutCommand gitCheckoutCommand(Git repo, String branchOrTag) throws IOException {
    CheckoutCommand cmd = repo.checkout();
    String ref = branchOrTag != null ? branchOrTag :repo.getRepository().getBranch();
    log.trace("GIT: Check out to revision/tag/branch [{}]", ref);          
    cmd.setName(ref);
    return cmd;
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
    Git repo = getLocalRepository(workingCopyUrl);
    try {
      repo.add().addFilepattern(ALL_FILE_PATTERN).call();
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      close(repo);
    }
  }

  @Override
  public String getImplementationName() {
    return IMPL_NAME;
  }

  @Override
  public String getLocalRevision(File workingCopyUrl) throws VcsException {
    Git repo = getLocalRepository(workingCopyUrl);
    String rev = currentLocalRevision(repo);
    close(repo);
    return rev;
  }

  @Override
  public String getRemoteRevision(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    updateCopy(workingCopyUrl);
    Git repo = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    String rev = currentLocalRevision(repo);
    close(repo);
    return rev;
  }

  @Override
  public List<RevisionHistoryItem> getRemoteRevisionHistory(String remoteRepoUrl, File workingCopyUrl, int limit)
      throws VcsException {
    List<RevisionHistoryItem> returnedsRevisions = new ArrayList<>();
    updateCopy(workingCopyUrl); // get all commits etc into our copy, then querty that, without modifying the actual working copy.

    Git bareRepository = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    try {
      ObjectId head = bareRepository.getRepository().resolve(Constants.HEAD);
      Iterable<RevCommit> commits = bareRepository.log().add(head).setMaxCount(limit).call();

      for (RevCommit commit : commits) {
        RevisionHistoryItem item = new RevisionHistoryItem();
        item.setComment(commit.getFullMessage());
        item.setRevision(commit.getId().getName());
        returnedsRevisions.add(item);
      }
    } catch (IOException | GitAPIException ex) {
      throw new VcsException(ex);
    } finally {
      close(bareRepository);
    }
    return returnedsRevisions;
  }

  @SuppressWarnings("rawtypes")
  private void configureAuthentication(TransportCommand command) {
    if (getAuthenticationProvider() != null) {
      if (getAuthenticationProvider().getCredentialsProvider() != null) {
        command.setCredentialsProvider(getAuthenticationProvider().getCredentialsProvider());
      }

      if (getAuthenticationProvider().getTransportInterceptor() != null) {
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
    String rev = null;
    try {
      String fullBranch = repository.getRepository().getFullBranch();
      ObjectId resolvedRevision = repository.getRepository().resolve(fullBranch);
      rev = resolvedRevision.getName();
    } catch (RevisionSyntaxException | IOException e) {
      throw new VcsException(e);
    }
    return rev;
  }

  /**
   * This creates a copy of the local working repository.
   * It's used to pull all tags, commits etc so we can query it without actually modifying the
   * real working copy url. We do it this way, because we cannot query the server side, we
   * have to keep a local working copy completely up to date.
   * @param remoteRepoUrl
   * @param workingCopyUrl
   * @throws VcsException
   */
  private File checkoutCopy(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    Git bareRepository = null;
    File cacheClone = null;
    try {
      cacheClone =  getLocalCloneCopy(workingCopyUrl).getCanonicalFile();
      if (cacheClone.exists()) {
        log.trace("GIT: Cached clone already exists [{}], updating", fullpath(cacheClone));
        updateCopy(workingCopyUrl);
      } else {
        CloneCommand cloneCommand = Git.cloneRepository().setURI(remoteRepoUrl).setDirectory(getLocalCloneCopy(workingCopyUrl))
            .setCloneAllBranches(true).setBare(true);
        configureAuthentication(cloneCommand);
        bareRepository = cloneCommand.call();        
      }
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(bareRepository);
    }
    return cacheClone;
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
      close(bareRepository);
    }
  }

  private File getLocalCloneCopy(File workingCopyFile) {
    String workingCopyDirName = workingCopyFile.getName();
    File workingCopyFileParent = workingCopyFile.getParentFile();
    String filename = String.format(LOCAL_COPY_FORMAT, workingCopyDirName);        
    return new File(workingCopyFileParent, filename);
  }

  private void pushCopy(File workingCopyUrl) throws VcsException {
    // This will push our copy of the clone to the GIT server
    Git bareRepository = getBareRepository(getLocalCloneCopy(workingCopyUrl));
    try {
      pushCommand(bareRepository);
    } finally {
      close(bareRepository);
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
        throw new VcsConflictException(message);
      }
    }
  }

  AuthenticationProvider getAuthenticationProvider() {
    return authenticationProvider;
  }

  void setAuthenticationProvider(AuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

  private void close(Git repo) {
    if (repo != null)
      repo.close();
  }

  private String initialBranch(String remoteRepoUrl) {
    String result = null;
    Matcher m = branchPattern.matcher(remoteRepoUrl);
    if (m.matches()) {
      result = m.group(BRANCH_GROUP);
    }
    return result;
  }

  private String actualUrl(String remoteRepoUrl) {
    String result = null;
    Matcher m = branchPattern.matcher(remoteRepoUrl);
    if (m.matches()) {
      result = m.group(URI_GROUP);
    }
    return result;
  }
  
  private String fullpath(File file) {
    String result = file.getAbsolutePath();
    try {
      result = file.getCanonicalPath();
    } catch(IOException e) {
      
    }
    return result;
  }
  
}
