package magic.oscremapper;

import heronarts.lx.LX;

/**
 * Logging utility for the OscRemapper plugin
 */
public class LOG {

  private static final String PREFIX = "[OscRemapper] ";

  public static void log(String message) {
    LX.log(PREFIX + message);
  }

  public static void error(String message) {
    LX.error(PREFIX + message);
  }

  public static void error(Exception e, String message) {
    LX.error(e, PREFIX + message);
  }

  public static void warning(String message) {
    LX.warning(PREFIX + message);
  }
}