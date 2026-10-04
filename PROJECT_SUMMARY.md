# Smart Ambulance Priority Traffic Control System (SAPTCS)

## Project Overview

The Smart Ambulance Priority Traffic Control System is an emergency-response platform that coordinates ambulances and traffic junctions using a hybrid local/cloud architecture. The system ensures ambulances can navigate through traffic efficiently while maintaining local operation capabilities even without internet connectivity.

## Core Purpose

- **Emergency Response Priority**: Automatically preempt traffic signals for ambulances during emergencies
- **Local Independence**: Use LoRa and RFID for local traffic control without internet dependency
- **Cloud Visibility**: Track ambulances, manage data, and provide operational dashboards via Firebase and MQTT
- **Hospital Integration**: Discover nearby hospitals, provide navigation, and alert hospital staff
- **Multi-Role Access**: Support drivers, traffic police, hospital staff, and administrators

## Architecture

### Hybrid Local-Cloud Design

**Local Layer (No Internet Required)**:
- LoRa communication between ambulances and traffic junctions
- GPS-based distance and bearing calculations
- RFID confirmation at stop lines
- Local traffic signal preemption logic

**Cloud Layer (Internet Available)**:
- Firebase Realtime Database for state management
- Firebase Authentication for user management
- MQTT for real-time telemetry
- Android app for driver operations
- Web dashboard for operational visibility

## Technologies Used

### Firmware (ESP32 Arduino C++)
- **Microcontroller**: ESP32 (dual-core, WiFi + Bluetooth)
- **LoRa Communication**: Sandeep Mistry Arduino LoRa library
- **GPS Parsing**: TinyGPSPlus library
- **RFID Reading**: MFRC522/RC522 library
- **JSON Processing**: ArduinoJson library
- **MQTT Publishing**: PubSubClient library
- **Configuration**: Runtime-configurable junctions and ambulances

### Mobile App (Android Kotlin + Jetpack Compose)
- **UI Framework**: Jetpack Compose (Material 3)
- **Location Services**: Google Play Services Fused Location Provider
- **Maps/Navigation**: Google Maps SDK (for LatLng), OpenStreetMap Overpass API, OSRM routing
- **Firebase**: Firebase Realtime Database, Firebase Authentication
- **MQTT**: Eclipse Paho MQTT client
- **Networking**: OkHttp for HTTP requests
- **Hospital Discovery**: OpenStreetMap Overpass API
- **Navigation Routing**: OSRM (Open Source Routing Machine)

### Dashboard (React + Vite)
- **UI Framework**: React with Vite
- **Map Visualization**: Leaflet + React Leaflet
- **MQTT Integration**: MQTT.js over WebSockets
- **Firebase**: Firebase JavaScript SDK
- **Icons**: Lucide React
- **Styling**: CSS

### Cloud Services
- **Firebase Realtime Database**: State management, user data, trips, alerts
- **Firebase Authentication**: User identity management
- **MQTT Broker**: Real-time telemetry (e.g., HiveMQ, EMQX)
- **OpenStreetMap**: Free map data and geocoding
- **OSRM**: Free routing service

## System Components

### 1. Ambulance Unit (ESP32 Firmware)
**File**: `firmware/ambulance_unit/ambulance_unit.ino`

**Features**:
- Broadcast GPS coordinates via LoRa
- Configurable ambulance ID and trip ID
- Real-time telemetry publishing
- RSSI fallback when GPS unavailable
- Integration with Firebase and MQTT

**Key Functions**:
- GPS parsing and coordinate transmission
- LoRa packet generation with ambulance ID, GPS data, trip info
- Local operation without internet
- Cloud synchronization when available

### 2. Traffic Signal Unit (ESP32 Firmware)
**File**: `firmware/traffic_signal_unit/traffic_signal_unit.ino`

**Features**:
- Receive ambulance LoRa packets
- Calculate Haversine distance and bearing to ambulance
- Determine if ambulance is approaching and eligible for preemption
- RFID confirmation at stop line
- Configurable junction parameters
- Support for multiple authorized ambulances

**Key Functions**:
- LoRa packet reception and parsing
- GPS-based distance calculation (Haversine formula)
- Bearing calculation for approach detection
- RFID tag validation for stop-line confirmation
- Traffic signal state management (normal, preemption, clearance)
- RSSI fallback for proximity detection
- Configurable thresholds (distance, bearing, RSSI, timeouts)

**Runtime Configuration**:
- Junction ID, name, coordinates
- Active lane configuration
- Approach threshold (distance)
- Bearing tolerance
- RSSI fallback threshold
- GPS packet timeout
- Clearance timeout
- Multiple authorized ambulance IDs

### 3. Android Mobile App
**Package**: `com.smartambulance.driver`

**Screens**:
- **Login Screen**: User authentication with PIN-based login
- **Driver Screen**: Emergency activation, hospital selection, GPS tracking, MQTT status
- **Police Screen**: Junction alerts, ambulance preemption status, telemetry
- **Hospital Screen**: Incoming ambulance alerts, readiness status updates
- **Admin Screen**: User registration, data seeding, record management
- **Hospital Search Screen**: Nearby hospital discovery, search, navigation

**Key Features**:
- Role-based access (driver, police, hospital, admin)
- Emergency trip activation and completion
- Real-time GPS location publishing
- MQTT integration for live telemetry
- Hospital discovery via OpenStreetMap
- Navigation via OSRM
- Firebase Realtime Database synchronization
- Firebase Authentication (with fallback)
- Location permission handling

**Services**:
- `MqttManager`: MQTT connection, subscription, publishing
- `HospitalDiscoveryService`: OpenStreetMap Overpass API integration
- `NavigationService`: OSRM routing and navigation

**Data Models**:
- `AppUser`: User profile with role and assignments
- `HospitalOption`: Hospital details with distance and ETA
- `Hospital`: OpenStreetMap hospital data

### 4. Web Dashboard (Operations Console)
**Framework**: React + Vite

**Features**:
- Six-view operations console: Live ops, Junctions, Ambulances, Hospitals, Alerts, System
- Live ambulance map with Leaflet (key-free OSM tiles, corridor route, follow mode)
- Firebase Realtime Database integration and MQTT over WebSockets, merged per-key by recency
- Active-response corridor card with derived ETA / distance / speed and junction preemption state
- Junction control-room override plus a tabletop hardware simulator (GPS/RSSI/RFID/timeout)
- Hospital bed availability, inbound alerts and trauma-bay readiness
- Unified junction + hospital alert stream
- Service health, data freshness and explicit seeding
- Fully responsive: desktop sidebar, mobile bottom navigation
- `?demo=1` simulated corridor for hardware-free demos

**Modules**:
- `views/*`: one module per console view
- `hooks/useOperationsData.js`: merges Firebase + MQTT + demo into a single model
- `components/MapPanel.jsx`, `components/EventFeed.jsx`, `components/ui.jsx`
- `integrations/firebaseClient.js`, `integrations/mqttClient.js`, `integrations/mqttTopics.js`
- `lib/geo.js` (Haversine/bearing/ETA), `lib/format.js`, `lib/model.js`, `lib/demoScenario.js`, `lib/seed.js`

## Data Flow

### Emergency Scenario Flow

1. **Driver activates emergency** in Android app
2. **App updates Firebase** with emergency status, destination hospital, severity
3. **App publishes MQTT** message with ambulance status
4. **Ambulance ESP32 broadcasts** GPS coordinates via LoRa
5. **Traffic junction ESP32 receives** LoRa packet
6. **Junction calculates** distance and bearing to ambulance
7. **If approaching and eligible**, junction preempts traffic signal
8. **RFID confirmation** at stop line validates ambulance passage
9. **Junction alerts** police via Firebase/MQTT
10. **Hospital receives** alert via Firebase with ETA
11. **Hospital staff updates** readiness status
12. **App publishes** real-time GPS location
13. **Dashboard displays** live ambulance position and status
14. **Driver completes** emergency trip, system resets

### Hospital Discovery Flow

1. **Driver requests** hospital search
2. **App requests** location permission
3. **App obtains** current GPS coordinates
4. **App queries** OpenStreetMap Overpass API for nearby hospitals
5. **App displays** hospitals sorted by distance
6. **Driver searches** or filters hospitals
7. **Driver selects** destination hospital
8. **App calculates** route via OSRM
9. **App launches** navigation

## Firebase Database Schema

```
users/
  {userId}/
    userId
    name
    pin
    role (ambulance_driver, police, hospital, admin)
    ambulanceId (for drivers)
    assignedJunctionId (for police)
    hospitalId (for hospital staff)
    active

ambulances/
  {ambulanceId}/
    ambulanceId
    driverId
    rfidTagId
    loraNodeId
    status (available, emergency_active)
    emergencyActive
    destinationHospitalId
    severity
    lastLoRaTelemetry/
      junctionId
      gpsFix
      rssi
      distanceMeters
      approaching
      preemptionEligible
    lastLocation/
      lat
      lng
      source
    updatedAt

emergency_trips/
  {tripId}/
    tripId
    ambulanceId
    driverId
    destinationHospitalId
    destinationHospitalName
    severity
    status (active, completed)
    startedAt
    endedAt

hospital_alerts/
  {hospitalId}/
    {tripId}/
      tripId
      ambulanceId
      severity
      status (incoming, team_alerted, bed_ready, doctor_ready, completed)
      eta
      message
      updatedAt

police_alerts/
  {junctionId}/
    {tripId}/
      tripId
      ambulanceId
      severity
      destinationHospitalId
      status (ambulance_approaching, preemption_active, clearance, completed)
      message
      preemptionMode (gps_lora, rssi_fallback)
      distanceMeters
      updatedAt

hospitals/
  {hospitalId}/
    hospitalId
    name
    distance
    eta
    bedsAvailable
    emergencyAvailable

junctions/
  {junctionId}/
    junctionId
    name
    activeLane
    signalState (normal, preemption, clearance)
    preemptionMode (none, gps_lora, rssi_fallback)
    approachThresholdMeters
    bearingToleranceDeg
    rssiFallbackThresholdDbm
    rssiConsecutivePacketCount
    gpsPacketTimeoutMs
    clearanceTimeoutMs
    updatedAt

rfid_tags/
  {rfidTagId}/
    rfidTagId
    ambulanceId
    authorized
    active
    updatedAt

lora_telemetry/
  {junctionId}/
    {ambulanceId}/
      ambulanceId
      tripId
      lat
      lng
      speedKmph
      headingDeg
      gpsFix
      rssi
      distanceMeters
      bearingToJunctionDeg
      approaching
      preemptionEligible
      source
      updatedAt
```

## MQTT Topics

```
ambulance/{ambulanceId}/status
ambulance/{ambulanceId}/location
ambulance/{ambulanceId}/emergency

junction/{junctionId}/alert
junction/{junctionId}/telemetry

hospital/{hospitalId}/alert
hospital/{hospitalId}/status

system/status
```

## Security Considerations

### Current Implementation
- Firebase Realtime Database rules configured (currently using unauthenticated rules for testing)
- Firebase Authentication available (with anonymous sign-in fallback)
- MQTT authentication recommended for production
- API keys managed via google-services.json

### Production Recommendations
- Enable Firebase Authentication with email/password or phone auth
- Implement role-based Firebase Realtime Database rules
- Use authenticated MQTT broker
- Add SSL/TLS for all communications
- Implement rate limiting
- Add audit logging
- Regular security audits

## Demo Accounts

- **Driver**: `driver_001` / `1111`
- **Police**: `police_001` / `2222`
- **Hospital**: `hospital_001` / `3333`
- **Admin**: `admin_001` / `0000`

## Current Status

✅ **Completed**:
- Firmware with runtime configuration
- Mobile app with all role screens
- Hospital discovery via OpenStreetMap
- Navigation via OSRM
- Firebase integration
- MQTT integration
- Web dashboard with map
- User registration system
- Emergency trip management
- Login system with demo accounts

🔄 **In Progress**:
- Testing all features end-to-end
- Transitioning to production Firebase rules
- Security hardening

📋 **Planned**:
- Multi-city architecture
- Analytics and reporting
- Push notifications
- Offline data sync
- Enhanced security
- Performance optimization

## Documentation

- `docs/complete-system-guide.md` - Complete system documentation
- `docs/architecture.md` - System architecture details
- `docs/hybrid-preemption.md` - Hybrid preemption logic
- `docs/mqtt-topics.md` - MQTT topic structure
- `docs/database-schema.md` - Database schema documentation
- `docs/technical-decisions.md` - Technical decision rationale
- `docs/roadmap.md` - Project roadmap
- `docs/FIREBASE_PERMISSION_DENIED_FIX.md` - Firebase troubleshooting
- `docs/ADDING_REAL_USERS_GUIDE.md` - User registration guide
- `docs/FIREBASE_SECURITY_RULES.md` - Production security rules
- `docs/FIREBASE_SETUP_GUIDE.md` - Firebase setup instructions
- `docs/HOSPITAL_DISCOVERY_IMPLEMENTATION.md` - Hospital feature docs
- `docs/PRODUCTION_ENHANCEMENT_PLAN.md` - Production roadmap

## Repository

**GitHub**: https://github.com/likhithmdev/Major-project

**Project Structure**:
```
smart_ambulance/
├── firmware/
│   ├── ambulance_unit/
│   │   └── ambulance_unit.ino
│   └── traffic_signal_unit/
│       └── traffic_signal_unit.ino
├── mobile_app/
│   ├── app/
│   │   ├── src/main/java/com/smartambulance/driver/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   ├── mqtt/
│   │   │   ├── services/
│   │   │   └── ui/screens/
│   │   ├── build.gradle.kts
│   │   └── google-services.json
│   └── build.gradle.kts
├── dashboard/
│   ├── src/
│   │   ├── views/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── integrations/
│   │   ├── lib/
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── styles.css
│   ├── package.json
│   └── vite.config.js
├── docs/
│   └── (various documentation files)
└── README.md
```

## License

This is a student project for academic purposes.

## Contact

For questions or contributions, please refer to the GitHub repository.