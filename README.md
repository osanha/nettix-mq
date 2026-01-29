# nettix-mq

A brokerless, high-performance message queue for Java — built on [Nettix](https://github.com/osanha/nettix).

## Overview

**nettix-mq** is a lightweight pub/sub messaging library that enables direct publisher-to-subscriber communication without intermediary broker servers.
Designed for distributed systems that need to synchronize state (sessions, transactions, etc.) across nodes without a central coordination point.                                                                                  

Ideal when traditional brokers add unnecessary overhead — just fast, reliable messaging between Java services.

### Why Brokerless?                                                                                                                                                                              
                                                                                                                                                                                                 
```                                                                                                                                                                                              
[Traditional MQ]                    [nettix-mq (Brokerless)]

Publisher → Broker → Subscribers    Publisher ←→ Subscribers
              ↑                            ↖   ↗
         Single point of             Direct WebSocket
         failure & latency           connections
```                                                                                                                                                                                              
                                                                                                                                                                                                 
- **Zero infrastructure** — no broker to deploy, configure, or maintain                                                                                                                          
- **Lower latency** — messages go directly from publisher to subscriber                                                                                                                          
- **Simpler topology** — fewer moving parts, easier to reason about                                                                                                                              
- **Web-friendly** — WebSocket transport works through firewalls and proxies    

## Features

- **Brokerless Architecture**: Direct peer-to-peer messaging, no intermediary servers                                                                                                            
- **WebSocket Transport**: Reliable, bidirectional communication that works everywhere
- **Kryo Serialization**: Fast, compact binary serialization with object pooling
- **Messaging Patterns**: One-way (fire-and-forget) and two-way (request-response)
- **Unicast & Broadcast**: Send message to a specific subscriber or all connected subscribers
- **Type-safe API**: Enum-based message types for compile-time safety
- **Resilient Connections**: Built-in heartbeat, reconnection and timeout handling
 
## Requirements

- Java 6+
- nettix 3.1.1+
- [Kryo](https://github.com/EsotericSoftware/kryo) - Fast binary serialization
- [eraasoft objectpool](https://mvnrepository.com/artifact/nf.fr.eraasoft/objectpool) - Kryo instance pooling

## Installation

Available on
[![](https://jitpack.io/v/osanha/nettix-mq.svg)](https://jitpack.io/#osanha/nettix-mq)

> **Note:**
>  When distributed via JitPack, the groupId is resolved as com.github.osanha based on the GitHub repository owner.
>
> Future releases will be available on Maven Central with coordinates `io.nettix:nettix-mq`.

### Maven

Add the dependency:

```xml
<dependency>
    <groupId>com.github.osanha</groupId>
    <artifactId>nettix-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```


Add the JitPack repository to your `pom.xml`:

```xml
<repositories>
    <!--Prioritize Central repository for faster dependency resolution-->
    <repository>
        <id>central</id>
        <url>https://repo.maven.apache.org/maven2</url>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### Gradle

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.osanha:nettix-mq:1.0.0")
}
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
/**
 * Java primitive types are registered by default.
 */
public class MyKryoFactory extends PoolableKryoFactory {
    @Override
    protected void initialize(Kryo kryo) {
        kryo.register(UserLoginEvent.class);
        kryo.register(DataUpdateEvent.class);
        kryo.register(NotificationEvent.class);
    }
}
```

### Publisher

```java
MessagePublisher<EventType> publisher = new MessagePublisher<EventType>(
    "EventPublisher",
    "/mq",
    new MyKryoFactory(),
    9000
);

publisher.start();

// Broadcast to all subscribers (fire-and-forget)
publisher.publish(EventType.NOTIFICATION, new NotificationEvent("System update"));

// Broadcast to all subscribers (request-response)
publisher.request(EventType.DATA_UPDATE, data).addListener(new ChannelGroupFutureListener() {
    @Override
    public void operationComplete(ChannelGroupFuture futures) throws Exception {
        for (ChannelFuture f : futures) {
            if (f.isSuccess()) {
                Response r = (Response) ((CallableChannelFuture<Object>) f).get();
                ...
            }
        }
    }
});

// Unicast to specific subscriber (fire-and-forget)
publisher.publish(channel, EventType.USER_LOGIN, loginEvent);

// Unicast to specific subscriber (request-response)
CallableChannelFuture<Object> future = publisher.request(channel, EventType.DATA_UPDATE, data);
future.addListener(new CallableChannelFutureListener<Object>() {
    @Override
    public void operationComplete(CallableChannelFuture<Object> f) throws Exception {
        if (f.isSuccess()) {
            Response r = (Response) f.get();
            ...
        }
    }
});
```

### Subscriber

```java
MessageSubscriber<EventType> subscriber = new MessageSubscriber<EventType>(
    "EventSubscriber",
    "/mq",
    new MyKryoFactory()
);

// Register message listeners
subscriber.addListener(EventType.NOTIFICATION, new MessageListener<NotificationEvent, Void>() {
    @Override
    public Void messageReceived(Channel ch, NotificationEvent event) {
        System.out.println("Received: " + event.getMessage());
        return null;  // No response for fire-and-forget messages
    }
});

subscriber.addListener(EventType.DATA_UPDATE, new MessageListener<DataUpdateEvent, Boolean>() {
    @Override
    public Boolean messageReceived(Channel ch, DataUpdateEvent event) {
        processUpdate(event);
        return true;  // Response for request-response messages
    }
});

subscriber.start();
subscriber.connect("localhost", 9000);
subscriber.connect("localhost", 9001);
```

## Messaging Patterns

### Broadcast vs Unicast

nettix-mq supports two delivery patterns:

| Pattern | Method                          | Description                       |
|---------|---------------------------------|-----------------------------------|
| **Broadcast** | `publish(type, value)`          | Send to all connected subscribers |
| **Unicast** | `publish(channel, type, value)` | Send to a specific subscriber    |

#### Broadcast Example

```java
// Notify all connected subscribers about a system event
publisher.publish(EventType.SYSTEM_ALERT, new Alert("Server maintenance in 10 minutes"));

// Broadcast with acknowledgement from all subscribers
ChannelGroupFuture future = publisher.request(EventType.CONFIG_UPDATE, newConfig);
future.addListener(new ChannelGroupFutureListener() {
    @Override
    public void operationComplete(ChannelGroupFuture f) {
        if (f.isCompleteSuccess()) {
            System.out.println("All subscribers acknowledged");
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
   │────── publish(msg) ─────>│             │             │
   │───────────────────────────────────────>│             │
   │─────────────────────────────────────────────────────>│
   │                          │             │             │
```

#### Unicast Example

```java
// Send a private message to a specific subscriber
Channel targetClient = getClientByUserId("user123");
publisher.publish(targetClient, EventType.PRIVATE_MSG, message);

// request-response with a specific subscriber
CallableChannelFuture<UserInfo> future = publisher.request(
    targetClient,
    EventType.GET_USER_INFO,
    new UserInfoRequest("user123")
);

future.addListener(new CallableChannelFutureListener<Object>() {
    @Override
    public void operationComplete(CallableChannelFuture<Object> f) throws Exception {
        if (f.isSuccess()) {
            UserInfo info = (UserInfo) f.get();
        }
    }
});

```

```
Publisher               Subscriber A   Subscriber B   Subscriber C
   │                          │             │             │
   │──── publish(chB, msg) ────────────────>│             │
   │                          │             │  (only B)   │
   │                          │             │             │
```

### one-way vs two-way

| Mode        | Method      | Return Type | Use Case |
|-------------|-------------|-------------|----------|
| **one-way** | `publish()` | `ChannelFuture` | Fire-and-forget, notifications |
| **two-way** | `request()` | `CallableChannelFuture<T>` | Request-response, confirmations |

#### One-way Flow (Fire-and-Forget)

```
Publisher                      Subscriber
   │                               │
   │───── publish(type, msg) ─────>│
   │                               │ messageReceived()
   │     (no response expected)    │ returns null
   │                               │
```

#### Two-way Flow (Request-Response)

```
Publisher                      Subscriber
   │                               │
   │───── request(type, msg) ─────>│
   │                               │ messageReceived()
   │<──────── response ────────────│ returns value
   │                               │
   │ future.get() returns response │
```

## Configuration

### Publisher Options

```java
MessagePublisher<EventType> publisher = new MessagePublisher<EventType>(
    "MyMQ",
    "/mq", 
    kryoFactory,
    9000, 
    60,   // enquire link interval (seconds)
    10    // response timeout (seconds)
);

publisher.setConnectionHandler(new ConnectStateEventHandler() {
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

### Subscriber Options

```java
MessageSubscriber<EventType> client = new MessageSubscriber<EventType>(
    "MyClient",
    "/mq",
    kryoFactory,
    60,   // enquire link interval (seconds)
    10    // response timeout (seconds)
);

client.setConnectionHandler(handler);
```

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    MessagePublisher                      │
│  ┌─────────────────────────────────────────────────────┐ │
│  │     publish() / request() - Unicast & Broadcast     │ │
│  └─────────────────────────────────────────────────────┘ │
│                          │                               │
│  ┌───────────────────────▼───────────────────────────┐   │
│  │              WebSocket Transport                  │   │
│  │         (KryoWebSocketEncoder/Decoder)            │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
                           │
                    WebSocket Connection
                           │
┌──────────────────────────────────────────────────────────┐
│                    MessageSubscriber                     │
│  ┌───────────────────────────────────────────────────┐   │
│  │              WebSocket Transport                  │   │
│  │         (KryoWebSocketEncoder/Decoder)            │   │
│  └───────────────────────────────────────────────────┘   │
│                          │                               │
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
- Distributed system coordination & synchronization

## Related Projects

- [nettix](https://github.com/osanha/nettix) - Core networking framework

## License

MIT License

## Author

Sanha