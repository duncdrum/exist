/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xquery.XQueryContext;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Opens HTTP connections with bounded connect and read timeouts using {@link HttpClient},
 * so that a slow or silent remote host cannot block the calling thread, which usually holds a broker, indefinitely.
 * See <a href="https://github.com/eXist-db/exist/issues/6898">#6898</a> and
 * <a href="https://github.com/eXist-db/exist/issues/6902">#6902</a>.
 */
public final class UrlConnections {

    private static final Logger LOG = LogManager.getLogger(UrlConnections.class);

    /**
     * Default connect timeout in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 10_000;
    /**
     * Default read timeout in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT = 30_000;

    private static final ConcurrentMap<Integer, HttpClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    private UrlConnections() {
    }

    /**
     * Connect and read timeouts in milliseconds, 0 means no timeout. The read timeout is the
     * longest wait for data between two reads, not a limit on the whole transfer.
     *
     * @param connect the connect timeout
     * @param read the read timeout
     */
    public record Timeouts(int connect, int read) {

        public static final Timeouts DEFAULT = new Timeouts(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);

        /**
         * The timeouts configured by the {@code url-connect-timeout} and {@code url-read-timeout}
         * attributes of {@code <xquery>} in conf.xml.
         *
         * @param config the database configuration, or null for the defaults
         *
         * @return the configured timeouts
         */
        public static Timeouts from(@Nullable final Configuration config) {
            if (config == null) {
                return DEFAULT;
            }
            return new Timeouts(
                    config.getProperty(XQueryContext.PROPERTY_URL_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT),
                    config.getProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, DEFAULT_READ_TIMEOUT));
        }

        /**
         * The timeouts of the running database, for code that has no broker or configuration at hand.
         *
         * @return the configured timeouts, or the defaults when no database is running
         */
        public static Timeouts current() {
            if (BrokerPool.isConfigured()) {
                try {
                    return from(BrokerPool.getInstance().getConfiguration());
                } catch (final EXistException e) {
                    LOG.debug("No database instance available, using the default URL timeouts: {}", e.getMessage());
                }
            }
            return DEFAULT;
        }
    }

    /**
     * Gets a cached {@link HttpClient} configured with the specified connect timeout in milliseconds
     * and normal redirect following policy.
     *
     * @param connectTimeoutMs the connect timeout in milliseconds, or 0 for no timeout
     *
     * @return the HTTP client instance
     */
    public static HttpClient getClient(final int connectTimeoutMs) {
        return CLIENT_CACHE.computeIfAbsent(connectTimeoutMs, timeout -> {
            final HttpClient.Builder builder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL);
            if (timeout > 0) {
                builder.connectTimeout(Duration.ofMillis(timeout));
            }
            return builder.build();
        });
    }

    /**
     * Gets a cached {@link HttpClient} configured with the connect timeout specified in {@link Timeouts}.
     *
     * @param timeouts the timeouts containing the connect timeout to apply
     *
     * @return the HTTP client instance
     */
    public static HttpClient getClient(final Timeouts timeouts) {
        return getClient(timeouts.connect());
    }

    /**
     * Creates a new {@link HttpRequest.Builder} for a GET request to the given URI, configured with
     * the read timeout from {@link Timeouts}.
     *
     * @param uri the URI of the target resource
     * @param timeouts the timeouts containing the read timeout to apply
     *
     * @return the configured request builder
     */
    public static HttpRequest.Builder newRequestBuilder(final URI uri, final Timeouts timeouts) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
        if (timeouts.read() > 0) {
            builder.timeout(Duration.ofMillis(timeouts.read()));
        }
        return builder;
    }

    /**
     * Performs an HTTP GET request to the specified URI with the given timeouts applied.
     *
     * @param uri the URI to send the GET request to
     * @param timeouts the connect and read timeouts to apply
     *
     * @return the HTTP response with the body as an {@link InputStream}
     *
     * @throws IOException if an I/O error occurs or the request is interrupted
     */
    public static HttpResponse<InputStream> get(final URI uri, final Timeouts timeouts) throws IOException {
        final HttpClient client = getClient(timeouts);
        final HttpRequest request = newRequestBuilder(uri, timeouts).build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request to " + uri + " was interrupted", e);
        }
    }

    /**
     * Performs an HTTP GET request to the specified URL with the given timeouts applied.
     *
     * @param url the URL to send the GET request to
     * @param timeouts the connect and read timeouts to apply
     *
     * @return the HTTP response with the body as an {@link InputStream}
     *
     * @throws IOException if an I/O error occurs or the request is interrupted
     */
    public static HttpResponse<InputStream> get(final URL url, final Timeouts timeouts) throws IOException {
        return get(toUri(url), timeouts);
    }

    /**
     * Opens an input stream to read the resource at the specified URI with timeouts applied,
     * verifying a successful response status code.
     *
     * @param uri the URI to read from
     * @param timeouts the connect and read timeouts to apply
     *
     * @return the response body input stream
     *
     * @throws FileNotFoundException if the server returns a 404 Not Found response
     * @throws IOException if the server returns an HTTP error status code (400 or greater) or an I/O error occurs
     */
    public static InputStream openStream(final URI uri, final Timeouts timeouts) throws IOException {
        final HttpResponse<InputStream> response = get(uri, timeouts);
        final int statusCode = response.statusCode();
        if (statusCode == 404) {
            throw new FileNotFoundException("HTTP request to " + uri + " returned 404 Not Found");
        }
        if (statusCode >= 400) {
            throw new IOException("HTTP request to " + uri + " returned status code " + statusCode);
        }
        return response.body();
    }

    /**
     * Opens an input stream to read the resource at the specified URL with timeouts applied,
     * in place of {@link URL#openStream()}.
     *
     * @param url the URL to read from
     * @param timeouts the connect and read timeouts to apply
     *
     * @return the response body input stream
     *
     * @throws FileNotFoundException if the server returns a 404 Not Found response
     * @throws IOException if the server returns an HTTP error status code (400 or greater) or an I/O error occurs
     */
    public static InputStream openStream(final URL url, final Timeouts timeouts) throws IOException {
        return openStream(toUri(url), timeouts);
    }

    /**
     * Extracts the {@code Last-Modified} timestamp in milliseconds since epoch from the HTTP response headers.
     *
     * @param response the HTTP response to inspect
     *
     * @return the last modified timestamp in milliseconds since epoch, or 0 if absent or unparseable
     */
    public static long getLastModified(final HttpResponse<?> response) {
        return response.headers().firstValue("Last-Modified")
                .map(value -> {
                    try {
                        return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                    } catch (final DateTimeParseException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private static URI toUri(final URL url) throws IOException {
        try {
            return url.toURI();
        } catch (final URISyntaxException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
    }
}
