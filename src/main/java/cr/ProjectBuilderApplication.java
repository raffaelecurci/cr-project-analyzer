package cr;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import cr.annotation.QueueDefinition;

@QueueDefinition(rpcClient={"db"},encryption="PlainText",queues= {"res"},excludeListeners= {"res"})
@RefreshScope
@SpringBootApplication
@EnableRabbit
@EnableDiscoveryClient
public class ProjectBuilderApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(ProjectBuilderApplication.class, args);
	}

}

