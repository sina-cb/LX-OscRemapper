package magic.oscremapper;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.osc.LXOscConnection;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.studio.LXStudio;
import heronarts.lx.utils.LXUtils;
import magic.oscremapper.ui.UIOscRemapperPlugin;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.osc.OscPacket;
import java.io.IOException;
import java.io.InputStream;
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
  private LXOscConnection.Output remapOutput;
  
  // OSC Capture parameters
  public final BooleanParameter oscCaptureEnabled = 
    new BooleanParameter("OSC Remap", false)
      .setDescription("Enable remapping /lx OSC messages to /test");

  public OscRemapperPlugin(LX lx) {
    this.lx = lx;
    LOG.log("OscRemapperPlugin(LX) constructor called - version: " + loadVersion());
    
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
            
            // Only remap /lx messages (original engine messages) - use proper OSC prefix matching
            if (message.hasPrefix("/lx/tempo/beat")) {
              // Create new address with /test prefix
              String newAddress = originalAddress.replaceFirst("^/lx", "/test");
              LOG.log(newAddress + " -> " + message.getFloat(0));
              lx.engine.osc.sendMessage(newAddress, message.getFloat(0));
              LOG.log("Sent message to " + newAddress);
            }
          }
        } catch (Exception e) {
          LOG.error(e, "Error processing transmitted OSC message");
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
   * Add the basic common items: OSC output, global brightness modulator, global tempo modulator.
   */
  private void runSetup() {
    this.remapOutput = confirmOscOutput(this.lx, DEFAULT_OSC_HOST, DEFAULT_OSC_PORT);
    if (this.remapOutput != null) {
      LOG.log("[OscRemapper] Main OSC output configured at " + this.remapOutput.host.getString() + ":" + this.remapOutput.port.getValuei());
    }
  }
  
  /**
   * Create or find a dedicated OSC output for remapped messages (following Beyond plugin pattern)
   */
  public static LXOscConnection.Output confirmOscOutput(LX lx, String host, int port) {
    for (LXOscConnection.Output output : lx.engine.osc.outputs) {
      if (output.hasFilter.isOn() && DEFAULT_OSC_FILTER.equals(output.filter.getString())) {
        return output;
      }
    }

    LXOscConnection.Output oscOutput = lx.engine.osc.addOutput();
    if (!LXUtils.isEmpty(host)) {
      oscOutput.host.setValue(host);
    }
    oscOutput.port.setValue(port);
    oscOutput.filter.setValue(DEFAULT_OSC_FILTER);
    oscOutput.hasFilter.setValue(true);
    try {
      oscOutput.active.setValue(true);
    } catch (Exception e) {
      LOG.error(e, "Failed to activate OSC output. Set the correct IP and port.");
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