package cr.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import cr.ProjectBuilderApplication;
import cr.generated.config.ApplicationConfigReader;
import cr.generated.ops.MessageSender;
import cr.generated.ops.service.RPCClient;
import cr.interf.EncryptedMessage;
import cr.shared.JenkinsBuildInfo;

@Controller
public class JenkinsController {
	private static final Logger log = LoggerFactory.getLogger(JenkinsController.class);
	private static String encryption=ProjectBuilderApplication.class.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
	
	@Autowired
	private RPCClient client;
	@Autowired
	private MessageSender msgSender;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	private ApplicationConfigReader applicationConfigReader;
	
	

	@RequestMapping(path = "/build", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_MARKDOWN_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public void beforeBuild(@RequestBody String requestBody) {
		log.info("Received request: " + requestBody);
		JenkinsBuildInfo prebuild=null;
		try {
		    ObjectMapper mapper = new ObjectMapper();
		    prebuild = mapper.readValue(requestBody, JenkinsBuildInfo.class);
		    EncryptedMessage enc=prebuild.toEncryptedMessage(encryption).encodeBase64();
		    client.sendAndReceiveDb(enc);
		    if(prebuild.getVeracodeScan()!=null)
		    	msgSender.sendMessage(rabbitTemplate, applicationConfigReader.getResExchange(), applicationConfigReader.getResRoutingKey(), enc);
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}
}
