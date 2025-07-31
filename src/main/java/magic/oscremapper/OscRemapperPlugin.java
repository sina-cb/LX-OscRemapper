package magic.oscremapper;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.osc.LXOscConnection;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio;
import heronarts.lx.utils.LXUtils;
import magic.oscremapper.ui.UIOscRemapperPlugin;
import magic.oscremapper.config.ConfigLoader;
import magic.oscremapper.config.RemapperConfig;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.osc.OscPacket;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Plugin for Chromatik that provides OSC remapping and forwarding capabilities
 * Based on the Beyond plugin architecture
 */
@LXPlugin.Name("OscRemapper")
public class OscRemapperPlugin implements LXStudio.Plugin {

  private static final String DEFAULT_OSC_HOST = "127.0.0.1";
  private static final int DEFAULT_OSC_PORT = 7000;
  private static final String DEFAULT_OSC_FILTER = "/test";

  public final TriggerParameter setUpNow =
    new TriggerParameter("Set Up Now", this::runSetup)
      .setDescription("Add an OSC output and add global modulators for brightness and tempo sync");

  private final LX lx;
  private OscRemapperTransmissionListener transmissionListener;
  private RemapperConfig config;
  
  // Track OSC outputs by remote name
  private final Map<String, LXOscConnection.Output> remoteOutputs = new HashMap<>();
  
  // OSC Capture parameters
  public final BooleanParameter oscCaptureEnabled = 
    new BooleanParameter("OSC Remap", false)
      .setDescription("Enable remapping /lx OSC messages to /test");

  public OscRemapperPlugin(LX lx, Path configPath) {
    this.lx = lx;
    LOG.log("OscRemapperPlugin(LX) constructor called - version: " + loadVersion());
    LOG.log("Using config path: " + configPath);
    
    // Load configuration from YAML file
    this.config = ConfigLoader.loadConfig(configPath);
    LOG.log("Loaded configuration with " + this.config.getRemotes().size() + " remotes");
    
    // Set up transmission listener for OSC remapping
    this.transmissionListener = new OscRemapperTransmissionListener();
    
    // Listen for parameter changes
    this.oscCaptureEnabled.addListener(p -> {
      if (this.oscCaptureEnabled.isOn()) {
        startOscCapture();
      } else {
        stopOscCapture();
      }
    });
  }

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    new UIOscRemapperPlugin(ui, this, ui.leftPane.content.getContentWidth())
      .addToContainer(ui.leftPane.content, 2);
  }

  /**
   * Start OSC remapping by listening to transmission events
   */
  private void startOscCapture() {
    try {
      // Add transmission listener to capture ALL outgoing OSC messages
      lx.engine.osc.addTransmissionListener(this.transmissionListener);
      LOG.log("OSC remapping started - listening to all transmitted OSC messages");
    } catch (Exception e) {
      LOG.error(e, "Failed to start OSC remapping");
    }
  }
  
  /**
   * Stop OSC remapping
   */
  private void stopOscCapture() {
    try {
      // Remove transmission listener
      lx.engine.osc.removeTransmissionListener(this.transmissionListener);
      LOG.log("OSC remapping stopped");
    } catch (Exception e) {
      LOG.error(e, "Failed to stop OSC remapping");
    }
  }
  
  /**
   * TransmissionListener for capturing and remapping outgoing OSC messages
   */
  private class OscRemapperTransmissionListener implements LXOscEngine.TransmissionListener {
    @Override
    public void oscMessageTransmitted(OscPacket packet) {
      try {
        // Check if this is an OscMessage that we should remap
        if (packet instanceof OscMessage) {
          OscMessage message = (OscMessage) packet;
          String originalAddress = message.getAddressPattern().getValue();
          
          // Process each configured remote
          for (RemapperConfig.Remote remote : config.getRemotes()) {
            // Check if this remote should handle this address
            if (remote.shouldHandleAddress(originalAddress)) {
              // Get the remapped addresses (can be multiple destinations)
              List<String> remappedAddresses = remote.remapAddress(originalAddress);
              
              // Send the remapped message to each destination
              for (String remappedAddress : remappedAddresses) {
                try {
                  sendRemappedMessage(message, originalAddress, remappedAddress, remote.getName());
                } catch (Exception e) {
                  LOG.error(e, "Failed to send remapped message to " + remappedAddress + " for " + remote.getName());
                }
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error(e, "Error processing transmitted OSC message");
      }
    }
    
    /**
     * Send a remapped OSC message through the LX engine (assuming all values are floats)
     */
    private void sendRemappedMessage(OscMessage originalMessage, String originalAddress, 
                                   String remappedAddress, String remoteName) {
      try {
        // Send the remapped message - LX engine will route it to appropriate outputs based on filters
        float value = (originalMessage.size() > 0) ? originalMessage.getFloat(0) : 0.0f;
        lx.engine.osc.sendMessage(remappedAddress, value);
        LOG.log("[" + remoteName + "] " + originalAddress + " → " + remappedAddress + " (" + value + ")");
      } catch (Exception e) {
        LOG.error(e, "Failed to send remapped message to " + remoteName + ": " + e.getMessage());
      }
    }
  }

  /**
   * Cleanup resources when the plugin is disposed
   */
  public void dispose() {
    stopOscCapture();
  }

  /**
   * Set up OSC outputs for all configured remotes
   */
  private void runSetup() {
    LOG.log("[OscRemapper] Setting up OSC outputs for " + config.getRemotes().size() + " remotes");
    
    // Clear existing outputs
    remoteOutputs.clear();
    
    // Create or find OSC output for each configured remote
    for (RemapperConfig.Remote remote : config.getRemotes()) {
      try {
        LXOscConnection.Output output = confirmOscOutput(this.lx, remote.getName(), 
          remote.getIp(), remote.getPort(), remote.calculateFilterPrefix());
        
        if (output != null) {
          remoteOutputs.put(remote.getName(), output);
          LOG.log("[OscRemapper] ✅ " + remote.getName() + " → " + remote.getIp() + ":" + 
            remote.getPort() + " (filter: " + remote.calculateFilterPrefix() + ")");
        } else {
          LOG.error("Failed to create OSC output for remote: " + remote.getName());
        }
      } catch (Exception e) {
        LOG.error(e, "Error setting up remote: " + remote.getName());
      }
    }
    
    LOG.log("[OscRemapper] Setup complete - " + remoteOutputs.size() + " outputs active");
  }
  
  /**
   * Create or find a dedicated OSC output for remapped messages (following Beyond plugin pattern)
   */
  public static LXOscConnection.Output confirmOscOutput(LX lx, String remoteName, String host, int port, String filter) {
    // Check if we already have an output with this exact configuration
    for (LXOscConnection.Output output : lx.engine.osc.outputs) {
      if (output.hasFilter.isOn() && 
          filter.equals(output.filter.getString()) &&
          host.equals(output.host.getString()) &&
          port == output.port.getValuei()) {
        LOG.log("Found existing OSC output for " + remoteName + ": " + host + ":" + port);
        return output;
      }
    }

    // Create new output
    LXOscConnection.Output oscOutput = lx.engine.osc.addOutput();
    if (!LXUtils.isEmpty(host)) {
      oscOutput.host.setValue(host);
    }
    oscOutput.port.setValue(port);
    oscOutput.filter.setValue(filter);
    oscOutput.hasFilter.setValue(true);
    
    try {
      oscOutput.active.setValue(true);
      LOG.log("Created new OSC output for " + remoteName + ": " + host + ":" + port + " (filter: " + filter + ")");
    } catch (Exception e) {
      LOG.error(e, "Failed to activate OSC output for " + remoteName + ". Check IP and port.");
      return null;
    }
    
    return oscOutput;
  }

  /**
   * Loads 'oscremapper.properties', after maven resource filtering has been applied.
   */
  private String loadVersion() {
    String version = "1.0.0";
    Properties properties = new Properties();
    try (InputStream inputStream =
           getClass().getClassLoader().getResourceAsStream("oscremapper.properties")) {
      if (inputStream != null) {
        properties.load(inputStream);
        version = properties.getProperty("oscremapper.version", version);
      }
    } catch (IOException e) {
      LOG.error(e, "Failed to load version information");
    }
    return version;
  }
}