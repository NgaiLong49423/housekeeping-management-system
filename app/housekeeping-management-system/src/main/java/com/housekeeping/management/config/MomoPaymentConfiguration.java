package com.housekeeping.management.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MomoPaymentProperties.class)
public class MomoPaymentConfiguration { }
