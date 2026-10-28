# Tracking Engine

## Overview

For authentication configuration, forced re-login during migration, denied
callbacks and deployment limitations, see [SECURITY.md](SECURITY.md).

A Java 21 / Spring Boot vehicle-tracking backend with fleet management,
tracking trips, alert rules/geofences, tenant authorization, and notification
infrastructure. This repository has no product frontend. Device ingestion,
command delivery, and external notification providers have outstanding integration
and security gates; the presence of an API or domain model does not prove a
complete workflow.

Current delivery status, feature inventory, defects, and verification evidence
are in [docs/autonomous/](docs/autonomous/SESSION_STATE.md). Historical plans and
older test totals are not release-readiness claims.

## Architecture

### **Bounded Contexts (DDD Strategic Design)**

| Bounded Context | Purpose | Key Responsibilities |
|----------------|---------|---------------------|
| **Core Tracking** | Heart of the system | Vehicle aggregates, location processing, trip management |
| **Device Communication** | External interface | Protocol parsing (GT06/Autoseeker), device commands |
| **Alerting** | Real-time monitoring | Alert rules, geofencing, notification triggering |
| **Shared** | Cross-cutting concerns | Event bus, common utilities, base exceptions |

### **Module Structure**

```
tracking-engine/
├── pom.xml           # Business backend executable JAR (not a Maven reactor)
├── src/              # core, alerting, notification, userauth, main packages
├── shared/src/       # Contracts/utilities added as sources to both builds
├── device-comm/      # Independent device gateway Maven project
├── tools/            # Verification script and Python device simulators
└── docs/autonomous/  # Current evidence and execution backlog
```

## Module Dependencies

### **Dependency Graph**
```
shared sources → business backend
shared sources → device gateway
business backend ↔ Artemis/Redis ↔ device gateway (integration gates remain)
```

### **Detailed Dependencies**

| Module | Dependencies | Purpose |
|--------|-------------|---------|
| **business backend** | shared sources; Spring/JPA/Redis/Artemis | Logical core, alerting, notification and userauth packages |
| **device-comm** | shared sources; Redis/Artemis/Reactor Netty | Independent protocol/TCP deployment boundary |
| **shared** | Infrastructure libraries | Event/transport contracts, tenant context, common utilities |
 
## Core Features

### **1. Real-Time Tracking**
- **Location Processing**: GPS data ingestion and validation
- **Trip Management**: Automatic trip start/end detection
- **State Management**: Engine state, fuel status, movement patterns

### **2. Device Protocol Support**
- **GT06 Protocol**: Binary protocol parsing
- **Autoseeker Protocol**: Text-based protocol
- **Extensible**: Add new protocols via ITrackerProtocolParser

### **3. Alert System**
- **Speed Alerts**: Configurable speed thresholds
- **Geofencing**: Polygon-based boundary monitoring
- **Idle Time**: Excessive idle detection
- **Custom Rules**: Extensible rule engine

### **4. Device Control**
- **Fuel Cut/On**: Remote fuel system control
- **Safety Rules**: Business rule validation (e.g., no fuel cut while moving)
- **Command Queuing**: Device command management

## Historical package examples

The following examples describe the original module design, not current Maven
projects. Current packages live under `src/main/java/com/surf/tracker/`, shared
code under `shared/src/main/java/com/surf/tracker/`, and device code under
`device-comm/src/main/java/com/surf/tracker/`. Use the current source tree and
feature matrix when locating implementations.

### **Core-Tracking Module**
```
core-tracking/
├── domain/
│   ├── Vehicle.java              # Aggregate Root
│   ├── LocationPoint.java        # Value Object
│   ├── Trip.java                 # Entity
│   ├── FuelStatus.java           # Value Object
│   ├── EngineState.java          # Enum
│   ├── VehicleUpdatedEvent.java  # Domain Event
│   └── FuelCutRequestedEvent.java # Domain Event
├── application/
│   ├── VehicleCommandService.java # Application Service
│   ├── VehicleQueryService.java   # Query Service
│   └── ITripService.java          # Domain Service Interface
├── infrastructure/
│   ├── IVehicleRepository.java    # Repository Interface
│   └── InMemoryVehicleRepository.java
└── exception/
    ├── VehicleException.java
    └── TripException.java
```

### **Device-Comm Module**
```
device-comm/
├── domain/
│   ├── ITrackerProtocolParser.java # Protocol Adapter Interface
│   ├── DeviceDataPacket.java       # Value Object
│   ├── LocationDataEvent.java      # Domain Event
│   └── ProtocolParseException.java # Custom Exception
├── infrastructure/
│   ├── Gt06ProtocolParser.java     # GT06 Implementation
│   └── AutoseekerProtocolParser.java # Autoseeker Implementation
├── application/
│   ├── ParserFactory.java          # Factory Pattern
│   ├── DeviceDataProcessor.java    # Data Processing
│   └── DeviceCommandService.java   # Command Service
└── exception/
    ├── ProtocolException.java
    └── DeviceException.java
```

### **Alerting Module**
```
alerting/
├── domain/
│   ├── IAlertRule.java            # Rule Interface (ISP)
│   ├── MaxSpeedRule.java          # Concrete Rule
│   ├── GeofenceExitRule.java      # Concrete Rule
│   ├── IdleTimeRule.java          # Concrete Rule
│   ├── AlertEvent.java            # Domain Event
│   └── AlertSeverity.java         # Enum
├── application/
│   ├── AlertingEngine.java        # Rule Engine
│   └── AlertRuleEvaluationService.java # Evaluation Service
└── exception/
    └── AlertException.java
```

### **Shared Module**
```
shared/
├── pubsub/
│   ├── EventPublisher.java        # Publisher Interface
│   ├── EventSubscriber.java       # Subscriber Interface
│   ├── EventHandler.java          # Handler Interface
│   ├── SimpleEventBus.java        # Implementation
│   └── DomainEvent.java           # Base Event Class
└── exception/
    ├── DomainException.java       # Base Exception
    ├── ValidationException.java   # Validation Errors
    ├── BusinessRuleException.java # Business Rule Violations
    └── InfrastructureException.java # Infrastructure Errors
```

## Technical Implementation

### **Event Flow**
```
1. Device → Raw Data → SocketServer
2. SocketServer → DeviceDataPacket → DeviceDataProcessor
3. DeviceDataProcessor → LocationDataEvent → EventBus
4. Core Tracking → VehicleUpdate → VehicleUpdatedEvent
5. Alerting → Rule Evaluation → AlertEvent
6. Notification System → Alert Handling → User Notification
```

### **Command Flow**
```
1. User Request → Fuel Cut Command
2. VehicleCommandService → Business Validation
3. Vehicle Aggregate → FuelCutRequestedEvent
4. EventBus → Device-Comm Subscriber
5. Protocol Parser → Device-Specific Command
6. Physical Device → Command Execution
```

## Testing Strategy

### **Unit Tests Coverage**
- **Domain Models**: Value objects, entities, aggregates
- **Application Services**: Command/query services with mocking
- **Protocol Parsers**: GT06 and Autoseeker protocol handling
- **Alert Rules**: Rule evaluation and alert generation
- **Event System**: Pub/sub functionality

### **Test Organization**
```
src/test/java/com/jjenus/tracker/
├── core/
│   ├── domain/           # Domain model tests
│   └── application/      # Service tests with mocks
├── devicecomm/
│   ├── infrastructure/   # Protocol parser tests
│   └── application/      # Factory and processor tests
└── alerting/
    ├── domain/          # Rule implementation tests
    └── application/     # Engine and evaluation tests
```

## Getting Started

### **Prerequisites**
- Installed Java 21 (the wrapper selects it; it does not install Java).
- Network access plus curl/wget and unzip for the wrapper's first Maven 3.9.9 download.
- Docker for disposable PostgreSQL/Redis/Artemis integration verification.

### **Build & Run**
```bash
# From this repository root: build/test business backend
./mvnw -B verify

# Independent device build
./mvnw -B -f device-comm/pom.xml verify

# Default backend test discovery excludes *IT
./mvnw -B test

# Release verification: explicitly includes *IT and owns disposable services
sh tools/verify-release.sh
```

### **Running the Application**
```bash
# Business backend; configure Redis/Artemis and required JWT_SECRET first
./mvnw spring-boot:run

# Or, after successful packaging:
java -jar target/tracking-engine-1.0.0-SNAPSHOT.jar
```

The default database is **ephemeral H2** and is not a durable deployment.
For PostgreSQL use `SPRING_PROFILES_ACTIVE=postgres`, the database/broker/Redis
settings in [SECURITY.md](SECURITY.md), and Flyway migrations. Never switch
migration histories on an existing database. Public registration is disabled;
first-administrator provisioning requires a reviewed out-of-band process.

Backend health is `/actuator/health`; health success is not proof of telemetry,
physical command delivery, or provider delivery. Notification webhooks are
intentionally denied pending provider-specific signature/replay verification.
Protect the device TCP interface on a trusted network; device authentication,
TLS and replay protection are not supplied by the HTTP security boundary.

## Event Types

| Event | Publisher | Subscribers | Purpose |
|-------|-----------|-------------|---------|
| **LocationDataEvent** | Device-Comm | Core-Tracking | Raw location data from devices |
| **VehicleUpdatedEvent** | Core-Tracking | Alerting | Vehicle state changes |
| **FuelCutRequestedEvent** | Core-Tracking | Device-Comm | Fuel cut command request |
| **AlertEvent** | Alerting | Notification Systems | Alert notifications |

## Error Handling

### **Exception Hierarchy**
```
DomainException (abstract)
├── ValidationException (input validation)
├── BusinessRuleException (domain rules)
└── InfrastructureException (technical errors)
    ├── ProtocolException (parsing errors)
    └── DeviceException (device communication)
```

### **Error Codes**
- `VEHICLE_NOT_FOUND`: Vehicle ID doesn't exist
- `VEHICLE_FUEL_CUT_MOVING`: Cannot cut fuel while moving
- `PROTOCOL_PARSE_ERROR`: Failed to parse device data
- `ALERT_RULE_NOT_FOUND`: Alert rule doesn't exist

## Scalability Considerations

### **Horizontal Scaling**
- Each bounded context can be separate microservice
- Event bus can be replaced with Kafka/RabbitMQ
- Repository implementations can use different databases

### **Performance Optimizations**
- Connection pooling for device communications
- Caching for frequently accessed vehicles
- Batch processing for alert evaluations
- Asynchronous event processing

## Future Enhancements

### **Current gaps**
JPA, REST APIs, and authenticated WebSocket infrastructure exist. Remaining
workflow and deployment gates are tracked in
[FEATURE_MATRIX.md](docs/autonomous/FEATURE_MATRIX.md) and
[RELEASE_READINESS.md](docs/autonomous/RELEASE_READINESS.md), rather than inferred
from these historical enhancement lists. Ride-hailing, subscription billing,
and payout design documents are not implemented product capabilities.

### **Long Term**
1. Machine learning for anomaly detection
2. Predictive maintenance alerts
3. Fuel consumption analytics
4. Driver behavior scoring

[//]: # (## Contributing)

[//]: # ()
[//]: # (### **Development Workflow**)

[//]: # (1. Fork the repository)

[//]: # (2. Create feature branch)

[//]: # (3. Write tests for new functionality)

[//]: # (4. Implement feature)

[//]: # (5. Ensure all tests pass)

[//]: # (6. Submit pull request)

### **Coding Standards**
- Follow Google Java Style Guide
- Write comprehensive unit tests
- Use meaningful variable/class names
- Document public APIs with Javadoc
- Keep methods small and focused

## Troubleshooting

### **Common Issues**
1. **IntelliJ import errors**: Reimport Maven project
2. **Old reactor commands fail**: Business packages are now in the root JAR; use the commands above, not `-pl main-app` or `-pl core-tracking`.
3. **Integration failures**: Check the exact test report and disposable service readiness; do not skip or replace failed infrastructure tests with mocks.
4. **Event bus issues**: Verify event subscribers are registered

### **Debug Tips**
- Enable debug logging in main-app
- Use event bus tracing for event flow
- Check exception error codes for specific failures
- Verify protocol parser canHandle methods

---

**Built with ❤️ by Alakere Jenus**
