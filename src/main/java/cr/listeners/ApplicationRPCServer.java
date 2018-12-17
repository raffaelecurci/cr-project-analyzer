package cr.listeners;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.generated.interf.ProcessRPCResponse;
import cr.interf.EncryptedMessage;
import cr.service.ProjectAnalyzer;


@Configuration
public class ApplicationRPCServer {
	@Autowired
	private ProjectAnalyzer pa;
	@Bean
	public ProcessRPCResponse ProcessResponse() {
		return new ProcessRPCResponse() {
			@Override
			public EncryptedMessage processAnaResponseRPC(EncryptedMessage message) {
				return pa.action(message);
			}
		};
	}
}
