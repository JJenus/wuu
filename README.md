# Tracking Engine - Complete DDD Implementation

## Overview

For authentication configuration, forced re-login during migration, denied
callbacks and deployment limitations, see [SECURITY.md](SECURITY.md).

## Opt-in local account seeds and picker

This isolated checkout contains the user/admin seed implementation. The active `../sidie` checkout is unchanged. Build from this root with `./mvnw -B package`; the current root POM produces `target/tracking-engine-1.0.0-SNAPSHOT.jar` (older module diagrams below are historical).

Enable only against a local disposable database: `SPRING_PROFILES_ACTIVE=local`, `SERVER_ADDRESS=127.0.0.1`, `LOCAL_ACCOUNTS_ENABLED=true`, strong private `LOCAL_USER_PASSWORD` and `LOCAL_ADMIN_PASSWORD`, and `JWT_SECRET`. There are no default passwords. Local settings must never be enabled for public/production data; startup refuses enabled configurations without a literal loopback binding and local-only profile. `prod`/`production` profiles are explicitly refused.

Startup transactionally seeds `user@wuu.local` (VIEWER) and `admin@wuu.local` (TENANT_ADMIN) in `wuu-local`. A migrated permission row serializes seed runs before existence checks. Case-insensitive email checks skip existing identities without password reset, profile/flag changes, role elevation or membership rewriting; organization/role reuse and database constraints prevent duplicate seeds. Missing passwords for missing accounts fail startup instead of creating default credentials. Keep the private credential file across restarts.

`GET /api/v1/auth/local-accounts?page=0` is an opt-in loopback-only, no-store directory of enabled/unlocked database users. It returns pages of 100 `{email,display_name}` records plus `next_page`, never hashes, tokens or permissions. It has no passwordless-login counterpart. The Wuu frontend has its own separate picker opt-in and still requires normal password authentication.

See [local account verification](plan-docs/local-accounts-verification.md) for RED/GREEN evidence and limitations. Workspace-specific generated runtime settings are in ignored `.env`; Spring does not load that file automatically. Export it using your local process manager/shell before launching the JAR, and never commit it.

A comprehensive vehicle tracking engine built with **Domain-Driven Design (DDD)** principles, implementing all core features of a tracking solution including real-time locating, alerting, geofencing, and device control.

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
├── shared/           # Foundation layer
├── core-tracking/    # Core domain logic
├── device-comm/      # Protocol handling
├── alerting/        # Alert system
└── main-app/        # Composition root
```

## Module Dependencies

### **Dependency Graph**
```
shared (Foundation)
   ↑
core-tracking (Domain Core)
   ↑           ↖
device-comm   alerting (Domain Services)
   ↑             ↑
   └──────┐      │
          main-app (Application)
```

### **Detailed Dependencies**

| Module | Dependencies | Purpose |
|--------|-------------|---------|
| **shared** | None | Common infrastructure: Event bus, base exceptions, utilities |
| **core-tracking** | shared | Core domain: Vehicle, LocationPoint, Trip aggregates |
| **device-comm** | shared + core-tracking | Device protocols, command translation |
| **alerting** | shared + core-tracking | Alert rules, monitoring engine |
| **main-app** | ALL modules | Application wiring, dependency injection |
 
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

## Project Structure

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
- Java 17 or higher
- Maven 3.6 or higher
- IntelliJ IDEA (recommended)

### **Build & Run**
```bash
# Clone and build
git clone <repository>
cd tracking-engine

# Build all modules
mvn clean install

# Run tests
mvn test

# Run specific module tests
cd core-tracking
mvn test
```

### **Running the Application**
```bash
cd main-app
mvn exec:java
```

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

### **Short Term**
1. Database persistence (JPA/Hibernate)
2. REST API for external integrations
3. WebSocket support for real-time updates
4. Configuration management system

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
2. **Circular dependencies**: Check module imports follow the dependency graph
3. **Test failures**: Ensure all modules are built (`mvn clean install`)
4. **Event bus issues**: Verify event subscribers are registered

### **Debug Tips**
- Enable debug logging in main-app
- Use event bus tracing for event flow
- Check exception error codes for specific failures
- Verify protocol parser canHandle methods

---

**Built with ❤️ by Alakere Jenus**
