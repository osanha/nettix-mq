# nettix-mq

A lightweight, WebSocket-based message queue built on [nettix](https://github.com/osanha/nettix).

## Overview

nettix-mq provides a simple yet powerful message queue implementation using WebSocket as the transport layer and Kryo for high-performance serialization. It supports both synchronous (request-response) and asynchronous messaging patterns with unicast and broadcast capabilities.

## Features

- **WebSocket Transport**: Reliable, bidirectional communication over WebSocket
- **Kryo Serialization**: Fast, compact binary serialization with object pooling
- **Sync/Async Messaging**: Support for both fire-and-forget and request-response patterns
- **Unicast & Broadcast**: Send messages to a single client or all connected clients
- **Type-safe Messages**: Enum-based message types for compile-time safety
- **Connection Management**: Built-in heartbeat and timeout handling

## Requirements

- Java 6+
- nettix 3.10+
- [Kryo](https://github.com/EsotericSoftware/kryo) - Fast binary serialization
- [eraasoft objectpool](https://mvnrepository.com/artifact/nf.fr.eraasoft/objectpool) - Kryo instance pooling

## Installation

### Maven

```xml
<dependency>
    <groupId>io.nettix</groupId>
    <artifactId>nettix-mq</artifactId>
    <version>3.10</version>
</dependency>
```

## Quick Start

### Define Message Types

```java
public enum EventType {
    USER_LOGIN,
    USER_LOGOUT,
    DATA_UPDATE,
    NOTIFICATION
}
```

### Create Kryo Factory

```java
public class MyKryoFactory extends PoolableKryoFactory {
    @Override
    protected void configure(Kryo kryo) {
        kryo.register(UserLoginEvent.class);
        kryo.register(DataUpdateEvent.class);
        kryo.register(NotificationEvent.class);
    }
}
```

### Server

```java
MessageQueueServer<EventType> server = new MessageQueueServer<>(
    "NotificationServer",
    "/mq",
    new MyKryoFactory(),
    9000
);

server.setUp();

// Broadcast to all clients (async)
server.asyncWrite(EventType.NOTIFICATION, new NotificationEvent("System update"));

// Broadcast to all clients (sync - wait for ACK)
ChannelGroupFuture future = server.syncWrite(EventType.DATA_UPDATE, data);
future.await();

// Send to specific client (async)
server.asyncWrite(channel, EventType.USER_LOGIN, loginEvent);

// Send to specific client (sync)
CallableChannelFuture<Object> future = server.syncWrite(channel, EventType.DATA_UPDATE, data);
Object response = future.get();
```

### Client

```java
MessageQueueClient<EventType> client = new MessageQueueClient<>(
    "NotificationClient",
    "ws://localhost:9000/mq",
    new MyKryoFactory()
);

// Register message listeners
client.addListener(EventType.NOTIFICATION, new MessageListener<NotificationEvent, Void>() {
    @Override
    public Void messageReceived(Channel ch, NotificationEvent event) {
        System.out.println("Received: " + event.getMessage());
        return null;  // No response for async messages
    }
});

client.addListener(EventType.DATA_UPDATE, new MessageListener<DataUpdateEvent, Boolean>() {
    @Override
    public Boolean messageReceived(Channel ch, DataUpdateEvent event) {
        processUpdate(event);
        return true;  // Response for sync messages
    }
});

client.setUp();
client.connect("localhost", 9000);
```

## Messaging Patterns

### Broadcast vs Unicast

nettix-mq supports two delivery patterns:

| Pattern | Method | Description |
|---------|--------|-------------|
| **Broadcast** | `asyncWrite(type, value)` | Send to all connected clients |
| **Unicast** | `asyncWrite(channel, type, value)` | Send to a specific client |

#### Broadcast Example

```java
// Notify all connected clients about a system event
server.asyncWrite(EventType.SYSTEM_ALERT, new Alert("Server maintenance in 10 minutes"));

// Broadcast with acknowledgement from all clients
ChannelGroupFuture future = server.syncWrite(EventType.CONFIG_UPDATE, newConfig);
future.addListener(new ChannelGroupFutureListener() {
    @Override
    public void operationComplete(ChannelGroupFuture f) {
        if (f.isCompleteSuccess()) {
            System.out.println("All clients acknowledged");
        } else {
            // Handle partial failures
            for (ChannelFuture cf : f) {
                if (!cf.isSuccess()) {
                    System.err.println("Failed: " + cf.getChannel().getRemoteAddress());
                }
            }
        }
    }
});
```

```
Server                    Client A      Client B      Client C
   │                          │             │             │
   │─── asyncWrite(msg) ─────>│             │             │
   │──────────────────────────────────────>│             │
   │─────────────────────────────────────────────────────>│
   │                          │             │             │
```

#### Unicast Example

```java
// Send a private message to a specific client
Channel targetClient = getClientByUserId("user123");
server.asyncWrite(targetClient, EventType.PRIVATE_MSG, message);

// Request-response with a specific client
CallableChannelFuture<UserInfo> future = server.syncWrite(
    targetClient,
    EventType.GET_USER_INFO,
    new UserInfoRequest("user123")
);
UserInfo info = (UserInfo) future.get();
```

```
Server                    Client A      Client B      Client C
   │                          │             │             │
   │─ asyncWrite(chB, msg) ───────────────>│             │
   │                          │             │ (only B)   │
   │                          │             │             │
```

### Sync vs Async

| Mode | Method | Return Type | Use Case |
|------|--------|-------------|----------|
| **Async** | `asyncWrite()` | `ChannelFuture` | Fire-and-forget, notifications |
| **Sync** | `syncWrite()` | `CallableChannelFuture<T>` | Request-response, confirmations |

#### Async Flow (Fire-and-Forget)

```
Server                          Client
   │                               │
   │──── asyncWrite(type, msg) ───>│
   │                               │ messageReceived()
   │     (no response expected)    │ returns null
   │                               │
```

#### Sync Flow (Request-Response)

```
Server                          Client
   │                               │
   │──── syncWrite(type, msg) ────>│
   │                               │ messageReceived()
   │<──────── response ────────────│ returns value
   │                               │
   │ future.get() returns response │
```

## Configuration

### Server Options

```java
MessageQueueServer<EventType> server = new MessageQueueServer<>(
    "MyMQ",
    "/mq",
    kryoFactory,
    9000,
    60,   // enquire link interval (seconds)
    10    // response timeout (seconds)
);

server.setConnectionHandler(new ConnectStateEventHandler() {
    @Override
    public void channelConnected(Channel ch) {
        System.out.println("Client connected: " + ch.getRemoteAddress());
    }

    @Override
    public void channelDisconnected(Channel ch) {
        System.out.println("Client disconnected: " + ch.getRemoteAddress());
    }
});
```

### Client Options

```java
MessageQueueClient<EventType> client = new MessageQueueClient<>(
    "MyClient",
    uri,
    kryoFactory,
    60,   // enquire link interval (seconds)
    10    // response timeout (seconds)
);

client.setConnectionHandler(handler);
```

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    MessageQueueServer                     │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ asyncWrite() / syncWrite() - Unicast & Broadcast    │ │
│  └─────────────────────────────────────────────────────┘ │
│                          │                                │
│  ┌───────────────────────▼───────────────────────────┐   │
│  │              WebSocket Transport                   │   │
│  │         (KryoWebSocketEncoder/Decoder)            │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
                           │
                    WebSocket Connection
                           │
┌──────────────────────────────────────────────────────────┐
│                    MessageQueueClient                     │
│  ┌───────────────────────────────────────────────────┐   │
│  │              WebSocket Transport                   │   │
│  │         (KryoWebSocketEncoder/Decoder)            │   │
│  └───────────────────────────────────────────────────┘   │
│                          │                                │
│  ┌───────────────────────▼───────────────────────────┐   │
│  │      Dispatcher → MessageListener (per type)      │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

## Use Cases

- Real-time notifications and alerts
- Event-driven microservices communication
- Live data streaming (stock prices, sensor data)
- Chat and messaging applications
- Distributed system coordination

## Related Projects

- [nettix](https://github.com/osanha/nettix) - Core networking framework

## License

MIT License

## Author

sanha