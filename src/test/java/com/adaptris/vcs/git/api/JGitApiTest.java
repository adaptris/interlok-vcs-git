package com.adaptris.vcs.git.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class JGitApiTest {

  private static final String README_TXT = "README.TXT";

  @Rule
  public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }


  private File createAndDeleteTempFile(File dir) throws IOException {
    File result = null;
    if (dir != null) {
      result = File.createTempFile(getClass().getSimpleName(), "", dir);
    } else {
      result = File.createTempFile(getClass().getSimpleName(), "");
    }
    result.delete();
    return result;
  }

  @Test
  public void testGetImplementationName() {
    assertEquals("Git", new JGitApi().getImplementationName());
  }

  @Test
  public void testTestConnection() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    try {
      JGitApi api = new JGitApi();
      String testConnectionRev = api.testConnection(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, testConnectionRev);
    } finally {
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }


  @Test
  public void testCheckout() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    try {
      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);
    } finally {
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }

  @Test
  public void testCheckoutRevision() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    JGitApi api = new JGitApi();
    try {
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir, initialRev);
      assertEquals(initialRev, checkoutRev);
    } finally {
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }

  @Test
  public void testUpdate() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);

      String updateRev = addFile(git, testName.getMethodName(), generateContent());

      String apiRev = api.update(checkoutDir);
      assertEquals(updateRev, apiRev);
    } finally {
      close(git);
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }

  @Test
  public void testUpdateToRevision() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);

      String newRev = addFile(git, testName.getMethodName(), generateContent());

      // Do an update to move us past the initial revision.
      api.update(checkoutDir);
      String apiRev = api.update(checkoutDir, initialRev);
      assertEquals(initialRev, apiRev);
    } finally {
      close(git);
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }

  @Test
  public void testAddAndCommit() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);
      File newFile = createAndDeleteTempFile(checkoutDir);
      FileUtils.write(newFile, generateContent());
      api.addAndCommit(checkoutDir, testName.getMethodName(), newFile.getName());
      String apiRev = api.getLocalRevision(checkoutDir);
      assertNotSame(initialRev, apiRev);
      assertEquals(currentRevision(git), apiRev);
    } finally {
      close(git);
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }


  @Test
  public void testCommit() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);
      File changeFile = new File(checkoutDir, README_TXT);
      FileUtils.write(changeFile, generateContent());
      api.commit(checkoutDir, testName.getMethodName());
      String apiRev = api.getLocalRevision(checkoutDir);
      assertNotSame(initialRev, apiRev);
      assertEquals(currentRevision(git), apiRev);
    } finally {
      close(git);
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }

  @Test
  public void testRecursiveAdd_Commit() throws Exception {
    File baseGitRepo = createAndDeleteTempFile(null);
    File checkoutDir = createAndDeleteTempFile(null);
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);
      File newFile = createAndDeleteTempFile(checkoutDir);
      FileUtils.write(newFile, generateContent());

      api.recursiveAdd(checkoutDir);
      api.commit(checkoutDir, testName.getMethodName());
      String apiRev = api.getLocalRevision(checkoutDir);
      assertNotSame(initialRev, apiRev);
      assertEquals(currentRevision(git), apiRev);
    } finally {
      close(git);
      deleteQuietly(checkoutDir);
      deleteQuietly(baseGitRepo);
    }
  }


  private long count(Iterable objs) {
    long result = 0;
    for (Object o : objs) {
      result++;
    }
    return result;
  }

  private String initialiseRepo(File gitRepo) throws Exception {
    Git git = Git.init().setDirectory(gitRepo).call();
    String result = null;
    try {
      result = addFile(git, new File(gitRepo, README_TXT), "README.TXT", generateContent());
      result = addFile(git, "initialiseRepo", generateContent());
    } finally {
      close(git);
    }
    return result;
  }

  private String addFile(Git repo, String commitMsg, String contents) throws Exception {
    return addFile(repo, createAndDeleteTempFile(repo.getRepository().getWorkTree()), commitMsg, contents);
  }

  private String addFile(Git repo, File file, String commitMsg, String contents) throws Exception {
    FileUtils.write(file, contents);
    repo.add().addFilepattern(file.getName()).call();
    RevCommit commit = repo.commit().setAll(true).setMessage(commitMsg).call();
    return commit.getName();
  }

  private static void close(Git repo) {
    if (repo != null)
      repo.close();
  }

  private Git openRepo(File localRepoDir) throws Exception {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    Repository repository = builder.setWorkTree(localRepoDir).setup().build();
    return new Git(repository);
  }

  private static String generateContent() {
    return RandomStringUtils.randomAlphanumeric(ThreadLocalRandom.current().nextInt(1024));
  }

  private static void deleteQuietly(File f) throws IOException {
    FileUtils.deleteQuietly(f);
  }

  private String currentRevision(Git repository) throws GitAPIException, IOException {
    String fullBranch = repository.getRepository().getFullBranch();
    ObjectId resolvedRevision = repository.getRepository().resolve(fullBranch);
    return resolvedRevision.getName();
  }
}
