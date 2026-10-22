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

import com.sun.net.httpserver.HttpServer;
import org.exist.test.SilentServer;
import org.exist.util.Configuration;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * See <a href="https://github.com/eXist-db/exist/issues/6902">#6902</a>.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class UrlConnectionsTest {

    @RegisterExtension
    final SilentServer silentServer = new SilentServer();

    private static HttpServer testHttpServer;
    private static String testServerBaseUrl;

    @BeforeAll
    static void startTestHttpServer() throws IOException {
        testHttpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        testHttpServer.createContext("/ok", exchange -> {
            final byte[] response = "Hello World".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Last-Modified", "Wed, 21 Oct 2026 07:28:00 GMT");
            exchange.sendResponseHeaders(200, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        testHttpServer.createContext("/error", exchange -> {
            final byte[] response = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        testHttpServer.start();
        testServerBaseUrl = "http://127.0.0.1:" + testHttpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopTestHttpServer() {
        if (testHttpServer != null) {
            testHttpServer.stop(0);
        }
    }

    @Test
    void clientAppliesConnectTimeout() {
        final HttpClient client = UrlConnections.getClient(new UrlConnections.Timeouts(1234, 567));
        assertEquals(Optional.of(Duration.ofMillis(1234)), client.connectTimeout());
        assertEquals(HttpClient.Redirect.NORMAL, client.followRedirects());
    }

    @Test
    void requestBuilderAppliesReadTimeout() {
        final HttpRequest request = UrlConnections.newRequestBuilder(
                URI.create("http://localhost/test"), new UrlConnections.Timeouts(1234, 567)).build();
        assertEquals(Optional.of(Duration.ofMillis(567)), request.timeout());
    }

    @Test
    void openStreamFromSilentHostTimesOut() {
        assertThrows(HttpTimeoutException.class, () -> {
            try (final InputStream is = UrlConnections.openStream(
                    URI.create(silentServer.url("/x")).toURL(), new UrlConnections.Timeouts(1000, 300))) {
                is.read();
            }
        });
    }

    @Test
    void getAndOpenStreamSuccess() throws Exception {
        final URI uri = URI.create(testServerBaseUrl + "/ok");
        final HttpResponse<InputStream> response = UrlConnections.get(uri, UrlConnections.Timeouts.DEFAULT);
        assertEquals(200, response.statusCode());

        final long lastModified = UrlConnections.getLastModified(response);
        assertTrue(lastModified > 0);
        assertEquals(Instant.parse("2026-10-21T07:28:00Z").toEpochMilli(), lastModified);

        try (final InputStream is = response.body()) {
            final byte[] bytes = is.readAllBytes();
            assertArrayEquals("Hello World".getBytes(StandardCharsets.UTF_8), bytes);
        }

        try (final InputStream is = UrlConnections.openStream(uri, UrlConnections.Timeouts.DEFAULT)) {
            final byte[] bytes = is.readAllBytes();
            assertArrayEquals("Hello World".getBytes(StandardCharsets.UTF_8), bytes);
        }
    }

    @Test
    void openStreamNotFoundThrowsFileNotFoundException() {
        final URI uri = URI.create(testServerBaseUrl + "/nonexistent");
        assertThrows(FileNotFoundException.class, () -> UrlConnections.openStream(uri, UrlConnections.Timeouts.DEFAULT));
    }

    @Test
    void openStreamServerErrorThrowsIOException() {
        final URI uri = URI.create(testServerBaseUrl + "/error");
        assertThrows(IOException.class, () -> UrlConnections.openStream(uri, UrlConnections.Timeouts.DEFAULT));
    }

    @Test
    void timeoutsFromConfiguration() throws Exception {
        assertEquals(UrlConnections.Timeouts.DEFAULT, UrlConnections.Timeouts.from(null));

        final Configuration configuration = new Configuration();
        configuration.setProperty(XQueryContext.PROPERTY_URL_CONNECT_TIMEOUT, 1500);
        configuration.setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, 2500);
        assertEquals(new UrlConnections.Timeouts(1500, 2500), UrlConnections.Timeouts.from(configuration));
    }
}
