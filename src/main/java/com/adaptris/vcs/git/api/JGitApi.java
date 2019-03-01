package com.adaptris.vcs.git.api;

import static com.adaptris.vcs.git.api.PathUtil.toUnixPath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.LogCommand;
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

  private static final String ALL_FILE_PATTERN = ".";
  private static final String REMOTE_ORIGIN_REF = "refs/remotes/origin/%1s";
  private static final String REMOTE_ORIGIN_BRANCH = "origin/%1s";
  private static final String LOCAL_HEAD_BRANCH = "refs/heads/%1s";

  private AuthenticationProvider authenticationProvider;
  private transient boolean hardReset;

  public JGitApi() {
    hardReset = false;
  }

  public JGitApi(boolean resetRepo) {
    hardReset = resetRepo;
  }

  public JGitApi(AuthenticationProvider authenticationProvider, boolean resetRepo) {
    setAuthenticationProvider(authenticationProvider);
    hardReset = resetRepo;
  }

  @Override
  public String testConnection(final String remoteRepoUrl, final File workingCopyUrl) throws VcsException {
    try {
      String revision = null;
      String actualUrl = remoteRepoUrl;
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
    String initialBranch = null;
    log.trace("GIT: Check out [{}]", remoteRepoUrl);
    Git localRepository = null;
    String rev = null;
    try {
      localRepository = gitClone(remoteRepoUrl, workingCopyUrl, initialBranch);
      rev = currentLocalRevision(localRepository);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
  }

  @Override
  public String checkout(String remoteRepoUrl, File workingCopyUrl, String revision) throws VcsException {
    log.trace("GIT: Check out [{}]", remoteRepoUrl);
    Git localRepository = null;
    String rev = null;
    try {
      localRepository = gitClone(remoteRepoUrl, workingCopyUrl, null);
      if (isRemoteBranch(localRepository, revision)) {
        createBranchIfMissing(localRepository, revision);
      }
      gitCheckout(localRepository, revision).call();
      rev = currentLocalRevision(localRepository);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
    return rev;
  }

  @Override
  public String update(File workingCopyUrl, String tagName) throws VcsException {
    Git localRepository = getLocalRepository(workingCopyUrl);
    String rev = null;
    try {
      resetRepository(localRepository, null);
      gitFetch(localRepository).call();
      if (isRemoteBranch(localRepository, tagName)) {
        createBranchIfMissing(localRepository, tagName);
        // Do a checkout to the branch; so that when you pull you don't pull into the current branch.
        gitCheckout(localRepository, tagName, false).call();
        // Now pull any changes, and then checkout again.
        gitPull(localRepository, tagName).call();
        gitCheckout(localRepository, tagName).call();
      } else {
        // If it's not a remote branch, then just pull the changes for the current branch.
        gitPull(localRepository).call();
        gitCheckout(localRepository, tagName).call();
      }
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
    String rev = null;
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      resetRepository(localRepository, null);
      gitFetch(localRepository).call();
      gitPull(localRepository).call();
      gitCheckout(localRepository, null).call();
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
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      RevCommit revCommit = localRepository.commit().setAll(true).setMessage(commitMessage).call();
      push(localRepository, revCommit);
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
    Git localRepository = getLocalRepository(workingCopyUrl);
    try {
      if (fileNames.length > 0) {
        for (String fileName : fileNames) {
          // The file pattern need to use / and not \ so we convert it in case.
          localRepository.add().addFilepattern(toUnixPath(fileName)).call();
        }
        RevCommit revCommit = localRepository.commit().setMessage(commitMessage).call();
        push(localRepository, revCommit);
      }
    } catch (CheckoutConflictException cce) {
      throw new VcsConflictException(cce);
    } catch (GitAPIException e) {
      throw new VcsException(e);
    } finally {
      close(localRepository);
    }
  }

  private boolean isRemoteBranch(Git localRepository, String tagOrBranch) throws GitAPIException {
    String remoteBranchMatch = String.format(REMOTE_ORIGIN_REF, tagOrBranch);
    boolean result = false;
    List<Ref> branches = localRepository.branchList().setListMode(ListMode.ALL).call();
    for (Ref ref : branches) {
      if (remoteBranchMatch.equalsIgnoreCase(ref.getName())) {
        log.trace("GIT: Matched Tag/Branch [{}] against [{}]; assuming branch checkout", tagOrBranch, ref.getName());
        result = true;
      }
    }
    return result;
  }


  private void createBranchIfMissing(Git localRepository, String branchName) throws IOException, GitAPIException {
    String localRef = String.format(LOCAL_HEAD_BRANCH, branchName);
    String remoteRef = String.format(REMOTE_ORIGIN_BRANCH, branchName);
    if (localRepository.getRepository().findRef(localRef) != null) {
      log.debug("GIT: Matched [{}] against existing head [{}]", branchName, localRef);
      // the ref already exists, we don't need to do anything.
    } else {
      log.debug("GIT: Creating new tracked branch [{}] against [{}]", branchName, remoteRef);
      localRepository.branchCreate().setName(branchName).setStartPoint(remoteRef).setUpstreamMode(SetupUpstreamMode.TRACK).call();
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


  private FetchCommand gitFetch(Git localRepository) throws GitAPIException {
    FetchCommand fetcher = localRepository.fetch();
    configureAuthentication(fetcher);
    return fetcher;
  }

  private PullCommand gitPull(Git localRepository) throws GitAPIException, IOException {
    return gitPull(localRepository, localRepository.getRepository().getBranch());
  }

  private PullCommand gitPull(Git localRepository, String branchName) throws GitAPIException, IOException {
    PullCommand pullCommand = localRepository.pull();
    if (branchName != null) {
      pullCommand.setRemoteBranchName(branchName);
    }
    log.trace("GIT: Pulling changes for branch [{}]", branchName);
    configureAuthentication(pullCommand);
    return pullCommand;
  }

  private CheckoutCommand gitCheckout(Git repo, String branchOrTag) throws IOException {
    return gitCheckout(repo, branchOrTag, true);
  }

  private CheckoutCommand gitCheckout(Git repo, String branchOrTag, boolean logging) throws IOException {
    CheckoutCommand cmd = repo.checkout();
    String ref = branchOrTag != null ? branchOrTag : repo.getRepository().getBranch();
    if (logging) {
      log.trace("GIT: Check out to revision/tag/branch [{}]", ref);
    }
    cmd.setName(ref);
    return cmd;
  }


  private void resetRepository(Git repo, String branchOrTag) throws IOException, CheckoutConflictException, GitAPIException {
    if (hardReset) {
      ResetCommand cmd = repo.reset();
      String ref = branchOrTag != null ? branchOrTag : repo.getRepository().getBranch();
      log.trace("GIT: Hard Reset back to revision/tag/branch [{}]", ref);
      cmd.setMode(ResetType.HARD);
      cmd.call();
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
    String rev = null;
    try {
      rev = currentLocalRevision(repo);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(repo);
    }
    return rev;
  }

  @Override
  public String getRemoteRevision(String remoteRepoUrl, File workingCopyUrl) throws VcsException {
    Git repo = getLocalRepository(workingCopyUrl);
    String rev = null;
    try {
      rev = getRemoteRevision(repo);
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(repo);
    }
    return rev;
  }

  private LogCommand gitLogRemoteChanges(Git gitRepo) throws GitAPIException, IOException {
    Repository repo = gitRepo.getRepository();
    Ref currentBranchRef = repo.findRef(repo.getBranch());
    Ref remoteBranchRef = repo.findRef(String.format(REMOTE_ORIGIN_BRANCH, repo.getBranch()));
    return gitRepo.log().addRange(currentBranchRef.getObjectId(), remoteBranchRef.getObjectId());
  }


  private String getRemoteRevision(Git gitRepo) throws GitAPIException, IOException {
    String result = currentLocalRevision(gitRepo);
    Iterable<RevCommit> commits = gitLogRemoteChanges(gitRepo).setMaxCount(1).call();
    for (RevCommit commit : commits) {
      result = commit.getId().getName();
    }
    return result;
  }


  @Override
  public List<RevisionHistoryItem> getRemoteRevisionHistory(String remoteRepoUrl, File workingCopyUrl, int limit)
      throws VcsException {
    List<RevisionHistoryItem> result = new ArrayList<>();
    Git gitRepo = getLocalRepository(workingCopyUrl);
    try {
      result.addAll(toHistoryList(gitLogRemoteChanges(gitRepo).setMaxCount(limit).call()));
      if (result.size() < limit) {
        result.addAll(getLocalRevisions(gitRepo, limit - result.size()));
      }
    } catch (GitAPIException | IOException e) {
      throw new VcsException(e);
    } finally {
      close(gitRepo);
    }
    return result;
  }

  private List<RevisionHistoryItem> getLocalRevisions(Git gitRepo, int limit) throws GitAPIException, IOException {
    ObjectId head = gitRepo.getRepository().resolve(Constants.HEAD);
    return toHistoryList(gitRepo.log().add(head).setMaxCount(limit).call());
  }

  private List<RevisionHistoryItem> toHistoryList(Iterable<RevCommit> commits) {
    List<RevisionHistoryItem> result = new ArrayList<>();
    for (RevCommit commit : commits) {
      result.add(new RevisionHistoryItem(commit.getId().getName(), commit.getFullMessage()));
    }
    return result;
  }

  @SuppressWarnings("rawtypes")
  private TransportCommand configureAuthentication(TransportCommand command) {
    if (getAuthenticationProvider() != null) {
      if (getAuthenticationProvider().getCredentialsProvider() != null) {
        command.setCredentialsProvider(getAuthenticationProvider().getCredentialsProvider());
      }

      if (getAuthenticationProvider().getTransportInterceptor() != null) {
        command.setTransportConfigCallback(getAuthenticationProvider().getTransportInterceptor());
      }
    }
    return command;
  }

  private Git getLocalRepository(File localRepoDir) throws VcsException {
    return getRepository(localRepoDir, false);
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

  private String currentLocalRevision(Git repository) throws GitAPIException, IOException {
    String fullBranch = repository.getRepository().getFullBranch();
    ObjectId resolvedRevision = repository.getRepository().resolve(fullBranch);
    return resolvedRevision.getName();
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
    if (repo != null) {
      repo.close();
    }
  }

}
