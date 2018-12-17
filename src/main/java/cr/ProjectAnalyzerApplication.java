package cr;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import cr.annotation.QueueDefinition;

@QueueDefinition(rpcServer= {"ana"},rpcClient={"db","bui"},encryption="PlainText",queues= {"ana","bui","res"}, excludeListeners= {"bui","res"})
@RefreshScope
@SpringBootApplication
@EnableRabbit
@EnableDiscoveryClient
public class ProjectAnalyzerApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(ProjectAnalyzerApplication.class, args);
	}
	
}

