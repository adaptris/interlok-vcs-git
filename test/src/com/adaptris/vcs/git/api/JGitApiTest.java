package com.adaptris.vcs.git.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class JGitApiTest {

  private File baseGitRepo = null;

  @Rule
  public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
    baseGitRepo = createAndDeleteTempFile(null);
  }

  @After
  public void tearDown() throws Exception {
    FileUtils.deleteQuietly(baseGitRepo);
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
  public void testCheckout() throws Exception {
    String initialRev = initialiseRepo(baseGitRepo);
    JGitApi api = new JGitApi();
    File checkoutDir = createAndDeleteTempFile(null);
    String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
    assertEquals(initialRev, checkoutRev);
  }

  @Test
  public void testCheckoutRevision() throws Exception {
    String initialRev = initialiseRepo(baseGitRepo);
    JGitApi api = new JGitApi();
    File checkoutDir = createAndDeleteTempFile(null);
    String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir, initialRev);
    assertEquals(initialRev, checkoutRev);
  }

  @Test
  public void testUpdate() throws Exception {
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {
      File checkoutDir = createAndDeleteTempFile(null);

      JGitApi api = new JGitApi();
      String checkoutRev = api.checkout(baseGitRepo.getAbsolutePath(), checkoutDir);
      assertEquals(initialRev, checkoutRev);

      String updateRev = addFile(git, testName.getMethodName(), generateContent());

      String apiRev = api.update(checkoutDir);
      assertEquals(updateRev, apiRev);
    } finally {
      close(git);
    }
  }

  @Test
  public void testUpdateToRevision() throws Exception {
    String initialRev = initialiseRepo(baseGitRepo);
    Git git = openRepo(baseGitRepo);
    try {
      File checkoutDir = createAndDeleteTempFile(null);

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
    Git git = Git.init().setDirectory(baseGitRepo).call();
    String result = null;
    try {
      result = addFile(git, "initialiseRepo", generateContent());
    } finally {
      close(git);
    }
    return result;
  }

  private String addFile(Git repo, String commitMsg, String contents) throws Exception {
    File file = createAndDeleteTempFile(repo.getRepository().getWorkTree());
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
}
