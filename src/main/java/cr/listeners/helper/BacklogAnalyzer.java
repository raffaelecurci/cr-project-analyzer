package cr.listeners.helper;

import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;

import cr.ProjectAnalyzerApplication;
import cr.generated.config.ApplicationConfigReader;
import cr.generated.ops.MessageSender;
import cr.generated.ops.service.RPCClient;
import cr.shared.BuildRecord;
import cr.shared.BuildRecordList;
import cr.shared.Operation;
import cr.shared.Project;
import cr.shared.SecurityProject;
import cr.shared.SecurityProjectList;

@EnableAsync
@Service
public class BacklogAnalyzer {
	private static final Logger log = LoggerFactory.getLogger(BacklogAnalyzer.class);
	private static String encryption = ProjectAnalyzerApplication.class
			.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
//	@Autowired
//	private DeclareOk buiCounter;
	@Autowired
	private RPCClient client;
	@Autowired
	private ApplicationConfigReader applicationConfigReader;
	@Autowired
	private MessageSender messageSender;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	private RabbitListenerEndpointRegistry listenersreg;

	@Qualifier("backlogLock")
	@Autowired
	private Object backlogLock;

	@Async
	public void processBackLog() {
		log.info(" [Backlog BacklogAnalyzer Server] awaiting for requests from JenkinsController");
		while (true)
			process();
	}
	private void process() {
		synchronized (backlogLock) {
			try {
				log.info("\n\n waiting notify for backlog check\n\n");
				backlogLock.wait();
				System.out.println("\n\n Received Notify \n\n");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		//SHOULD I STOP DEQUEUE BUILD
//		log.info("\n\nnotified\n\n"+buiCounter.getMessageCount()+"\n\n\n");
//		if (buiCounter.getMessageCount() == 0) {
//			Operation nop = new Operation("START_DEQUEUE_BUILDS");
//			client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
//		} else {
//
//		}
		
		BuildRecord br = new BuildRecord();
		br.setId(-1L);
		br.setIdCommit(-1L);
		br.setStatus("FIND_BACKLOG");
		BuildRecordList builds = (BuildRecordList) (client
				.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject();
		List<BuildRecord> brl = builds.getBuilds();
		int i = 0;
		SecurityProject sp = new SecurityProject();
		sp.setLanguage("%");
		List<SecurityProject> spList = ((SecurityProjectList) client
				.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject())
						.getProject();

		while (i < brl.size() && spList.size() > 0) {
			br = brl.get(i);
			log.info("Going to search Project for "+br.toString());
			log.info("Project id "+br.getIdRepository());
			Project p = new Project(br.getIdRepository(), null, null, null, null,null);
			final Project pr = ((Project) client.sendAndReceiveDb(p.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject());
			Optional<SecurityProject> osp = spList.stream()
					.filter(s -> s.getLanguage().equals(pr.getLanguage()) && s.isAvailable()).findAny();
			if (osp.isPresent()) {
				sp = osp.get();
				log.info("SecurityProgect id "+sp.getId());
				int pos = spList.indexOf(sp);
				sp.setAvailable(false);
				client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
				br.setStatus("BUILDSTARTING");
				br.setIdSecurityProject(sp.getId());
				br = (BuildRecord) (client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64()))
						.decodeBase64ToObject();
				if (pos >= 0) {
					spList.remove(pos);
					System.out.println("+++ [Backlog BacklogAnalyzer] marked " + sp.getUrl() + " as unavailable");
				}
				messageSender.sendMessage(rabbitTemplate, applicationConfigReader.getBuiExchange(),
						applicationConfigReader.getBuiRoutingKey(), br.toEncryptedMessage(encryption).encodeBase64());
			}
			i++;
		}
//		br.setId(-1L);
		br.setStatus("FIND_BACKLOG");
		builds = (BuildRecordList) (client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64()))
				.decodeBase64ToObject();
		brl = builds.getBuilds();
		log.info("Builds in queue: "+brl.size()); 
		if (brl.size() == 0) {
//			listenersreg.getListenerContainer("ana").start();
//			log.info("ANALYZER DEQUEING STATUS REACTIVATED by " + this.getClass().getSimpleName() + "NOT RUNNING");
			Operation nop = new Operation("START_DEQUEUE_ANALYSIS");
			Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			log.info("START_DEQUEUE_ANALYSIS");
		}else {
			sp = new SecurityProject();
			sp.setLanguage("%");
			spList = ((SecurityProjectList) client
					.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject())
							.getProject();
			
			Stream<SecurityProject> targetStream = StreamSupport.stream(
			          Spliterators.spliteratorUnknownSize(spList.iterator(), Spliterator.ORDERED),
			          false);
			
			if(targetStream.allMatch(s->!s.isAvailable())) {
				Operation nop = new Operation("STOP_DEQUEUE_ANALYSIS");
				try {
					Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
					log.info("STOP_DEQUEUE_ANALYSIS");
				}catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			
		}
	}
}
