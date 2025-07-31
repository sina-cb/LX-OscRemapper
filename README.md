# OscRemapper Plugin

A plugin for LX Studio/Chromatik that provides OSC remapping and forwarding capabilities.

## Features

- Global brightness and tempo modulators that send OSC messages
- Configurable OSC message mappings
- Real-time parameter forwarding to remote applications
- Based on the Beyond plugin architecture

## Configuration

The plugin uses `sync-config.yaml` for OSC mappings and network configuration.

## Usage

1. Click "Set Up Now" to create OSC output and add global modulators
2. Configure target IP and port for your remote application
3. Use the brightness and tempo modulators to control remote parameters

## Build & Install

Assuming the `LX-OscRemapper` and `LXStudio-TE` are cloned in `~/workspace/{LX-OscRemapper | LXStudio-TE}`
```bash
cd ~/workspace/LX-OscRemapper && mvn compile && mvn install -DskipTests
cd ~/workspace/LXStudio-TE/te-app && mvn package -DskipTests

cd ~/workspace/LXStudio-TE/te-app && LOG_FILE="../.agent_logs/te_app_logs_$(date +%Y%m%d_%H%M%S).log" && echo "🎯 Testing Fix - Logs: $LOG_FILE" && java -ea -XstartOnFirstThread -Djava.awt.headless=true -Dgpu -jar target/te-app-0.3.0-SNAPSHOT-jar-with-dependencies.jar --resolution 1920x1200 &> "$LOG_FILE"
```