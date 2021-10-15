package com.adaptris.vcs.git;

import static org.mockito.ArgumenMatchers.any;
import static org.mockito.ArgumenMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.Properties;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.management.vcs.VcsConstants;
import com.adaptris.core.management.vcs.VcsException;
import com.adaptris.core.stubs.JunitBootstrapProperties;
import com.adaptris.vcs.git.api.JGitApi;

import junit.framework.TestCase;

public class GitVCSTest extends TestCase {
  
  private static String TEMP_DIR_PROP = "java.io.tmpdir";

  private static final String EXPECTED_IMPL_NAME = "Git";
  
  private GitVCS vcs;
  
  private Properties bootstrapProperties;
  
  private File temporaryDir;
  
  @Mock
  private JGitApi mockApi;
  
  public void setUp() throws Exception {
    vcs = new GitVCS();
    bootstrapProperties = new Properties();
    
    String tempDir = System.getProperty(TEMP_DIR_PROP);
    temporaryDir = new File(tempDir);
    
    MockitoAnnotations.initMocks(this);
  }
  
  public void tearDown() throws Exception {
    
  }

  public void testImplName() throws Exception {
    assertEquals(EXPECTED_IMPL_NAME, vcs.getImplementationName());
  }
  
  public void testUpdateNoLocalDirSet() throws Exception {
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    
    // nothing should happen.
    verify(mockApi, never()).update(any(File.class));
    verify(mockApi, never()).update(any(File.class), any(String.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class), any(String.class));
  }
  
  
  //temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
  
  public void testUpdateNoRemoteRepo() throws Exception {
    File tempFile = new File(temporaryDir, "temp" + Long.toString(System.nanoTime()));
    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, tempFile.toURI().toURL().toString());
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    // nothing should happen.
    verify(mockApi, never()).update(any(File.class));
    verify(mockApi, never()).update(any(File.class), any(String.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class), any(String.class));
  }
  
  public void testLocalDirDoesntExistSoCheckout() throws Exception {
    File tempFile = new File(temporaryDir, "temp" + Long.toString(System.nanoTime()));
    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, tempFile.toURI().toURL().toString());
    bootstrapProperties.put(VcsConstants.VCS_REMOTE_REPO_URL_KEY, "svn://remoteHost/remoteDir");
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    // nothing should happen.
    verify(mockApi, times(1)).checkout(anyString(), any(File.class));
    verify(mockApi, times(0)).checkout(anyString(), any(File.class), any(String.class));
  }
  
  public void testLocalDirDoesntExistSoCheckoutToTag() throws Exception {
    File tempFile = new File(temporaryDir, "temp" + Long.toString(System.nanoTime()));
    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, tempFile.toURI().toURL().toString());
    bootstrapProperties.put(VcsConstants.VCS_REMOTE_REPO_URL_KEY, "svn://remoteHost/remoteDir");
    bootstrapProperties.put(VcsConstants.VCS_REVISION_KEY, "1");
    
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    // nothing should happen.
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, times(1)).checkout(anyString(), any(File.class), any(String.class));
  }
  
  public void testLocalDirDoesntExistRepoUrlMalformed() throws Exception {    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, "xxx:\\//:xxx");
    bootstrapProperties.put(VcsConstants.VCS_REMOTE_REPO_URL_KEY, "svn://remoteHost/remoteDir");
    
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    try {
      vcs.checkout();
      fail("Should fail with malformed URl");
    } catch (VcsException ex) {
      // expected.
    }
    
    // nothing should happen.
    verify(mockApi, never()).update(any(File.class));
    verify(mockApi, never()).update(any(File.class), any(String.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class), any(String.class));
  }
  
  public void testLocalDirExistsSoUpdate() throws Exception {
    File tempFile = new File(temporaryDir, "temp" + Long.toString(System.nanoTime()));
    tempFile.mkdir();
    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, tempFile.toURI().toURL().toString());
    bootstrapProperties.put(VcsConstants.VCS_REMOTE_REPO_URL_KEY, "svn://remoteHost/remoteDir");
    vcs.setBootstrapProperties(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    // nothing should happen.
    verify(mockApi, times(1)).update(any(File.class));
    verify(mockApi, never()).update(any(File.class), any(String.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class), any(String.class));
    
    tempFile.delete();
  }
  
  public void testLocalDirExistsSoUpdateWithRevision() throws Exception {
    File tempFile = new File(temporaryDir, "temp" + Long.toString(System.nanoTime()));
    tempFile.mkdir();
    
    bootstrapProperties.put(VcsConstants.VCS_LOCAL_URL_KEY, tempFile.toURI().toURL().toString());
    bootstrapProperties.put(VcsConstants.VCS_REMOTE_REPO_URL_KEY, "svn://remoteHost/remoteDir");
    bootstrapProperties.put(VcsConstants.VCS_REVISION_KEY, "1");
    vcs = new GitVCS(new JunitBootstrapProperties(bootstrapProperties));
    vcs.setApi(mockApi);
    
    vcs.update();
    // nothing should happen.
    verify(mockApi, never()).update(any(File.class));
    verify(mockApi, times(1)).update(any(File.class), any(String.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class));
    verify(mockApi, never()).checkout(anyString(), any(File.class), any(String.class));
    
    tempFile.delete();
  }
  
  public void testApiBuiltWithAuthentication() throws Exception {
    bootstrapProperties.put(VcsConstants.VCS_AUTHENTICATION_IMPL_KEY, "UsernamePassword");
    bootstrapProperties.put(VcsConstants.VCS_USERNAME_KEY, "myUsername");
    bootstrapProperties.put(VcsConstants.VCS_PASSWORD_KEY, "myPassword");
    
    vcs = new GitVCS(new JunitBootstrapProperties(bootstrapProperties));
    assertNotNull(vcs.getApi(bootstrapProperties));
  }
  
  public void testApiBuiltWithSSHAuthentication() throws Exception {
    bootstrapProperties.put(VcsConstants.VCS_AUTHENTICATION_IMPL_KEY, "SSH");
    bootstrapProperties.put(VcsConstants.VCS_SSH_KEYFILE_URL_KEY, "myKeyFileUrl");
    bootstrapProperties.put(VcsConstants.VCS_SSH_PASSPHRASE_KEY, "myPassphrase");
    
    vcs = new GitVCS(new JunitBootstrapProperties(bootstrapProperties));
    assertNotNull(vcs.getApi(bootstrapProperties));
  }

}
