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
