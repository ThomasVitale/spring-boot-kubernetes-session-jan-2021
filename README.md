# Spring Boot on Kubernetes Session - January 2021

## Building a Spring Boot application

### Bootstrapping

Initialize a new `demo-service` project from [Spring Initializer](https://start.spring.io/) with the following dependencies:

* _Reactive Web (`org.springframework.boot:spring-boot-starter-webflux`)_ contributes libraries for building web applications with Spring Flux and Netty.
* _Actuator (`org.springframework.boot:spring-boot-starter-actuator`)_ contributes endpoints for monitoring and managing your application, including health information, metrics, and configuration.
* _Spring Configuration Processor (`org.springframework.boot:spring-boot-configuration-processor`)_ generates metadata for custom configuration properties.
* _Lombok (`org.projectlombok:lombok`)_ helps reduce boilerplate code like getters, setters, and constructors.

### Defining a custom property

Create a `DemoProperties` class to hold the value for a welcome message.

```java
package com.thomasvitale.demoservice;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
@Data
public class DemoProperties {
	/**
	 * A message to welcome users.
	 */
	private String message;
}
```

In `application.yml`, define a default value for the new `demo.message` property.

```yaml
demo:
  message: "Welcome to Spring Boot!"
```

### Implementing a REST API

Implement a GET REST endpoint using the functional method.

```java
package com.thomasvitale.demoservice;

import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoServiceApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routes(DemoProperties properties) {
		return route()
				.GET("/", request ->
						ServerResponse.ok().body(Mono.just(properties.getMessage()), String.class))
				.build();
	}
}
```

### Running the application on the JVM

First, build the application.

```bash
./gradlew build
```

Then, run the JAR artifact.

```bash
java -jar build/libs/demo-service-0.0.1-SNAPSHOT.jar
```

## Containerizing a Spring Boot application

### Using a layered JAR

When building Docker images, fat-JARs are not the best. Spring provides a layered mode that organizes a JAR into layers.

You can see the list of layers with this command.

```bash
java -Djarmode=layertools -jar build/libs/demo-service-0.0.1-SNAPSHOT.jar list
```

As per the [documentation](https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/htmlsingle/#packaging-layered-jars), the following layers are defined by default:

* `dependencies` for any non-project dependency whose version does not contain SNAPSHOT.
* `spring-boot-loader` for the jar loader classes.
* `snapshot-dependencies` for any non-project dependency whose version contains SNAPSHOT.
* `application` for project dependencies, application classes, and resources.

### Leveraging Cloud Native Buildpacks

Integrated with Spring Boot since version 2.3, Cloud Native Buildpacks can package a Spring Boot application as a Docker image without providing a Dockerfile. It takes care of using a layered JAR, optimizing performance, ensuring reproducibility, and relying on best practices in terms of security.

You can build a Docker image with the default settings:

```bash
./gradlew bootBuildImage
```

You can also add custom settings in `build.gradle`.

```groovy
bootBuildImage {
	imageName = "thomasvitale/${project.name}:${project.version}"
	environment = ["BP_JVM_VERSION" : "11.*"]
}
```

### Publishing a Spring Boot image

You can configure the Gradle/Maven Spring Boot plugin to publish your image to a container registry.

```groovy
bootBuildImage {
	imageName = "thomasvitale/${project.name}:${project.version}"
	environment = ["BP_JVM_VERSION" : "11.*"]
    docker {
		publishRegistry {
			username = project.property("dockerUsername")
			password = project.property("dockerToken")
			url = "https://docker.io"
		}
	}
}
```

Username and token are defined as Gradle properties. You can use the `-publishImage` argument whenever you want to publish the image.

```bash
./gradlew bootBuildImage -publishImage
```

You can test the image by running it as a Docker container, for example using Docker Compose. You can even define a new value for the `demo.message` property.

```yaml
version: "3.8"
services:
  demo-service:
    image: thomasvitale/demo-service:0.0.1-SNAPSHOT
    container_name: demo-service
    ports:
      - 8080:8080
    environment:
      - DEMO_MESSAGE=Welcome to Spring Boot on Docker!
```

## Deploying Spring Boot on Kubernetes

### Starting a local cluster

Start a local Kubernetes cluster with kind.

```bash
kind create cluster
```

### Basic deployment

First, in a `k8s` folder, create a `Deployment` definition for the application.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: book-deployment
  labels:
    app: book
spec:
  replicas: 1
  selector:
    matchLabels:
      app: book
  template:
    metadata:
      labels:
        app: book
    spec:
      containers:
        - name: book-service
          image: thomasvitale/book-service:0.0.1-SNAPSHOT
          ports:
            - containerPort: 8080
```

Then, create a `Service` definition.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: book-service
  labels:
    app: book
spec:
  type: ClusterIP
  selector:
    app: book
  ports:
    - port: 8080
      targetPort: 8080
```

Finally, you can deploy the application on your local Kubernetes cluster.

```bash
kubectl create -f k8s
```

You can inspect the resources created on Kubernetes as follows.

```bash
kubectl get all -l app=demo 
```

The result should be similar to the following.

```bash
NAME                                   READY   STATUS    RESTARTS   AGE
pod/demo-deployment-57d6944794-qc4ms   1/1     Running   0          44s

NAME                   TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
service/demo-service   ClusterIP   10.106.154.110   <none>        8080/TCP   44s

NAME                              READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/demo-deployment   1/1     1            1           44s

NAME                                         DESIRED   CURRENT   READY   AGE
replicaset.apps/demo-deployment-57d6944794   1         1         1       44s
```

The application is now accessible only within the cluster, but you can forward the traffic to your local machine with this command.

```bash
kubectl port-forward service/demo-service 8080:8080
```

### Configuration with ConfigMaps

Since Spring Boot 2.3, you can natively configure your application through ConfigMaps.

Let's define a new value for the `demo.message` property in a `ConfigMap`.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: demo-config
data:
  application.yml: |
    demo:
      message: Welcome to Spring Boot on Kubernetes!
```

Then, we can mount the ConfigMap as a volume to the container.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-deployment
  labels:
    app: demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo
  template:
    metadata:
      labels:
        app: demo
    spec:
      containers:
        - name: demo-service
          image: thomasvitale/demo-service:0.0.1-SNAPSHOT
          ports:
            - containerPort: 8080
          volumeMounts:
            - name: config-volume
              mountPath: /workspace/config
      volumes:
        - name: config-volume
          configMap:
            name: demo-config
```

### Graceful shutdown

You can configure the graceful shutdown for the web server and define a shutdown timeout with these properties.

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 10s
```

You can configure them through the ConfigMap you defined in the previous step.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: demo-config
data:
  application.yml: |
    demo:
      message: Welcome to Spring Boot on Kubernetes!
    server:
      shutdown: graceful
    spring:
      lifecycle:
        timeout-per-shutdown-phase: 20s
```

### Liveness and readiness probes

Spring Boot Actuator exposes liveness and readiness probes automatically when it detects a Kubernetes environment.
So, you can use them directly in your Deployment file.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-deployment
  labels:
    app: demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo
  template:
    metadata:
      labels:
        app: demo
    spec:
      containers:
        - name: demo-service
          image: thomasvitale/demo-service:0.0.1-SNAPSHOT
          ports:
            - containerPort: 8080
          lifecycle:
            preStop:
              exec:
                command: [ "sh", "-c", "sleep 10" ]
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          volumeMounts:
            - name: config-volume
              mountPath: /workspace/config
      volumes:
        - name: config-volume
          configMap:
            name: demo-config
```

### Scaling pods

You can scaling pods by defining a number of replicas in the Deployment file or from kubectl.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-deployment
  labels:
    app: demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: demo
  template:
    metadata:
      labels:
        app: demo
    spec:
      containers:
        - name: demo-service
          image: thomasvitale/demo-service:0.0.1-SNAPSHOT
          ports:
            - containerPort: 8080
          lifecycle:
            preStop:
              exec:
                command: [ "sh", "-c", "sleep 10" ]
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          volumeMounts:
            - name: config-volume
              mountPath: /workspace/config
      volumes:
        - name: config-volume
          configMap:
            name: demo-config
```

## Local development with Kubernetes

Skaffold is a tool that lets you establish a convenient workflow to work with Kubernetes locally.

After installing the tool, create a `skaffold.yml` file in your project.

```yaml
apiVersion: skaffold/v2beta8
kind: Config
metadata:
  name: demo-service
build:
  artifacts:
    - image: thomasvitale/demo-service
      custom:
      buildpacks:
        builder: gcr.io/paketo-buildpacks/builder:base-platform-api-0.3
        env:
          - BP_JVM_VERSION=11.*
        dependencies:
          paths:
            - src
            - build.gradle
deploy:
  kubectl:
    manifests:
      - k8s/*
```

Run the following command and Skaffold will monitor changes in your code, builds an image, and deploys it to your local Kubernetes cluster.

```bash
skaffold dev --port-forward
```

If you need to debug the application, then Skaffold can expose a remote debug port for you.

```bash
skaffold debug --port-forward
```

## Service discovery and load balancing

### Build a client Spring Boot application

Initialize a new `demo-client` project from [Spring Initializer](https://start.spring.io/) with the following dependencies:

* _Reactive Web (`org.springframework.boot:spring-boot-starter-webflux`)_ contributes libraries for building web applications with Spring Flux and Netty.
* _Actuator (`org.springframework.boot:spring-boot-starter-actuator`)_ contributes endpoints for monitoring and managing your application, including health information, metrics, and configuration.

### Implementing a REST API

First, we define a property for the service URL.

```java
package com.thomasvitale.democlient;

import java.net.URI;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("demo")
@Data
public class DemoProperties {
	/**
	 * The URL of the demo service.
	 */
	private URI serviceUrl; 
}
```

Then, we define a value to use locally.

```yaml
demo:
  serviceUrl: http://localhost:8080

server:
  port: 8181
```

And finally the endpoint, which calls the demo service.

```java
package com.thomasvitale.democlient;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoClientApplication.class, args);
	}
	
	@Autowired
	private DemoProperties demoProperties;

	private final WebClient webClient = WebClient.create();

	@Bean
	RouterFunction<ServerResponse> routes() {
		return route()
				.GET("/", this::getMessage)
				.build();
	}

	public Mono<ServerResponse> getMessage(ServerRequest request) {
		Mono<String> finalMessage =  webClient.get()
				.uri(demoProperties.getServiceUrl())
				.retrieve()
				.bodyToMono(String.class)
				.map(message -> "The service says: " + message);

		return ServerResponse.ok().body(finalMessage, String.class);
	}
}
```
