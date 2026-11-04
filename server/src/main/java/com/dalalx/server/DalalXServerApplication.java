package com.dalalx.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the DalalX Spring Boot server.
 *
 * <p>The server is an <em>adapter</em> (Ports &amp; Adapters / Hexagonal style):
 * it translates HTTP and WebSocket traffic into calls on the framework-free
 * {@code core} module and never contains exchange logic itself. Keeping Spring
 * out of the core means the engine can be unit-tested and reasoned about as
 * plain Java.
 */
@SpringBootApplication
public class DalalXServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DalalXServerApplication.class, args);
    }
}
