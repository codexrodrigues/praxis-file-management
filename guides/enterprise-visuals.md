# Enterprise Visual Reference

## Solution Architecture Diagram

```mermaid
flowchart LR
    Client[Client] --> Gateway[API Gateway]
    Gateway --> RateLimiter[RateLimitingFilter]
    RateLimiter --> Core[Core Services]
    Core --> Storage[(External Storage)]
    Core --> AV[Antivirus]
```

## Integration Patterns

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Upload Service
    participant St as Storage

    alt Direct upload
        C->>S: POST /upload
        S->>St: Store file
        St-->>S: OK
        S-->>C: 201 Created
    else Pre-signed URL
        C->>S: POST /presign
        S-->>C: URL
        C->>St: PUT file
        St-->>C: 200 OK
    else Streaming to S3
        C->>S: POST /stream
        S->>St: Stream chunks
        St-->>S: OK
        S-->>C: 202 Accepted
    end
```

## Security Models

```mermaid
flowchart LR
    IdP[SSO / Identity Provider] --> Auth[JWT Token]
    Auth --> Service[Upload Service]
    Service -->|role:MONITORING| Metrics[Monitoring Endpoint]
    Service -->|role:FILE_UPLOAD| Upload[Upload Endpoint]
```

## Deployment Topologies

```mermaid
flowchart LR
    subgraph SingleTenant[Single Tenant]
        App1[Upload Service]
        App1 --> Vol1[(Persistent Volume)]
    end
    subgraph MultiTenant[Multi Tenant]
        App2[Upload Service Pod] --> Mesh[Service Mesh]
        Mesh --> Shared[(Shared Storage)]
    end
```

## User Journey Map (HR Scenario)

```mermaid
flowchart LR
    Emp[Employee] --> Upload[Send document]
    Upload --> Validate[Validations]
    Validate --> Store[Store]
    Store --> Audit[Audit Log]
```

## Process Flow Diagram (Compliance)

```mermaid
flowchart LR
    Upload[Upload]
    Upload --> Scan[Antivirus]
    Scan --> Approve[Approval]
    Approve --> Retain[Retention]
    Retain --> Expunge[Expurgo]
```
