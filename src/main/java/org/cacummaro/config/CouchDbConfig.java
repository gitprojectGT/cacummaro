package org.cacummaro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.http.HttpClient;
import org.ektorp.http.StdHttpClient;
import org.ektorp.impl.ObjectMapperFactory;
import org.ektorp.impl.StdCouchDbConnector;
import org.ektorp.impl.StdCouchDbInstance;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cacummaro.couchdb")
public class CouchDbConfig {

    private String host = "localhost";
    private int port = 5984;
    private String database = "cacummaro_docs";
    private String username = "admin";
    private String password = "password";
    private String protocol = "http";

    @Bean
    public HttpClient couchDbHttpClient() {
        return new StdHttpClient.Builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    public ObjectMapperFactory objectMapperFactory() {
        return new ObjectMapperFactory() {
            @Override
            public ObjectMapper createObjectMapper() {
                ObjectMapper om = new ObjectMapper();
                om.findAndRegisterModules(); // This will automatically find and register JSR310 module
                om.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                return om;
            }

            @Override
            public ObjectMapper createObjectMapper(CouchDbConnector db) {
                return createObjectMapper();
            }
        };
    }

    @Bean
    public CouchDbInstance couchDbInstance(HttpClient httpClient, ObjectMapperFactory objectMapperFactory) {
        return new StdCouchDbInstance(httpClient, objectMapperFactory);
    }

    @Bean
    public CouchDbConnector couchDbConnector(CouchDbInstance couchDbInstance) {
        CouchDbConnector connector = new StdCouchDbConnector(database, couchDbInstance);
        connector.createDatabaseIfNotExists();
        return connector;
    }


    // Getters and setters
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}