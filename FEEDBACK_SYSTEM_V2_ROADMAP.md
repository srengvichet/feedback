# Feedback System V2 Roadmap

## Purpose

This document defines the next professional development phase for the Student Feedback System.

V2 focuses on four goals:
- strengthen security and data integrity
- improve reporting and analytics
- improve maintainability and testing
- prepare the system for real institutional deployment

---

## Current Strengths

The current system already includes:
- role-based access for admin, teacher, and student
- Spring Boot web application with Thymeleaf
- REST APIs with JWT authentication
- feedback submission workflow
- teacher self-assessment workflow
- join code and enrollment flow
- report generation support
- Docker and HTTPS deployment support

This is a strong V1 foundation.

---

## V2 Priorities

### Priority 1. Security and Integrity

Goal:
Make the system safer and more reliable for real users.

Tasks:
- remove hardcoded fallback secrets from production configuration
- separate `dev` and `prod` application profiles
- add stronger password and account policies
- add audit logs for admin changes
- enforce one-time submission rules at service and database level
- remove sensitive data from logs
- add rate limiting for login and password reset endpoints

Expected result:
The system becomes safer for campus-wide use.

---

### Priority 2. Reporting and Analytics

Goal:
Help administrators and teachers understand results faster.

Tasks:
- add dashboard cards for response rates and averages
- add charts by semester, course, section, and teacher
- add trend comparison across semesters
- add export to Excel and CSV for statistics and comments
- add filters by cohort, group, shift, department, and semester
- improve AI summary review workflow before publishing

Expected result:
The system becomes useful not only for data collection, but also for academic decision-making.

---

### Priority 3. User Experience and Professional UI

Goal:
Make the system easier to use for students, teachers, and administrators.

Tasks:
- create dashboard home pages for each role
- improve table search, filtering, and pagination
- improve mobile-friendly layout
- add clearer success and error messages
- improve teacher report screens with charts and summaries
- add profile image support and better account settings UI

Expected result:
The system looks and feels more professional.

---

### Priority 4. Testing and Code Quality

Goal:
Reduce bugs and make future changes safer.

Tasks:
- add unit tests for service-layer rules
- add integration tests for API endpoints
- add security tests for role-based access
- add repository tests for custom queries
- clean static-analysis warnings and dead code
- add a global exception handler for API responses
- standardize validation using `@Valid` and DTO constraints

Expected result:
The system becomes easier to maintain and more reliable.

---

### Priority 5. Deployment and Operations

Goal:
Prepare the project for real deployment and team collaboration.

Tasks:
- use database migrations such as Flyway or Liquibase
- add environment-based configuration management
- add CI pipeline for build and tests
- add log files and production log levels
- add backup and restore procedure for the database
- document deployment steps for local, test, and production environments
- add health check and monitoring endpoints

Expected result:
The project becomes more suitable for production operations.

---

## Recommended New Features

### Academic Features
- anonymous feedback mode with configurable privacy policy
- midterm and final feedback cycles
- separate question sets by department or course type
- teacher response notes after receiving feedback
- section comparison report for department heads

### Student Features
- notification center for open feedback windows
- save feedback draft before final submission
- feedback history page showing completed sections
- improved join-section experience with QR scan support in mobile app

### Teacher Features
- per-question score chart
- comment sentiment summary
- self-improvement action plan tracking
- semester-to-semester performance comparison

### Admin Features
- audit trail of admin actions
- bulk import/export for registry, teachers, and schedules
- bulk question management
- scheduled email and Telegram reminders
- report approval workflow

---

## Suggested Delivery Plan

### Phase 1. Stabilization
Duration: 1 to 2 weeks

Deliverables:
- fix submission integrity issues
- remove sensitive logs
- clean important warnings
- add core service tests

### Phase 2. Professionalization
Duration: 2 to 3 weeks

Deliverables:
- validation and exception handling improvements
- dashboards and better UX
- CSV and Excel export improvements
- environment-specific configuration

### Phase 3. Analytics and Notifications
Duration: 2 to 4 weeks

Deliverables:
- reporting dashboard
- charts and trend analysis
- reminder notifications by email and Telegram
- improved AI summary workflow

### Phase 4. Deployment Readiness
Duration: 1 to 2 weeks

Deliverables:
- CI pipeline
- database migration scripts
- production deployment documentation
- monitoring and backup procedure

---

## Recommended Architecture Improvements

To make the codebase more professional over time:
- move configuration into typed property classes
- reduce controller logic and push rules into service classes
- add dedicated DTO validation for API input
- add service interfaces for larger modules if the project grows
- add package-level separation for admin, teacher, student, and shared modules
- consider frontend modernization later if the UI grows beyond Thymeleaf comfort level

---

## Success Indicators for V2

V2 is successful if:
- duplicate submissions are impossible
- critical flows are covered by tests
- the system can be deployed with environment-specific secrets
- admin and teacher dashboards provide quick insights
- reports are easier to export and present
- logs and deployment steps are suitable for professional use

---

## Final Recommendation

The best immediate path is:
1. harden the current workflows
2. add automated tests
3. improve dashboards and reporting
4. prepare the system for production deployment

This sequence gives the best balance between technical quality and visible project value.