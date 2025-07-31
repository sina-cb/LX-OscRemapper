package magic.oscremapper.config;

import magic.oscremapper.LOG;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads and parses the OSC Remapper configuration from YAML file
 */
public class ConfigLoader {
  
  private static final String CONFIG_FILE_PATH = "resources/osc_remapper/remapper_config.yaml";
  
  /**
   * Load configuration from the standard location
   */
  public static RemapperConfig loadConfig() {
    return loadConfig(Path.of(CONFIG_FILE_PATH));
  }
  
  /**
   * Load configuration from specified path
   */
  public static RemapperConfig loadConfig(Path configPath) {
    try {
      LOG.log("Loading OSC Remapper config from: " + configPath.toAbsolutePath());
      
      if (!Files.exists(configPath)) {
        LOG.error("Config file not found: " + configPath.toAbsolutePath());
        return createDefaultConfig();
      }
      
      try (InputStream inputStream = new FileInputStream(configPath.toFile())) {
        RemapperConfig config = parseYamlConfig(inputStream);
        LOG.log("Successfully parsed YAML config with " + config.getRemotes().size() + " remotes");
        return config;
      }
      
    } catch (Exception e) {
      LOG.error(e, "Failed to load OSC Remapper config: " + e.getMessage());
      return createDefaultConfig();
    }
  }

  
  /**
   * Parse YAML configuration from input stream
   */
  private static RemapperConfig parseYamlConfig(InputStream inputStream) throws IOException {
    LOG.log("Starting YAML parsing...");
    Yaml yaml = new Yaml();
    
    // Parse the YAML as a generic object structure
    Object data = yaml.load(inputStream);
    LOG.log("YAML data type: " + (data != null ? data.getClass().getSimpleName() : "null"));
    
    if (data == null) {
      LOG.error("YAML data is null - file might be empty or invalid");
      return createDefaultConfig();
    }
    
    RemapperConfig config = new RemapperConfig();
    List<RemapperConfig.Remote> remotes = new ArrayList<>();
    
    if (data instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> rootMap = (Map<String, Object>) data;
      LOG.log("YAML root keys: " + rootMap.keySet());
      
      // Handle the remotes array format
      Object remotesData = rootMap.get("remotes");
      LOG.log("Remotes data type: " + (remotesData != null ? remotesData.getClass().getSimpleName() : "null"));
      
      if (remotesData instanceof List) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remotesList = (List<Map<String, Object>>) remotesData;
        LOG.log("Remotes list size: " + remotesList.size());
        
        for (int i = 0; i < remotesList.size(); i++) {
          Map<String, Object> remoteMap = remotesList.get(i);
          LOG.log("Processing remote " + i + ": " + remoteMap.keySet());
          RemapperConfig.Remote remote = parseRemote(remoteMap);
          if (remote != null) {
            remotes.add(remote);
            LOG.log("Successfully parsed remote: " + remote.getName());
          } else {
            LOG.error("Failed to parse remote " + i);
          }
        }
      } else {
        LOG.error("Config file must contain 'remotes:' array. Found: " + (remotesData != null ? remotesData.getClass().getSimpleName() : "null"));
        if (remotesData != null) {
          LOG.error("Remotes data content: " + remotesData.toString());
        }
      }
    } else {
      LOG.error("YAML root is not a Map. Found: " + data.getClass().getSimpleName());
    }
    
    config.setRemotes(remotes);
    LOG.log("Loaded " + remotes.size() + " remote configurations");
    
    for (RemapperConfig.Remote remote : remotes) {
      LOG.log("");
      LOG.log("remote: " + remote.getName());
      for (Map.Entry<String, List<String>> mapping : remote.getMappings().entrySet()) {
        String source = mapping.getKey();
        List<String> destinations = mapping.getValue();
        if (destinations.size() == 1) {
          LOG.log("  mappings -> " + source + " : " + destinations.get(0));
        } else {
          LOG.log("  mappings -> " + source + " : " + destinations);
        }
      }
      LOG.log("  longest_prefix_filter -> " + remote.calculateFilterPrefix());
      LOG.log("");
    }
    
    return config;
  }
  
  /**
   * Parse a single remote configuration from YAML map
   */
  private static RemapperConfig.Remote parseRemote(Map<String, Object> remoteMap) {
    try {
      LOG.log("Parsing remote: " + remoteMap.keySet());
      RemapperConfig.Remote remote = new RemapperConfig.Remote();
      
      // Set basic properties
      String name = getString(remoteMap, "name", "Unknown");
      String ip = getString(remoteMap, "ip", "127.0.0.1");
      int port = getInt(remoteMap, "port", 7000);
      
      remote.setName(name);
      remote.setIp(ip);
      remote.setPort(port);
      
      LOG.log("Remote basic properties: name=" + name + ", ip=" + ip + ", port=" + port);
      
      // Parse mappings - supports both single strings and arrays
      Object mappingsData = remoteMap.get("mappings");
      LOG.log("Mappings data type: " + (mappingsData != null ? mappingsData.getClass().getSimpleName() : "null"));
      
      if (mappingsData instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mappingsMap = (Map<String, Object>) mappingsData;
        LOG.log("Found " + mappingsMap.size() + " mapping entries");
        
        for (Map.Entry<String, Object> entry : mappingsMap.entrySet()) {
          String source = entry.getKey();
          Object targetData = entry.getValue();
          
          // All mappings must be arrays (even for single destinations)
          if (!(targetData instanceof List)) {
            LOG.error("Mapping for '" + source + "' must be an array. Found: " + targetData.getClass().getSimpleName());
            LOG.error("Use format: '" + source + ": [\"destination\"]' instead of '" + source + ": \"destination\"'");
            continue; // Skip this invalid mapping
          }
          
          @SuppressWarnings("unchecked")
          List<Object> targetList = (List<Object>) targetData;
          List<String> destinations = new ArrayList<>();
          
          LOG.log("Source " + source + " has " + targetList.size() + " destinations");
          
          for (Object target : targetList) {
            String targetStr = target.toString();
            destinations.add(targetStr);
            LOG.log("Mapping: " + source + " -> " + targetStr);
          }
          
          // Validate wildcard mappings
          if (!validateWildcardMapping(source, destinations)) {
            LOG.error("Invalid wildcard mapping for '" + source + "' - skipping");
            continue; // Skip this invalid mapping
          }
          
          remote.addMapping(source, destinations);
        }
        
      } else {
        LOG.log("No mappings found or mappings not a Map");
      }
      
      LOG.log("Successfully created remote with " + remote.getMappings().size() + " mappings");
      return remote;
      
    } catch (Exception e) {
      LOG.error(e, "Failed to parse remote configuration: " + e.getMessage());
      return null;
    }
  }
  
  /**
   * Get string value from map with default
   */
  private static String getString(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value != null ? value.toString() : defaultValue;
  }
  
  /**
   * Get integer value from map with default
   */
  private static int getInt(Map<String, Object> map, String key, int defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        LOG.error("Invalid integer value for " + key + ": " + value);
      }
    }
    return defaultValue;
  }
  
  /**
   * Create a default configuration when file loading fails
   */
  private static RemapperConfig createDefaultConfig() {
    LOG.log("Creating default OSC Remapper configuration");
    
    RemapperConfig config = new RemapperConfig();
    List<RemapperConfig.Remote> remotes = new ArrayList<>();
    
    // Create a default remote for testing
    RemapperConfig.Remote testRemote = new RemapperConfig.Remote();
    testRemote.setName("Failed to load config");
    testRemote.setIp("127.0.0.1");
    testRemote.setPort(7000);
    testRemote.getMappings().put("/", List.of("/failed/to/load/config"));
    
    remotes.add(testRemote);
    config.setRemotes(remotes);
    
    return config;
  }
  
  /**
   * Validate wildcard mappings according to plugin rules
   * @param source Source OSC pattern
   * @param destinations List of destination patterns
   * @return true if valid, false if invalid
   */
  private static boolean validateWildcardMapping(String source, List<String> destinations) {
    // Check if source is a wildcard pattern
    if (source.endsWith("/*")) {
      // Rule: Wildcard sources don't support multiple destinations
      if (destinations.size() > 1) {
        LOG.error("Wildcard source '" + source + "' cannot have multiple destinations (" + destinations.size() + " found)");
        LOG.error("Wildcard mappings must be 1:1. Found destinations: " + destinations);
        return false;
      }
      
      // Rule: If source is wildcard, destination must also be wildcard
      String destination = destinations.get(0);
      if (!destination.endsWith("/*")) {
        LOG.error("Wildcard source '" + source + "' must map to wildcard destination, found: '" + destination + "'");
        LOG.error("Use format: '/lx/tempo/*' -> ['/remote/tempo/*']");
        return false;
      }
    }
    
    return true; // Valid mapping
  }
}