# Therapy Practice Platform — Cloud-Native MVP

## 1. Overview

The project consists of a cloud-native platform designed to support psychologists in managing their clinical practice by enabling patient management, appointment scheduling, clinical records, and asynchronous notifications triggered by domain events.

The primary goal is not to build a complete clinical platform, but rather to develop a professional and well-architected vertical slice that demonstrates expertise in:

- Spring Boot
- Microservices
- Clean Architecture
- RabbitMQ
- Docker
- Kubernetes
- CI/CD
- Prometheus
- Grafana

---

# 2. Main Objective

Develop a cloud-native microservices platform that allows psychologists to manage patients and appointments, while demonstrating asynchronous event-driven communication and full observability capabilities.

---

# 3. Specific Objectives

1. Implement a microservices architecture with clear separation of responsibilities.

2. Apply Clean Architecture principles, SOLID principles, and software engineering best practices.

3. Use RabbitMQ for asynchronous communication between services.

4. Build a secure API using JWT authentication and role-based authorization.

5. Containerize all services using Docker.

6. Orchestrate the platform using Kubernetes.

7. Expose service metrics through Spring Boot Actuator and Micrometer.

8. Monitor the platform using Prometheus and Grafana.

9. Create CI/CD pipelines for build automation, testing, and Docker image generation.

10. Provide clear technical and architectural documentation.

---

# 4. MVP Scope

## Included in MVP

The platform should support:

- user authentication;
- psychologist and patient management;
- patient creation, update, and retrieval;
- expanded patient profile data, consent tracking, and emergency contacts;
- appointment scheduling, rescheduling, and cancellation;
- clinical cases, care plans, session records, and psychologist practice profiles;
- publication of patient and appointment events;
- event consumption through a notification service;
- simulated notification delivery;
- technical metrics exposure;
- local deployment with Docker Compose;
- Kubernetes deployment;
- basic Grafana dashboards.

## Out of Scope

The following features are intentionally excluded from the MVP:

- payments;
- video calls;
- real-time chat;
- document uploads;
- prescriptions or diagnostics;
- clinical recommendations and decision support;
- emergency psychological systems;
- calendar integrations;
- advanced frontend features.

These features may be documented as future roadmap items.

---

# 5. Users and Roles

## Psychologist

Primary platform user.

Capabilities:

- create patients;
- view patients;
- update patient information;
- maintain clinical cases, care plans, and session records;
- schedule appointments;
- reschedule appointments;
- cancel appointments;
- view appointment history.

## Patient

Secondary platform user.

Capabilities:

- view own appointments;
- receive simulated notifications.

## Admin

Administrative/technical user.

Capabilities:

- manage users;
- access protected administrative endpoints;
- inspect platform health and operational state.

---

# 6. Microservices

## 6.1 Auth/User Service

Responsible for authentication, authorization, and user management.

### Responsibilities

- register users;
- authenticate users;
- issue JWT tokens;
- validate roles;
- manage basic user profiles;
- differentiate psychologists, patients, and admins.

### Main Entities

- User
- Role
- Credentials
- UserProfile

### Example Endpoints

```http
POST /auth/register
POST /auth/login
GET /users/me
GET /users/{id}
```

---

## 6.2 Patient Service

Responsible for psychologist patient management.

### Responsibilities

- create patient;
- update patient information;
- retrieve patient data;
- list psychologist patients;
- associate patients with psychologists;
- publish patient creation events.

### Main Entities

- Patient
- PatientProfile
- EmergencyContact
- ConsentStatus
- ContactPreference

### Example Endpoints

```http
POST /patients
GET /patients
GET /patients/{id}
PUT /patients/{id}
PATCH /patients/{id}/status
```

### Published Event

- `PatientCreated`

---

## 6.3 Appointment Service

Responsible for appointment management.

### Responsibilities

- schedule appointments;
- reschedule appointments;
- cancel appointments;
- retrieve psychologist appointments;
- retrieve patient appointments;
- publish appointment-related events.

### Main Entities

- Appointment
- AppointmentStatus
- AppointmentType

### Possible States

- `SCHEDULED`
- `RESCHEDULED`
- `CANCELLED`
- `COMPLETED`

### Example Endpoints

```http
POST /appointments
GET /appointments
GET /appointments/{id}
PATCH /appointments/{id}/reschedule
PATCH /appointments/{id}/cancel
PATCH /appointments/{id}/complete
```

### Published Events

- `AppointmentScheduled`
- `AppointmentRescheduled`
- `AppointmentCancelled`
- `AppointmentCompleted`

---

## 6.4 Clinical Service

Responsible for clinical workflow records owned by the psychologist.

### Responsibilities

- maintain psychologist practice profiles;
- create and track clinical cases for patients;
- maintain care plans and care goals;
- create manual session records;
- consume completed appointment events to create pending session-record shells;
- expose patient clinical history without sharing clinical details over RabbitMQ.

### Main Entities

- PracticeProfile
- ClinicalCase
- CarePlan
- CareGoal
- SessionRecord

### Example Endpoints

```http
GET /practice-profile
PUT /practice-profile
POST /clinical-cases
GET /clinical-cases
GET /clinical-cases/{id}
PATCH /clinical-cases/{id}/status
GET /clinical-cases/{id}/care-plan
PUT /clinical-cases/{id}/care-plan
GET /clinical-cases/{id}/session-records
POST /clinical-cases/{id}/session-records
PATCH /session-records/{id}
GET /patients/{patientId}/clinical-history
```

### Consumed Events

- `AppointmentCompleted`

---

## 6.5 Notification Service

Responsible for consuming RabbitMQ events and simulating notifications.

### Responsibilities

- consume RabbitMQ events;
- process notifications;
- simulate email/log notifications;
- store notification history;
- expose event-processing metrics.

### Main Entities

- Notification
- NotificationStatus
- NotificationType

### Consumed Events

- `PatientCreated`
- `AppointmentScheduled`
- `AppointmentRescheduled`
- `AppointmentCancelled`

### Example Endpoints

```http
GET /notifications
GET /notifications/{id}
```

---

# 7. RabbitMQ Events

## Exchange

```text
therapy.events.exchange
```

### Exchange Type

```text
topic
```

## Routing Keys

```text
patient.created
appointment.scheduled
appointment.rescheduled
appointment.cancelled
appointment.completed
```

---

## Minimal Events

### PatientCreated

Triggered when a psychologist creates a new patient.

### Example Payload

```json
{
  "eventId": "uuid",
  "eventType": "PatientCreated",
  "occurredAt": "2026-05-26T15:00:00Z",
  "patientId": "uuid",
  "psychologistId": "uuid",
  "patientEmail": "patient@example.com"
}
```

---

### AppointmentScheduled

```json
{
  "eventId": "uuid",
  "eventType": "AppointmentScheduled",
  "occurredAt": "2026-05-26T15:00:00Z",
  "appointmentId": "uuid",
  "patientId": "uuid",
  "psychologistId": "uuid",
  "scheduledAt": "2026-06-01T10:00:00Z"
}
```

---

### AppointmentRescheduled

```json
{
  "eventId": "uuid",
  "eventType": "AppointmentRescheduled",
  "occurredAt": "2026-05-26T15:00:00Z",
  "appointmentId": "uuid",
  "previousDate": "2026-06-01T10:00:00Z",
  "newDate": "2026-06-03T14:00:00Z"
}
```

---

### AppointmentCancelled

```json
{
  "eventId": "uuid",
  "eventType": "AppointmentCancelled",
  "occurredAt": "2026-05-26T15:00:00Z",
  "appointmentId": "uuid",
  "cancelledBy": "psychologist",
  "reason": "Patient requested cancellation"
}
```

---

### AppointmentCompleted

```json
{
  "eventId": "uuid",
  "eventType": "AppointmentCompleted",
  "occurredAt": "2026-05-26T15:00:00Z",
  "appointmentId": "uuid",
  "patientId": "uuid",
  "psychologistId": "uuid",
  "appointmentDate": "2026-06-01T10:00:00Z",
  "modality": "ONLINE"
}
```

---

# 8. Functional Requirements

## FR01 — Authentication

The system shall allow users to authenticate and receive a JWT token.

## FR02 — Authorization

The system shall restrict operations according to user roles.

## FR03 — Patient Management

Psychologists shall be able to create, retrieve, list, and update patients.

## FR04 — Psychologist-Patient Association

Each patient shall be associated with a responsible psychologist.

## FR05 — Appointment Scheduling

Psychologists shall be able to schedule appointments for patients.

## FR06 — Appointment Rescheduling

Psychologists shall be able to reschedule appointments.

## FR07 — Appointment Cancellation

Psychologists shall be able to cancel appointments.

## FR08 — Appointment Retrieval

Psychologists shall be able to retrieve upcoming appointments.

## FR09 — Clinical Workflow

Psychologists shall be able to maintain practice profiles, clinical cases, care plans, and session records.

## FR10 — Patient Clinical History

Psychologists shall be able to retrieve the clinical history for their own patients.

## FR11 — Event Publishing

Patient Service and Appointment Service shall publish RabbitMQ events.

## FR12 — Event Consumption

Notification Service shall consume RabbitMQ events and generate notifications.

Clinical Service shall consume completed appointment events and create pending session-record shells.

## FR13 — Notification History

The system shall maintain a basic notification history.

## FR14 — Observability

Each service shall expose metrics, health checks, and structured logs.

---

# 9. Non-Functional Requirements

## NFR01 — Scalability

Each microservice shall be independently scalable in Kubernetes.

## NFR02 — Maintainability

Each service shall follow a Clean Architecture structure.

## NFR03 — Testability

Each service shall include unit tests and integration tests.

## NFR04 — Security

Protected APIs shall require JWT authentication.

## NFR05 — Data Isolation

Each service shall own its own database or logical schema.

## NFR06 — Observability

The platform shall expose metrics, health checks, and operational telemetry.

## NFR07 — Resilience

Asynchronous processing shall tolerate temporary notification service failures.

## NFR08 — Portability

The platform shall run locally through Docker Compose and in Kubernetes.

## NFR09 — Documentation

The project shall provide clear architecture and setup documentation.

---

# 10. Technology Stack

## Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring AMQP
- Spring Boot Actuator
- Micrometer
- OpenAPI/Swagger

## Messaging

- RabbitMQ
- Topic Exchanges
- Durable Queues
- Dead Letter Queues (optional)

## Database

- PostgreSQL
- One database/schema per service

## Observability

- Prometheus
- Grafana
- Spring Boot Actuator
- Micrometer
- Structured JSON logging (optional)

## Containerization & Orchestration

- Docker
- Docker Compose
- Kubernetes
- kubectl
- Minikube, Kind, or Docker Desktop Kubernetes
- Helm (optional)

## CI/CD

- GitHub Actions
- Maven
- Automated tests
- Docker image builds
- Docker image publishing (optional)
- Kubernetes deployment (optional)

## Frontend (Optional)

- React
- TypeScript
- Vite or Next.js
- Tailwind CSS

## Testing

- JUnit 5
- Mockito
- Testcontainers
- Spring Boot Test
- REST Assured (optional)

---

# 11. Internal Service Architecture

Each Spring Boot service should follow a Clean Architecture structure that is
clear enough to demonstrate architectural discipline without introducing
unnecessary enterprise-level complexity for the prototype.

The intended dependency direction is always from the outer layers to the inner
layers:

```text
infrastructure / adapters -> application -> domain
```

The domain layer must remain independent from Spring, persistence frameworks,
RabbitMQ, HTTP controllers, DTOs, and database-specific concerns.

Each Spring Boot service should follow a structure similar to:

```text
service-name/
  src/main/java/com/example/service/
    domain/
      entities/
      valueobjects/
      events/
      exceptions/
      services/
    application/
      usecases/
      services/
      ports/
        repositories/
        messaging/
    adapters/
      inbound/
        rest/
          controllers/
          dto/
          mappers/
      outbound/
        persistence/
          repositories/
          mappers/
        messaging/
    infrastructure/
      spring/
      persistence/
        jpa/
      messaging/
        rabbitmq/
      security/
      config/
      observability/
```

---

## Layers

### Domain

Contains entities, value objects, domain events, domain exceptions, and domain
rules.

The domain layer must not contain repository interfaces, controller classes,
DTOs, Spring annotations, JPA annotations, RabbitMQ configuration, or any other
technology-specific detail.

Entities preserve identity throughout their lifecycle. Business methods mutate
the existing entity only after validating the complete operation and return the
same instance. Equality and hash codes are based only on typed identity value
objects.

Value objects are immutable, compare by value, normalize and validate their own
data, and have no independent lifecycle. Each service owns its value-object
types to preserve bounded-context autonomy. Patient phone numbers use validated
international E.164 representation.

### Application

Contains use cases, application services, orchestration logic, and application
ports.

Repository interfaces belong here as outbound ports, so the application layer
depends only on abstractions. Examples include `PatientRepository`,
`AppointmentRepository`, `NotificationRepository`, and event publisher ports
such as `PatientEventPublisher`.

### Adapters

Contains the concrete adapters that translate between external mechanisms and
the application layer.

Inbound adapters include REST controllers, request/response DTOs, and HTTP
mappers. Outbound adapters include repository implementations, persistence
mappers, and messaging publishers/consumers that implement the application
ports.

MapStruct-backed boundary mappers translate between request/response DTOs,
application commands/results, domain objects, JPA entities, and RabbitMQ
payloads. Mappers contain conversion only; validation belongs to value objects
and business decisions belong to domain or application policies. Repository
save ports return no replacement entity.

### Infrastructure

Contains technology-specific configuration and framework integration, including
Spring Boot configuration, JPA configuration, RabbitMQ configuration, security,
Actuator, Micrometer, and observability setup.

Infrastructure classes may wire concrete adapters to application ports, but
domain and application code must not depend on infrastructure packages.

---

# 12. Main Application Flow

## Patient Creation and Appointment Scheduling Flow

1. Psychologist authenticates.
2. Auth Service issues JWT token.
3. Psychologist creates a patient through Patient Service.
4. Patient Service persists patient data.
5. Patient Service publishes `PatientCreated`.
6. Notification Service consumes the event.
7. Notification Service creates a simulated notification.
8. Psychologist schedules an appointment.
9. Appointment Service persists appointment data.
10. Appointment Service publishes `AppointmentScheduled`.
11. Notification Service consumes the event.
12. Notification Service creates a simulated notification.
13. Psychologist creates or updates the patient's clinical case and care plan.
14. Psychologist completes an appointment.
15. Appointment Service publishes `AppointmentCompleted`.
16. Clinical Service consumes the event and creates a pending session record.
17. Metrics become available in Prometheus.
18. Grafana dashboards display operational metrics.

---

# 13. Recommended Grafana Dashboards

## General Platform Dashboard

- service health;
- number of running instances;
- CPU and memory usage;
- HTTP request count;
- average response latency;
- error rate.

## RabbitMQ Dashboard

- published messages;
- consumed messages;
- queued messages;
- rejected messages;
- dead letter messages.

## Domain Dashboard

- patients created;
- appointments scheduled;
- appointments cancelled;
- clinical cases opened;
- session records pending notes;
- processed notifications;
- notification failures.

---

# 14. Success Criteria

The project shall be considered successful if it demonstrates:

- JWT authentication;
- patient management;
- appointment scheduling/rescheduling/cancellation;
- clinical cases, care plans, and session records;
- RabbitMQ event publishing and consumption;
- functioning Notification Service;
- containerized services;
- local Docker Compose execution;
- Kubernetes deployment;
- Prometheus metrics collection;
- Grafana dashboards;
- functional CI/CD pipeline;
- clear technical documentation.

---

# 15. Future Roadmap

Potential future improvements:

- complete patient portal;
- advanced clinical recommendations and decision support;
- follow-up questionnaires;
- document uploads;
- Google Calendar integration;
- real email notifications;
- OAuth2 authentication;
- API Gateway;
- service discovery;
- distributed tracing with OpenTelemetry;
- Helm charts;
- cloud deployment;
- complete frontend;
- multi-tenancy support.

---

# 16. Suggested Project Name

Possible names:

- `therapy-practice-platform`
- `mindcare-platform`
- `therapy-cloud`
- `clinicflow`
- `mindful-practice`
- `theraops`
- `psycare-cloud`

### Recommended Name

```text
therapy-practice-platform
```

Clear, professional, and easy to understand in a GitHub portfolio.
