package cr.listeners;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.generated.interf.Listener;
import cr.generated.ops.MessageListener;
import cr.interf.EncryptedMessage;
import cr.service.ProjectAnalyzer;

@Configuration
public class ApplicationListener {
	@Autowired
	private ProjectAnalyzer prja;
	@Bean
	public Listener listener() {
		return new Listener() {
			@Override
			public void processAna(EncryptedMessage message) {
				prja.action(message);
			}
		};
	}
//	@Bean
//	public MessageListener messageListener() {
//		return new MessageListener();
//	}
}
