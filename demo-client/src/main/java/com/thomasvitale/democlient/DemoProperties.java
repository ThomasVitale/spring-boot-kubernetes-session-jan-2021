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
