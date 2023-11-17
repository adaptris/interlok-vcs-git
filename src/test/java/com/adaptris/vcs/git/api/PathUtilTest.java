package com.adaptris.vcs.git.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class PathUtilTest {

  @Test
  public void testToUnixPath() {
    assertEquals("path/to/file", PathUtil.toUnixPath("path\\to\\file"));
  }

  @Test
  public void testToUnixPathAlreadUnix() {
    assertEquals("path/to/file", PathUtil.toUnixPath("path/to/file"));
  }

  @Test
  public void testToUnixPathEmptyString() {
    assertEquals("", PathUtil.toUnixPath(""));
  }

  @Test
  public void testToUnixPathNull() {
    assertNull(PathUtil.toUnixPath(null));
  }

}
