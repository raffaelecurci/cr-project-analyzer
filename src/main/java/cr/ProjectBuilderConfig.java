package cr;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.service.BuilderManager;

@RefreshScope
@Configuration
public class ProjectBuilderConfig {
	@Autowired
	private BuilderManager buildermanager;
	

	@Value("${projects.storage}")
	private String storageFolder;
	
	@Bean
	public Object exec() {
		buildermanager.checkBuildVerifyScan();
		return new Object();
	}

	public String getStorageFolder() {
		return storageFolder;
	}
}
