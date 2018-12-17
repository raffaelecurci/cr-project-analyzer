package cr.listeners.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class BacklogAnalyzerConf {
	@Autowired
	private BacklogAnalyzer backlogAnalyzer;

	@Scope("singleton")
	@Bean("backLogProcess")
	public Object backLogProcess() {
		backlogAnalyzer.processBackLog();
		return new Object();
	}
	
	
}
