package ru.shishmakov.server.core;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.authentication.UserCredentials;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.CustomConversions;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.shishmakov.config.AppConfig;
import ru.shishmakov.config.CommonConfig;
import ru.shishmakov.server.dao.PackageMarkerRepository;
import ru.shishmakov.server.entity.Profile;
import ru.shishmakov.server.helper.BinaryConverterToUuid;
import ru.shishmakov.server.helper.UuidToBinaryConverter;
import ru.shishmakov.server.service.PackageMarkerService;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Extension of configuration for Server
 */
@Configuration
@Import(CommonConfig.class)
@ComponentScan(basePackageClasses = {PackageMarkerService.class, PackageMarkerRepository.class})
public class ServerConfig extends AbstractMongoConfiguration {

    @Autowired
    private AppConfig config;

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ResponseSender responseSender() {
        return new ResponseSender();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public RequestProcessor requestProcessor() {
        return new RequestProcessor();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public DatabaseHandler databaseHandler() {
        return new DatabaseHandler();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public HttpRequestDecoder httpRequestDecoder() {
        return new HttpRequestDecoder();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public HttpObjectAggregator httpObjectAggregator() {
        return new HttpObjectAggregator(1048576);
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public HttpResponseEncoder httpResponseEncoder() {
        return new HttpResponseEncoder();
    }

    @Bean(name = "bootGroup", destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup bootGroup() {
        return new NioEventLoopGroup(1);
    }

    @Bean(name = "processGroup", destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup processGroup() {
        return new NioEventLoopGroup();
    }

    @Bean(name = "eventExecutorGroup", destroyMethod = "shutdownGracefully")
    public EventExecutorGroup eventExecutorGroup() {
        final int countThreads = Runtime.getRuntime().availableProcessors() * 2;
        return new DefaultEventExecutorGroup(countThreads);
    }

    @Bean(name = "serverChannelPipelineInitializer")
    public ServerChannelPipelineInitializer channelPipelineInitializer() {
        return new ServerChannelPipelineInitializer() {
            @Override
            public HttpRequestDecoder getHttpRequestDecoder() {
                return httpRequestDecoder();
            }

            @Override
            public HttpObjectAggregator getHttpObjectAggregator() {
                return httpObjectAggregator();
            }

            @Override
            public HttpResponseEncoder getHttpResponseEncoder() {
                return httpResponseEncoder();
            }

            @Override
            public EventExecutorGroup getEventExecutorGroup() {
                return eventExecutorGroup();
            }

            @Override
            public RequestProcessor getRequestProcessor() {
                return requestProcessor();
            }

            @Override
            public DatabaseHandler getDatabaseHandler() {
                return databaseHandler();
            }

            @Override
            public ResponseSender getResponseSender() {
                return responseSender();
            }
        };
    }

    @Bean(name = "server")
    public ServerBootstrap serverBootstrap() {
        final ServerBootstrap server = new ServerBootstrap();
        server.group(bootGroup(), processGroup())
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(channelPipelineInitializer());
        return server;
    }

    /**
     * Register custom {@link Converter} types in a {@link CustomConversions} object
     */
    @Bean
    @Override
    public CustomConversions customConversions() {
        // todo: StringToDBObjectConverter ???
        final List<?> converters = Arrays.asList(BinaryConverterToUuid.INSTANCE,
                UuidToBinaryConverter.INSTANCE);
        return new CustomConversions(converters);
    }

    /**
     * Return the name of the database to connect to.
     */
    @Override
    protected String getDatabaseName() {
        return config.getDatabaseName();
    }

    /**
     * Return the base package to scan classes for mapped {@link Document} annotations.
     */
    @Override
    protected String getMappingBasePackage() {
        final Package mappingPackage = Profile.class.getPackage();
        return mappingPackage == null ? null : mappingPackage.getName();
    }

    /**
     * The MongoClient class is designed to be <u>thread-safe</u> and shared among threads.
     */
    @Bean(destroyMethod = "close")
    public MongoClient mongo() throws UnknownHostException {
        final String host = config.getDatabaseHost();
        final Integer port = config.getDatabasePort();
        final MongoClient mongoClient = new MongoClient(new ServerAddress(host, port));
        mongoClient.setWriteConcern(WriteConcern.SAFE);
        return mongoClient;
    }

    /**
     * Creates a {@link MongoDbFactory} to be used by the {@link MongoTemplate}.
     */
    @Bean
    @Override
    public MongoDbFactory mongoDbFactory() throws Exception {
        final String user = config.getDatabaseUser();
        final String password = config.getDatabasePassword();
        final UserCredentials userCredentials = new UserCredentials(user, password);
        return new SimpleMongoDbFactory(mongo(), getDatabaseName(), userCredentials);
    }

    /**
     * The template offers convenience operations to create, update, delete and query
     * for MongoDB documents and provides a mapping between your domain objects and MongoDB documents.
     * MongoTemplate is <u>thread-safe</u> and can be reused across multiple instances.
     */
    @Bean(name = "serverMongoTemplate")
    @Override
    public MongoTemplate mongoTemplate() throws Exception {
        return new MongoTemplate(mongoDbFactory(), mappingMongoConverter());
    }
}
