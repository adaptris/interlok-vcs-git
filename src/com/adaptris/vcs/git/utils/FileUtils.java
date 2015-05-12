package com.adaptris.vcs.git.utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.adaptris.util.URLString;

public class FileUtils {

  public static File toFile(String url) throws MalformedURLException {
    File result = null;
    
    URLString urlString = new URLString(url);
    if (urlString.getProtocol() == null || "file".equals(urlString.getProtocol())) {
      result = new File(urlString.getFile());
    } else {
      result = org.apache.commons.io.FileUtils.toFile(new URL(url));
    }
    
    return result;
  }
}
