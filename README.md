***Task Manager REST API***

This Task Manager REST API was designed to help users collaborate within teams, so they can efficiently manage tasks. It is enforced role-based access control to teams and tasks. The project follows clean architecture principles, strong validation rules, and explicit domain constraints to ensure data integrity and maintainability.

This API is intended to be used as the backend for a web or mobile application.

📌 ***Overview***

The Task Manager API allows authenticated users to:

- Create, update, and track tasks

- Create and manage teams

- Assign roles to team members 

- Enforce permissions based on team roles

- Secure all endpoints using JWT authentication

🔑 ***Key Features***

👤 ***Authentication & Security***

- JWT‑based authentication

- Secure password handling

- Authentication filter applied to protected endpoints

- Role‑aware authorization logic

✅ ***Task Management***

- Create tasks within teams

- Assign tasks to users

- Update task status and metadata

- Fetch task summaries

👥 ***Team Management***

- Create and update teams

- Invite users to teams by username or email

- Remove team members

- List team members with assigned roles

👤 ***Role‑Based Access Control***

Each team member has a role:

- OWNER –> Full control over the team

- ADMIN –> Can manage members and tasks

- MEMBER –> Can interact with tasks

Role logic is encapsulated directly in an enum for clarity and reuse.

🔄 ***Clean DTO Mapping***

- Clear separation between entities and API responses

- Dedicated DTOs for create, update, and response flows

- Centralized mappers for consistency

🧪 ***Testability***

- Services structured for unit testing

- Dependency injection throughout the application

- Mock‑friendly service boundaries

***API Architecture***

Key Layers

Controller Layer – Handles HTTP requests and responses

Service Layer – Contains business logic and validation

Repository Layer – Data access via Spring Data JPA

Model Layer – Domain entities and enums

Mapper Layer – DTO ↔ Entity transformations

🛠 ***Tech Stack***

- Java 17+

- Spring Boot

- Spring Security

- Spring Data JPA

- Hibernate

- JWT Authentication

- Maven

- PostgreSQL

- Docker


🔑 **Authentication Flow**

User registers or logs in

API returns a JWT token and a verification email

📡 ***API Endpoints***

Base path: /api

- All endpoints require a valid JWT except the authentication endpoints under /api/auth

    - **Auth (/api/auth)**

    |   Method    |                  Endpoint                  |                  Description                 |
    |-------------|--------------------------------------------|----------------------------------------------|
    |     POST    |                   /users                   |                Create a user                 |
    |     GET     |               /users/profile               |  Get the current authenticated user profile  |
    |     GET     |            /users/profile/teams            |         Get the current user’s teams         |
    |     GET     |         /users/username/{username}         |            Find a user by username           |
    |     GET     |              /users?email=...              |             Find a user by email             |
    |     PUT     |                /users/{id}                 |             Update your own user             |
    |    DELETE   |                /users/{id}                 |             Delete your own user             |
    
  - **Teams (/api/teams)**

    |   Method     |                  Endpoint                        |                  Description                  |
    |--------------|--------------------------------------------------|-----------------------------------------------|
    |     POST     |                      /teams                      |                Create a team                  |
    |     GET      |                      /teams                      |        List teams for the current user        |
    |     PUT      |                  /teams/{teamId}                 |                Update a team                  |
    |    DELETE    |                 /teams/{teamId}                  |                Delete a team                  |
    |     GET      |              /teams/{teamId}/members             |    List team members (must belong to team)    |
    |     POST     |     /teams/{teamId}/members/add?role=MEMBER      |       Add a member by username or email       |
    |     PUT      |  /teams/{teamId}/users/{userId}/role?role=ADMIN  |      Update a user’s role within the team     |
    |    DELETE    |           /teams/{teamId}/users/{userId}         |          Remove a user from the team          |
    |     GET      |               /teams/{teamId}/tasks              |      Get tasks for a team (summary view)      |

  - **Tasks (/api/tasks)**

    |   Method     |                  Endpoint             |                  Description                   |
    |--------------|---------------------------------------|------------------------------------------------|
    |     POST     |                 /tasks                |                Create a task                   |
    |     GET      |                 /tasks                |     Get tasks assigned to the current user     |
    |     GET      |              /tasks/{id}              |                Get a task by id                |
    |     PUT      |              /tasks/{id}              |                  Update a task                 |
    |    DELETE    |              /tasks/{id}              |                  Delete a task                 |
    |     GET      |          /tasks/team/{teamId}         |            Get all tasks for a team            |
    |     GET      |     /tasks/search/title/{keyword}     |        Search tasks by keyword in title        |
    |     GET      |     /tasks/search/status/{status}     |             Filter tasks by status             |
    |     GET      |            /tasks/statuses            |         List all possible task statuses        | 


The API uses custom exceptions for clarity:

- ResourceNotFoundException

- UnauthorizedActionException

- ConflictException

Each exception maps to an appropriate HTTP status code and message.
