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
