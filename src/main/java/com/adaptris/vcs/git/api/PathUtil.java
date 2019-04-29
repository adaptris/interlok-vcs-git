package com.adaptris.vcs.git.api;

public class PathUtil {

  private static final String FORWARD_SLASH = "/";
  private static final String BACK_SLASH = "\\";

  /**
   * Replace <b>\</b> by <b>/</b>
   *
   * @param str
   * @return a new string path with <b>\</b> replaced by <b>/</b>
   */
  public static String toUnixPath(String str) {
    String toReturn = null;
    if (str != null) {
      toReturn = str.replaceAll(BACK_SLASH + BACK_SLASH, FORWARD_SLASH);
    }
    return toReturn;
  }

}
