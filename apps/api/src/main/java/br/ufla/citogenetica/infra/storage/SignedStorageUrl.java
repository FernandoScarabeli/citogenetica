package br.ufla.citogenetica.infra.storage;

import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Instant;

public record SignedStorageUrl(String objectKey, HttpMethod method, URI url, Instant expiresAt) {
}
