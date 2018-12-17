package cr.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;

import cr.ProjectAnalyzerApplication;
import cr.generated.config.ApplicationConfigReader;
import cr.generated.ops.MessageSender;
import cr.generated.ops.service.RPCClient;
import cr.interf.EncryptedMessage;
import cr.shared.BuildRecord;
import cr.shared.Commit;
import cr.shared.Operation;
import cr.shared.Project;
import cr.shared.ProjectFinder;
import cr.shared.SecurityProject;
import cr.shared.SecurityProjectList;
import cr.util.RepoHandler;

@RefreshScope
@Component
public class ProjectAnalyzer {
	@Autowired
	private DeclareOk buiCounter;
	@Autowired
	private RPCClient client;
	@Autowired
	private RabbitListenerEndpointRegistry listenersreg;
	@Autowired
	private ApplicationConfigReader applicationConfigReader;
	@Autowired
	private MessageSender messageSender;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	private static String encryption = ProjectAnalyzerApplication.class
			.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
	private static Logger log = LoggerFactory.getLogger(ProjectAnalyzer.class);

	@Value("${projects.storage}")
	private String storagePath;
	@Value("${repo.user}")
	private String repoUsername;
	@Value("${repo.passwd}")
	private String repoPasswd;
	private boolean countDown = false;
	private int count;
	
	
	

	public synchronized EncryptedMessage action(EncryptedMessage message) {
		if (message.getPayloadType().equals("cr.shared.Project"))
			return analyze(message);
		if (message.getPayloadType().equals("cr.shared.Operation"))
			return processOperation(message);
		Operation nop = new Operation("NOP");
		return nop.toEncryptedMessage(encryption).encodeBase64();

	}

	private EncryptedMessage processOperation(EncryptedMessage message) {
		Operation r = message.decodeBase64ToObject();
		if (r.getMessage().equals("STOP_DEQUEUE_ANALYSIS")) {
			MessageListenerContainer buiContainer = listenersreg.getListenerContainer("ana");
			if (buiContainer != null) {
				buiContainer.stop();
				Operation op = new Operation("ANA_LISTENER_STOP");
				log.info("ANALYZER DEQUEING STATUS " + (buiContainer.isRunning()?"RUNNING":"NOT RUNNING") );
				return op.toEncryptedMessage(encryption).encodeBase64();
			}
		} else if (r.getMessage().equals("START_DEQUEUE_ANALYSIS")) {
			MessageListenerContainer buiContainer = listenersreg.getListenerContainer("ana");
			if (buiContainer != null) {
				buiContainer.start();
				Operation op = new Operation("ANA_LISTENER_STARTED");
				log.info("ANALYZER DEQUEING STATUS " + (buiContainer.isRunning()?"RUNNING":"NOT RUNNING") );
				return op.toEncryptedMessage(encryption).encodeBase64();
			}
		} else
			System.out.println(r.getMessage());

		return new Operation("NOP").toEncryptedMessage(encryption).encodeBase64();
	}

	public EncryptedMessage analyze(EncryptedMessage message) {
		Project project = message.decodeBase64ToObject();
		if (needScan(project.getLastscan())) {
			System.out.println(project);
			log.info("Starting analysis for " + project.getName() + " from url " + project.getUrl());
			// clone locally the project and detect the language, then update the project
			// record in the database with the detected technology
			String clonedProject = pushProject(project.getUrl());
			String detectedLanguage = detectLanguage(clonedProject);
			project.setLanguage(detectedLanguage);
			client.sendAndReceiveDb(project.toEncryptedMessage(encryption).encodeBase64());
			// check if the securityRepository is available. If yes push it to builder queue
			// otherwise save it to database for future use
			SecurityProject sp = new SecurityProject();
			sp.setLanguage(detectedLanguage);
			EncryptedMessage spl = client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
			SecurityProjectList splist = spl.decodeBase64ToObject();
			Optional<SecurityProject> securityProjectScannerCandidate = splist.getProject().stream().filter(s -> s.isAvailable()).findAny();
			
			if (securityProjectScannerCandidate.isPresent() && !countDown) {// push it to builder
				// mark securityRepository as unavailable
				SecurityProject s = securityProjectScannerCandidate.get();
				sp.setId(s.getId());
				sp.setAvailable(false);
				EncryptedMessage reply = client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
				System.out.println(reply.decodeBase64ToObject().toString());
				BuildRecord br = new BuildRecord();
				br.setDate(new Date());
				br.setIdRepository(project.getId());
				br.setIdSecurityProject(s.getId());
				br.setStoragefolder(clonedProject);
				br.setStatus("BUILDSTARTING");
				System.out.println("\n\n\nadding build record");
				EncryptedMessage enc=client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());
				log.info("Receiving "+enc.toString());
				br= enc.decodeBase64ToObject();
				log.info("Added "+br.toString());
				messageSender.sendMessage(rabbitTemplate, applicationConfigReader.getBuiExchange(),applicationConfigReader.getBuiRoutingKey(), br.toEncryptedMessage(encryption).encodeBase64());
			} else {// store it in database
				System.out.println("Storing "+project.getName()+" into the DB");
				BuildRecord br = new BuildRecord();
				br.setDate(new Date());
				br.setStatus("TOBUILD");
				br.setIdRepository(project.getId());
				br.setStoragefolder(clonedProject);
				br.setId(((BuildRecord) (client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject()).getId());
				if (count>0)
					count--;
				System.out.println("CountDown "+count+" "+countDown);
			}
		}
		if (!countDown) {
			SecurityProject sp = new SecurityProject();
			sp.setLanguage("%");
			boolean blockBuilderDequeuing = ((SecurityProjectList) client
					.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject())
							.getProject().stream().allMatch(s -> !s.isAvailable());
			if (blockBuilderDequeuing) {
				// block dequeuing activity of builder instance
				Operation nop = new Operation("STOP_DEQUEUE_BUILDS");
				Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
				// count messages enqueued in the builder with buiCounter.getMessageCount()
				count = buiCounter.getMessageCount();
				countDown=true;
				System.out.println("\n\n messages ready on rabbit to be built " + countDown + "\n\n");
				nop = new Operation("START_DEQUEUE_BUILDS");
				o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
				
			}
		} else if (count == 0) {
			Operation nop = new Operation("STOP_DEQUEUE_ANALYSIS");
			Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			log.info("STOP_DEQUEUE_ANALYSIS");
			nop = new Operation("STOP_DEQUEUE_BUILDS");
			o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			log.info("STOP_DEQUEUE_BUILDS");
		}
		Operation nop = new Operation("NOP");
		return nop.toEncryptedMessage(encryption).encodeBase64();
	}

	private boolean needScan(Date d) {// check if the project need to be scanned
		return true;
	}

	private String pushProject(String projectToclone) {
		String destinationFolder = (storagePath.charAt(storagePath.length() - 1) == '/' ? storagePath
				: storagePath + "/") + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "_";
		StringTokenizer st = new StringTokenizer(projectToclone, "//");
		String protocol = st.nextToken();
		String address = "";
		String project = null;
		int i = 0;
		while (st.hasMoreTokens()) {
			if (i > 0)
				address += "/";
			project = st.nextToken();
			address += project;
			i++;
		}
		project = project.replace(".git", "");
		String completeUrl = protocol + "//" + repoUsername + "@" + address;
		RepoHandler r = new RepoHandler();
		r.cloneRepo(destinationFolder + project, completeUrl, repoUsername, repoPasswd);
		return destinationFolder + project;
	}

	public String detectLanguage(String clonedRepo) {
		/* detect language start */
		log.info("Going to analyze " + clonedRepo);
		String[] cmd = { "/bin/sh", "-c", "cd " + clonedRepo + " && github-linguist | awk '{print $2}'" };
		Process p1 = null;
		String hightest = null;
		try {
			p1 = Runtime.getRuntime().exec(cmd);
			InputStream stdin = p1.getInputStream();
			InputStreamReader isr = new InputStreamReader(stdin);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (i == 0)
					hightest = line;
				log.info("Detecting " + line);
				i++;
			}
			log.info("Hightest language detected percentage is  " + hightest);
			int exitVal = p1.waitFor();
			log.info("github-linguist: " + exitVal);

		} catch (InterruptedException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		/* detect language stop */
		return hightest;
	}

	private ProjectFinder findProjectByNameAndUrl(Commit c) {
		return new ProjectFinder(c.getProjectname(), c.getUrl());
	}

}
