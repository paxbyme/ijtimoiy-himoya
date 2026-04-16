package com.manager.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                // No limit on multipart/form-post body size (property -1 can mis-parse)
                protocol.setMaxPostSize(-1);
                // Allow Tomcat to fully discard any remaining request body before closing
                // the connection. Without this, bodies >2MB trigger a TCP RST while the
                // client is still writing — Android sees "Broken pipe (errno=32)".
                protocol.setMaxSwallowSize(-1);
            }
        });
    }
}
