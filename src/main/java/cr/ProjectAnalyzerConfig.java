package cr;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@RefreshScope
@Configuration
public class ProjectAnalyzerConfig {

	@Value("${rabbit.bui.queue.name}")
	private String buiQueue;
	@Autowired
	private ConnectionFactory connectionFactory;

//	@Bean
//	public DeclareOk getDeclareOk() throws IOException, TimeoutException {
//		Connection conn =null;
//		Channel channel =null;
//		try {
//			conn= connectionFactory.newConnection();
//			channel= conn.createChannel();
//			return channel.queueDeclarePassive(buiQueue);
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (TimeoutException e) {
//			e.printStackTrace();
//		}finally {
//			channel.close();
//			conn.close();
//		}
//		return null;
//	}

	@Bean//("cr-rabbit-executor")
	public TaskExecutor taskExecutor() {
//		return new ThreadPoolTaskExecutor();
		 ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		 executor.setCorePoolSize(2);
		 executor.setMaxPoolSize(15);
		 executor.setQueueCapacity(30);
		 return executor;
	}
	
	@Scope("singleton")
	@Bean("backlogLock")
	public Object backlogLock() {
		return new Object();
	}

//	@Bean
//	public BacklogAnalyzer backlogAnalyzer() {
//		return new BacklogAnalyzer();
//	}
}
