package com.adaptris.vcs.git.api;

import java.io.File;

import com.adaptris.core.management.vcs.VcsException;


public interface VersionControlSystemApi {

  /**
   * Will perform a fresh checkout from the remote repository url to the local working copy url.
   * @param remoteRepoUrl
   * @param workingCopyUrl
   * @return Revision number
   * @throws VcsException
   */
  public String checkout(String remoteRepoUrl, File workingCopyUrl) throws VcsException;
  
  /**
   * Will perform a fresh checkout from the remote repository url to the local working copy url, to the specified revision number.
   * @param remoteRepoUrl
   * @param workingCopyUrl
   * @param revision
   * @return Revision number
   * @throws VcsException
   */
  public String checkout(String remoteRepoUrl, File workingCopyUrl, String revision) throws VcsException;
  
  /**
   * Will fetch and update yuour local working copy to the specified revision.
   * @param workingCopyUrl
   * @param revision
   * @return Revision number
   * @throws VcsException
   */
  public String update(File workingCopyUrl, String revision) throws VcsException;
  
  /**
   * Will fetch and update your local working copy with the latest changes from the remote repository.
   * @param workingCopyUrl
   * @return Revision number
   * @throws VcsException
   */
  public String update(File workingCopyUrl) throws VcsException;
  
  /**
   * Will send your changes to the remote repository, with the supplied commit message.
   * @param workingCopyUrl
   * @param commitMessage
   * @throws VcsException
   */
  public void commit(File workingCopyUrl, String commitMessage) throws VcsException;
  
  /**
   * Will recursively check directories and sub directories adding all files for commit to the remote repository.
   * @param workingCopyUrl
   * @throws VcsException
   */
  public void recursiveAdd(File workingCopyUrl) throws VcsException;
  
}
