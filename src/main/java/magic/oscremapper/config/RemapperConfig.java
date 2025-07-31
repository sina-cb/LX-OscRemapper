package magic.oscremapper.config;

import magic.oscremapper.LOG;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration data structures for OSC Remapper plugin
 * Parses remapper_config.yaml from te-app resources
 */
public class RemapperConfig {
  private List<Remote> remotes = new ArrayList<>();

  public List<Remote> getRemotes() {
    return remotes;
  }

  public void setRemotes(List<Remote> remotes) {
    this.remotes = remotes;
  }

  /**
   * Remote configuration for OSC output
   */
  public static class Remote {
    private String name;
    private String ip;
    private int port;
    private Map<String, List<String>> mappings = new HashMap<>();
    private boolean isPassthrough = false; // True if this remote has identity mappings

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public Map<String, List<String>> getMappings() {
      return mappings;
    }

    public void setMappings(Map<String, List<String>> mappings) {
      this.mappings = mappings;
      // Auto-detect if this remote is passthrough (identity mappings)
      detectPassthrough();
    }

    public boolean isPassthrough() {
      return isPassthrough;
    }

    public void setPassthrough(boolean passthrough) {
      this.isPassthrough = passthrough;
    }

    /**
     * Add a mapping and update passthrough detection
     */
    public void addMapping(String source, List<String> destinations) {
      mappings.put(source, destinations);
      // Re-evaluate passthrough status after each mapping is added
      detectPassthrough();
    }

    /**
     * Detect if this remote is passthrough (identity mappings)
     * A remote is passthrough if all mappings map input to the same output
     */
    private void detectPassthrough() {
      LOG.log("🔍 Checking passthrough for remote: " + name);
      isPassthrough = true;
      
      if (mappings.isEmpty()) {
        LOG.log("❌ Remote " + name + " has no mappings - not passthrough");
        isPassthrough = false;
        return;
      }
      
      for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
        String sourcePattern = entry.getKey();
        List<String> destinations = entry.getValue();
        
        LOG.log("   Checking: " + sourcePattern + " → " + destinations);
        
        // For passthrough, we expect exactly one destination that matches the source
        if (destinations.size() != 1) {
          LOG.log("❌ Multiple destinations (" + destinations.size() + ") - not passthrough");
          isPassthrough = false;
          break;
        }
        
        String destination = destinations.get(0);
        if (!destination.equals(sourcePattern)) {
          LOG.log("❌ " + sourcePattern + " ≠ " + destination + " - not passthrough");
          isPassthrough = false;
          break;
        }
        
        LOG.log("✅ " + sourcePattern + " == " + destination + " - identity mapping");
      }
      
      if (isPassthrough) {
        LOG.log("🎯 Remote '" + name + "' detected as PASSTHROUGH - will skip OSC processing");
      } else {
        LOG.log("📡 Remote '" + name + "' is NOT passthrough - will process OSC messages");
      }
    }

    /**
     * Calculate the longest matching prefix from all destination mappings
     * This will be used as the filter for the OSC output
     */
    public String calculateFilterPrefix() {
      if (mappings.isEmpty()) {
        return "/lx";  // Default fallback
      }

      // Use destination patterns (values) instead of source patterns (keys)
      Set<String> destinationPatterns = new HashSet<>();
      for (List<String> destinations : mappings.values()) {
        destinationPatterns.addAll(destinations);
      }
      
      // Find the longest common prefix among all destination patterns
      String longestPrefix = findLongestCommonPrefix(destinationPatterns);
      
      // If no common prefix found, use the shortest pattern as base
      if (longestPrefix.isEmpty() || longestPrefix.equals("/")) {
        return destinationPatterns.stream()
          .min((a, b) -> Integer.compare(a.length(), b.length()))
          .orElse("/lx");
      }
      
      return longestPrefix;
    }

    /**
     * Find the longest common prefix among OSC address patterns
     */
    private String findLongestCommonPrefix(Set<String> patterns) {
      if (patterns.isEmpty()) {
        return "";
      }

      String first = patterns.iterator().next();
      String commonPrefix = first;

      for (String pattern : patterns) {
        commonPrefix = getCommonPrefix(commonPrefix, pattern);
        if (commonPrefix.isEmpty()) {
          break;
        }
      }

      // Ensure prefix ends at a path boundary
      if (!commonPrefix.isEmpty() && !commonPrefix.endsWith("/")) {
        int lastSlash = commonPrefix.lastIndexOf('/');
        if (lastSlash > 0) {
          commonPrefix = commonPrefix.substring(0, lastSlash + 1);
        }
      }

      return commonPrefix;
    }

    /**
     * Get common prefix between two OSC address patterns
     */
    private String getCommonPrefix(String a, String b) {
      int minLength = Math.min(a.length(), b.length());
      int i = 0;
      
      while (i < minLength && a.charAt(i) == b.charAt(i)) {
        i++;
      }
      
      return a.substring(0, i);
    }

    /**
     * Check if this remote should handle the given OSC address
     */
    public boolean shouldHandleAddress(String oscAddress) {
      for (String sourcePattern : mappings.keySet()) {
        if (matchesPattern(oscAddress, sourcePattern)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Remap an OSC address according to this remote's mapping rules
     * Returns a list of destination addresses (supports multiple destinations per source)
     */
    public List<String> remapAddress(String oscAddress) {
      List<String> results = new ArrayList<>();
      
      // Try exact match first
      List<String> exactMatches = mappings.get(oscAddress);
      if (exactMatches != null) {
        results.addAll(exactMatches);
        return results;
      }

      // Try prefix matching
      for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
        String sourcePattern = entry.getKey();
        List<String> targetPatterns = entry.getValue();
        
        if (sourcePattern.endsWith("/*")) {
          String sourcePrefix = sourcePattern.substring(0, sourcePattern.length() - 2);
          
          if (oscAddress.startsWith(sourcePrefix + "/")) {
            for (String targetPattern : targetPatterns) {
              if (targetPattern.endsWith("/*")) {
                String targetPrefix = targetPattern.substring(0, targetPattern.length() - 2);
                results.add(targetPrefix + oscAddress.substring(sourcePrefix.length()));
              } else {
                results.add(targetPattern);
              }
            }
          }
        }
      }

      // If no mapping found, return original address as single item
      if (results.isEmpty()) {
        results.add(oscAddress);
      }
      
      return results;
    }

    /**
     * Check if an OSC address matches a pattern (supporting /* wildcards)
     */
    private boolean matchesPattern(String address, String pattern) {
      if (pattern.equals(address)) {
        return true;  // Exact match
      }
      
      if (pattern.endsWith("/*")) {
        String prefix = pattern.substring(0, pattern.length() - 2);
        return address.startsWith(prefix + "/");
      }
      
      return false;
    }

    @Override
    public String toString() {
      return String.format("Remote{name='%s', ip='%s', port=%d, mappings=%d}", 
        name, ip, port, mappings.size());
    }
  }
}