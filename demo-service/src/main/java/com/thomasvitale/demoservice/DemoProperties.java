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
