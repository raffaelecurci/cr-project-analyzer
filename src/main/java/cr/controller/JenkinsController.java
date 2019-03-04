package cr.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import cr.ProjectAnalyzerApplication;
import cr.generated.config.ApplicationConfigReader;
import cr.generated.ops.MessageSender;
import cr.generated.ops.service.RPCClient;
import cr.interf.EncryptedMessage;
import cr.shared.BuildRecord;
import cr.shared.JenkinsBuildInfo;
import cr.shared.JenkinsJob;
import cr.shared.SecurityProject;
import cr.util.JenkinsAdapter;

@Controller
public class JenkinsController {
	private static final Logger log = LoggerFactory.getLogger(JenkinsController.class);
	private static String encryption=ProjectAnalyzerApplication.class.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
	@Qualifier("backlogLock")
	@Autowired
	private Object backlogLock;
	@Autowired
	private RPCClient client;
	@Autowired
	private MessageSender msgSender;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	private ApplicationConfigReader applicationConfigReader;
	@Autowired
	private RabbitListenerEndpointRegistry listenersreg;
	
	

	@RequestMapping(path = "/", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_MARKDOWN_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public void beforeBuild(@RequestBody String requestBody) {
		log.info("Received request: " + requestBody);
		JenkinsBuildInfo buildinfo=null;
		try {
		    ObjectMapper mapper = new ObjectMapper();
		    buildinfo = mapper.readValue(requestBody, JenkinsBuildInfo.class);
		    
		    EncryptedMessage enc=buildinfo.toEncryptedMessage(encryption).encodeBase64();
		    BuildRecord br=client.sendAndReceiveDb(enc).decodeBase64ToObject();
		    if(br.getIdSecurityProject()!=null) {
		    	JenkinsJob jj=new JenkinsJob();
			    jj.setIdSecurityRepository(br.getIdSecurityProject());
			    log.info("RQuesting: " + jj.toString());
			    jj=client.sendAndReceiveDb(jj.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			    JenkinsAdapter ja=new JenkinsAdapter(jj.getUser(), jj.getToken(), jj.getUrl());
			    System.out.println("\n\nis building:"+ja.isBuilding(jj.getJob(), br.getIdJenkinsBuild()).booleanValue()+"\n\n");
			    //if(!ja.isBuilding(jj.getJob(), br.getIdJenkinsBuild()).booleanValue()) {
			    if(buildinfo.getStatus().equals("SUCCESS")||buildinfo.getStatus().equals("FAILURE")) {
			    	SecurityProject sp=new SecurityProject();
					sp.setId(br.getIdSecurityProject());
					sp.setAvailable(true);
					client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
					
					if(buildinfo.getVeracodeScan()!=null) {
				    	msgSender.sendMessage(rabbitTemplate, applicationConfigReader.getResExchange(), applicationConfigReader.getResRoutingKey(), enc);
				    }
					notifyBackLog();
			    }
		    }else {
//		    	notifyBackLog();
		    }
		    
		    
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}
	private void notifyBackLog() {
//		System.out.println(!listenersreg.getListenerContainer("ana").isRunning() +" backlogLock:"+backlogLock);
//	    if(!listenersreg.getListenerContainer("ana").isRunning()) {
	    	synchronized (backlogLock) {
	    		log.info("going to call notify");
	    		backlogLock.notifyAll();
			}
	    	
	    	
//	    }
	}
}
