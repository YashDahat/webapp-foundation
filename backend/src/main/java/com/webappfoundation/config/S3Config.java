package com.webappfoundation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * S3 client for object storage. Points at the self-hosted MinIO service by default (path-style access,
 * endpoint override). To use real AWS S3 instead, leave {@code s3.endpoint} blank and set
 * {@code s3.path-style=false} with real credentials/region — no code change.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${s3.endpoint:}") String endpoint,
            @Value("${s3.region:us-east-1}") String region,
            @Value("${s3.access-key:}") String accessKey,
            @Value("${s3.secret-key:}") String secretKey,
            @Value("${s3.path-style:true}") boolean pathStyle) {

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());

        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));   // MinIO; blank => real AWS S3
        }
        return builder.build();
    }
}
